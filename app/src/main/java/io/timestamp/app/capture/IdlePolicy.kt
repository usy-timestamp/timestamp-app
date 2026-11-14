package io.timestamp.app.capture

const val PARK_IDLE_MS = 10 * 60 * 1000L
enum class CaptureActivity { STOP, PARKED_IDLE, ACTIVE }

fun captureActivity(enabled: Boolean, microphoneGranted: Boolean, parkedSince: Long?, now: Long): CaptureActivity = when {
    !enabled || !microphoneGranted -> CaptureActivity.STOP
    parkedSince != null && now - parkedSince >= PARK_IDLE_MS -> CaptureActivity.PARKED_IDLE
    else -> CaptureActivity.ACTIVE
}
