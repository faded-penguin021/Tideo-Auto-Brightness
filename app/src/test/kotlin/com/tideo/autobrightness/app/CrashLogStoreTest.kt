package com.tideo.autobrightness.app

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * C1 acceptance (D-158): crash-log ring and delegation contract (pure JVM, java.io + java.time).
 */
class CrashLogStoreTest {

    private lateinit var tmp: File

    @Before fun setUp() { tmp = Files.createTempDirectory("crashlog").toFile() }
    @After fun tearDown() { tmp.deleteRecursively() }

    private fun store(clock: () -> Long) = CrashLogStore(File(tmp, "crash"), clock)

    @Test fun recordWritesTraceAndLatestReadsItBack() {
        val store = store { 1_000L }
        assertFalse(store.hasAny())
        assertNull(store.latest())

        store.record(IllegalStateException("boom"), Thread.currentThread())

        assertTrue(store.hasAny())
        val log = store.latest()
        assertNotNull(log)
        assertTrue("trace should name the exception", log!!.contains("IllegalStateException"))
        assertTrue("trace should carry the message", log.contains("boom"))
        assertTrue("trace should carry a dated header", log.contains("Tideo Auto Brightness crash"))
    }

    @Test fun keepsOnlyTheFiveNewestAndLatestIsTheNewest() {
        var t = 0L
        val store = store { t }
        repeat(8) { i -> t = 1_000L + i; store.record(RuntimeException("crash #$i")) }

        val files = File(tmp, "crash").listFiles()!!.filter { it.isFile }
        assertEquals("prune to the newest ${CrashLogStore.KEEP}", CrashLogStore.KEEP, files.size)
        assertTrue("latest() is the newest crash", store.latest()!!.contains("crash #7"))
        assertFalse("the oldest crash was pruned", files.any { it.readText().contains("crash #0") })
    }

    @Test fun recordNeverThrowsWhenTheDirectoryCannotBeCreated() {
        // mkdirs()/writeText fail; record() must swallow, never mask the crash on dying thread.
        val blocker = File(tmp, "notadir").apply { writeText("x") }
        val store = CrashLogStore(blocker) { 1L }
        store.record(RuntimeException("boom"))
        assertFalse(store.hasAny())
        assertNull(store.latest())
    }

    @Test fun handlerRecordsThenDelegatesToPrevious() {
        val store = store { 5L }
        var delegated: Throwable? = null
        val handler = CrashLogHandler(store, { _, e -> delegated = e })

        val crash = RuntimeException("kaboom")
        handler.uncaughtException(Thread.currentThread(), crash)

        assertTrue("trace written before delegating", store.hasAny())
        assertSame("must delegate so the process still dies", crash, delegated)
    }

    @Test fun handlerDelegatesEvenWhenTheWriteFails() {
        // Write fails → record() swallows; delegation must still happen (write THEN delegate).
        val blocker = File(tmp, "blocker").apply { writeText("x") }
        val store = CrashLogStore(blocker) { 1L }
        var delegated = false
        val handler = CrashLogHandler(store, { _, _ -> delegated = true })

        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))

        assertFalse("no trace could be written", store.hasAny())
        assertTrue("delegation must still happen", delegated)
    }

    @Test fun handlerToleratesANullPreviousHandler() {
        val store = store { 9L }
        // No prior handler; must not NPE.
        CrashLogHandler(store, null).uncaughtException(Thread.currentThread(), RuntimeException("x"))
        assertTrue(store.hasAny())
    }

    @Test fun installWrapsThePreviousDefaultHandler() {
        val saved = Thread.getDefaultUncaughtExceptionHandler()
        try {
            var delegated = false
            Thread.setDefaultUncaughtExceptionHandler { _, _ -> delegated = true }
            installCrashLogHandler(store { 3L })

            val installed = Thread.getDefaultUncaughtExceptionHandler()
            assertTrue("install replaces the default with our wrapper", installed is CrashLogHandler)
            installed!!.uncaughtException(Thread.currentThread(), RuntimeException("x"))
            assertTrue("the wrapper chains to the prior default", delegated)
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(saved)
        }
    }

    @Test fun installIsIdempotent() {
        val saved = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(null)
            installCrashLogHandler(store { 1L })
            val first = Thread.getDefaultUncaughtExceptionHandler()

            installCrashLogHandler(store { 2L })

            assertSame(
                "a second install must not re-wrap our own handler",
                first, Thread.getDefaultUncaughtExceptionHandler(),
            )
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(saved)
        }
    }
}
