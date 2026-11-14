package io.timestamp.app.vehicle

import android.content.Context

object VehicleConnection {
    @Volatile private var shared: AaosPropertySource? = null

    fun get(context: Context): AaosPropertySource = shared ?: synchronized(this) {
        shared ?: AaosPropertySource(context.applicationContext).also { shared = it }
    }
}

// 기어만 보는 감시는 샘플링을 멈춘 뒤에도 유지할 수 있다.
class GearWatcher(context: Context, gearOnly: Boolean = false) {
    private val source = if (gearOnly) AaosPropertySource(context, gearOnly = true) else VehicleConnection.get(context)
    private var started = false
    val state = source.drivingState

    fun start() {
        if (started) return
        started = true
        source.start()
    }

    fun refreshPermissions() = source.refreshPermissions()

    fun stop() {
        if (!started) return
        started = false
        source.stop()
    }
}
