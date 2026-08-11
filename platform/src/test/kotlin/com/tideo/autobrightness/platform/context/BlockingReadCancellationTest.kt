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

/** DB-006: fetchGeoIp child-coroutine structure justified by cancellation semantics.
 * invokeOnCompletion on the blocked job can't interrupt read(); child structure fixes this.
 * Server accepts then silent (30s timeout); any quick success = socket closed (not timeout). */
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
                    coroutineContext[Job]!!.invokeOnCompletion { cause ->
                        if (cause != null) connection.disconnect()
                    }
                    runCatching { connection.inputStream.use { it.read() } }
                    unblocked.countDown()
                }
                Thread.sleep(500)
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
                job.join()
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
