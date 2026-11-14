package io.timestamp.app.voice

fun permanentSpeechError(name: String) = name in setOf("PERMISSION_DENIED", "NOT_SUPPORTED_API", "UNAVAILABLE_LANGUAGE")

class SpeechErrorBackoff {
    private var failures = 0
    fun nextDelayMs(): Long = (1000L shl failures.coerceAtMost(6)).coerceAtMost(60_000L).also { failures = (failures + 1).coerceAtMost(6) }
    fun reset() { failures = 0 }
}
