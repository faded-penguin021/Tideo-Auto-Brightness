package com.tideo.autobrightness.app.control

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure, framework-free contract for DA-039's admission bound — the pair `onReceive` itself uses. */
class ControlReceiverAdmissionTest {
    @Test
    fun exportedCommands_allowOnlyOneInFlight_DA039() {
        ControlReceiver.releaseCommand()
        try {
            assertTrue(ControlReceiver.tryAcquireCommand(), "the first command must be admitted")
            assertFalse(ControlReceiver.tryAcquireCommand(), "an overlapping command must be dropped, not queued")
            ControlReceiver.releaseCommand()
            assertTrue(ControlReceiver.tryAcquireCommand(), "completion must admit the next command")
        } finally {
            ControlReceiver.releaseCommand()
        }
    }
}
