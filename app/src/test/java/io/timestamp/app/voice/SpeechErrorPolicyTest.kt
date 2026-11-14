package io.timestamp.app.voice

import org.junit.Assert.*
import org.junit.Test

class SpeechErrorPolicyTest {
    @Test fun permanentErrorsDoNotRetry() {
        listOf("PERMISSION_DENIED", "NOT_SUPPORTED_API", "UNAVAILABLE_LANGUAGE").forEach { assertTrue(permanentSpeechError(it)) }
        listOf("MIC_ERROR", "UNKNOWN", "TIMEOUT").forEach { assertFalse(permanentSpeechError(it)) }
    }
    @Test fun exponentialDelayCapsAtSixtySeconds() {
        val backoff = SpeechErrorBackoff()
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 16000L, 32000L, 60000L, 60000L), List(8) { backoff.nextDelayMs() })
        repeat(100) { assertEquals(60000L, backoff.nextDelayMs()) }
    }
    @Test fun successResetsDelay() {
        val backoff = SpeechErrorBackoff()
        repeat(9) { backoff.nextDelayMs() }
        backoff.reset()
        assertEquals(1000L, backoff.nextDelayMs())
    }
}
