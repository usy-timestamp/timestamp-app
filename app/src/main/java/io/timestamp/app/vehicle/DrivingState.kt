package io.timestamp.app.vehicle

data class DrivingState(val gear: String? = null, val restricted: Boolean = false) {
    val showDrivingScreen: Boolean get() = restricted || (gear != null && gear != "P")
}
