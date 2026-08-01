package com.tideo.autobrightness.platform.context

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DB-006 — why [GeoIpLocationClient.fetchGeoIp] runs its blocking request in a child coroutine.
 *
 * The security review disputed the claim that cancelling the geo-IP request disconnects it:
 * *"The completion handler may not interrupt the blocking call before the socket timeout."* This
 * test was written to check that claim and **confirmed it** — the original shape could not
 * interrupt the read at all, for a reason worth pinning down:
 *
 *   `Job.invokeOnCompletion` fires when a job COMPLETES. A job parked in an uninterruptible
 *   `read()` does not complete when you cancel it; it completes when the read returns. Registering
 *   the disconnect on the job that then does the blocking call therefore schedules the rescue behind
 *   the very wait it was meant to cut short.
 *
 * Both cases below run against a server that accepts and then says nothing, with a 30 s socket
 * timeout — so any assertion that passes inside a few seconds can only be explained by the socket
 * having been closed, not by the timeout expiring.
 */
class BlockingReadCancellationTest {

    /** A server that completes the TCP handshake and then never responds. */
    private fun silentServer(): ServerSocket {
        val server = ServerSocket(0)
        Thread {
            runCatching {
                val socket = server.accept()
                Thread.sleep(60_000)
                socket.close()
            }
        }.apply { isDaemon = true }.start()
        return server
    }

    private fun connectionTo(server: ServerSocket): HttpURLConnection =
        (URL("http://127.0.0.1:${server.localPort}/").openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 30_000
            requestMethod = "GET"
        }

    @Test
    fun completionHandlerOnTheBlockedJob_cannotInterruptTheRead() {
        val server = silentServer()
        val unblocked = CountDownLatch(1)
        try {
            runBlocking {
                val connection = connectionTo(server)
                val scope = CoroutineScope(Dispatchers.IO + Job())
                val blocked = scope.launch {
                    // The ORIGINAL shape: register on our own job, then block on it.
                    coroutineContext[Job]!!.invokeOnCompletion { cause ->
                        if (cause != null) connection.disconnect()
                    }
                    runCatching { connection.inputStream.use { it.read() } }
                    unblocked.countDown()
                }
                Thread.sleep(500) // let the read actually block
                blocked.cancel()
                unblocked.await(5, TimeUnit.SECONDS)
                scope.coroutineContext[Job]!!.cancel()
            }
        } finally {
            runCatching { server.close() }
        }

        assertFalse(
            unblocked.count == 0L,
            "the read was interrupted, so the premise of the DB-006 fix no longer holds — " +
                "re-check whether fetchGeoIp still needs its child-coroutine structure",
        )
    }

    @Test
    fun disconnectWhileTheChildIsBlocked_releasesItPromptly() {
        val server = silentServer()
        val elapsedMs: Long
        try {
            val started = System.nanoTime()
            runBlocking {
                val connection = connectionTo(server)
                val outer = CoroutineScope(Dispatchers.IO + Job())
                val job = outer.launch {
                    // The SHIPPED shape: the blocking call is a child, the parent waits at a real
                    // suspension point, and the socket is closed as that wait unwinds.
                    coroutineScope {
                        val request = async(Dispatchers.IO) {
                            connection.inputStream.use { it.read() }
                        }
                        try {
                            request.await()
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
                Thread.sleep(500)
                job.cancel()
                job.join() // returns only once the blocked child has actually been released
            }
            elapsedMs = (System.nanoTime() - started) / 1_000_000
        } finally {
            runCatching { server.close() }
        }

        assertTrue(
            elapsedMs < 15_000,
            "cancellation took ${elapsedMs}ms — that is the socket timeout expiring, not the " +
                "disconnect releasing the read",
        )
    }
}
