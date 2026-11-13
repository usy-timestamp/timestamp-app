package io.timestamp.app.vehicle

import android.content.Context

object VehicleConnection {
    @Volatile private var shared: AaosPropertySource? = null

    fun get(context: Context): AaosPropertySource = shared ?: synchronized(this) {
        shared ?: AaosPropertySource(context.applicationContext).also { shared = it }
    }
}

// 화면과 기록 서비스가 같은 연결을 빌리므로 창을 닫아도 기록은 계속된다.
class GearWatcher(context: Context) {
    private val source = VehicleConnection.get(context)
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
