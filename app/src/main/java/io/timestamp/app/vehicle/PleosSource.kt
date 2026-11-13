package io.timestamp.app.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import android.content.Context
import android.os.Handler
import android.os.Looper

class PleosSource(
    private val context: Context,
    private val clock: Clock = WallClock,
) : VehicleSignalSource {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var vehicle: Vehicle? = null
    @Volatile private var brakePressed: Boolean? = null
    @Volatile private var warningLights: List<String> = emptyList()

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            if (vehicle != null) brakePressed = pressed
        }
        override fun onFailed(e: Exception) { brakePressed = null }
    }

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            if (vehicle != null) updateTelltales(telltales)
        }
        override fun onFailed(e: Exception) { warningLights = emptyList() }
    }

    override fun start() {
        if (vehicle != null) return
        try {
            val v = Vehicle(context)
            vehicle = v
            v.initialize()
            // 차량 서비스가 연결되기 전에 구독하면 값을 받지 못하므로 기다린다.
            main.postDelayed({ if (vehicle === v) subscribe(v) }, 1500)
        } catch (_: Exception) {
            stop()
        }
    }

    private fun subscribe(v: Vehicle) {
        try {
            val brake = v.getBrake()
            brake.checkBrakePedalStatusCapabilities({}, {})
            brake.getCurrentBrakePedalStatus(
                { if (vehicle === v) brakePressed = it },
                { if (vehicle === v) brakePressed = null },
            )
            brake.registerBrakePedalStatus(brakeListener)
        } catch (_: Exception) { brakePressed = null }
        try {
            val safety = v.getSafety()
            safety.checkTelltalesCapabilities({}, {})
            safety.getTelltalesInfo(
                { if (vehicle === v) updateTelltales(it) },
                { if (vehicle === v) warningLights = emptyList() },
            )
            safety.registerTelltales(telltaleListener)
        } catch (_: Exception) { warningLights = emptyList() }
    }

    private fun updateTelltales(telltales: Telltales?) {
        warningLights = telltales?.telltales.orEmpty()
            .filter { it.state != TelltaleState.OFF }
            .map { it.type.toString() }.distinct().sorted()
    }

    override fun current() = SignalSample(
        timeMs = clock.nowMs(), brakePressed = brakePressed, warningLights = warningLights.toList(),
    )

    override fun stop() {
        main.removeCallbacksAndMessages(null)
        val v = vehicle
        vehicle = null
        try { v?.getBrake()?.unregisterBrakePedalStatus(brakeListener) } catch (_: Exception) { }
        try { v?.getSafety()?.unregisterTelltales(telltaleListener) } catch (_: Exception) { }
        try { v?.release() } catch (_: Exception) { }
        brakePressed = null
        warningLights = emptyList()
    }
}
