package io.timestamp.app.vehicle

import org.junit.Assert.*
import org.junit.Test

class DrivingStateTest {
    @Test fun parkAllowsRecordScreens() { assertFalse(DrivingState("P").showDrivingScreen) }
    @Test fun everyNonParkGearShowsOnlyDrivingScreen() {
        listOf("D", "N", "R", "1", "UNKNOWN").forEach { assertTrue(DrivingState(it).showDrivingScreen) }
    }
    @Test fun unreadableGearAllowsRecordScreensWhenUnrestricted() { assertFalse(DrivingState().showDrivingScreen) }
    @Test fun restrictionsOverrideBothParkAndUnreadableGear() {
        assertTrue(DrivingState("P", true).showDrivingScreen)
        assertTrue(DrivingState(null, true).showDrivingScreen)
    }
}
