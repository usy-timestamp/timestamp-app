package io.timestamp.app.capture

import org.junit.Assert.*
import org.junit.Test

class IdlePolicyTest {
    @Test fun disabledOrDeniedStopsService() {
        assertEquals(CaptureActivity.STOP, captureActivity(false, true, null, 0))
        assertEquals(CaptureActivity.STOP, captureActivity(true, false, null, 0))
        assertEquals(CaptureActivity.STOP, captureActivity(false, false, 0, PARK_IDLE_MS))
    }
    @Test fun parksExactlyAtTenMinutes() {
        assertEquals(CaptureActivity.ACTIVE, captureActivity(true, true, 100, 100 + PARK_IDLE_MS - 1))
        assertEquals(CaptureActivity.PARKED_IDLE, captureActivity(true, true, 100, 100 + PARK_IDLE_MS))
        assertEquals(CaptureActivity.PARKED_IDLE, captureActivity(true, true, 100, 100 + PARK_IDLE_MS + 1))
    }
    @Test fun leavingParkResumesAndUnknownGearDoesNotIdle() {
        assertEquals(CaptureActivity.ACTIVE, captureActivity(true, true, null, PARK_IDLE_MS * 2))
        assertEquals(CaptureActivity.ACTIVE, captureActivity(true, true, PARK_IDLE_MS, PARK_IDLE_MS + 1))
    }
}
