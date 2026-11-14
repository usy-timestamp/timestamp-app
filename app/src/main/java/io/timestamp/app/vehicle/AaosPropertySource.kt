package io.timestamp.app.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AaosPropertySource(
    private val context: Context,
    private val clock: Clock = WallClock,
    private val gearOnly: Boolean = false,
) : VehicleSignalSource {
    private val lock = Any()
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private val callbacks = mutableListOf<CarPropertyManager.CarPropertyEventCallback>()
    private var active = false
    private var users = 0
    private var uxManager: CarUxRestrictionsManager? = null
    private val driving = MutableStateFlow(DrivingState())
    val drivingState = driving.asStateFlow()
    private val uxListener = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
        synchronized(lock) {
            if (active) driving.value = driving.value.copy(restricted = restrictions.isRequiresDistractionOptimization)
        }
    }
    private var sample = SignalSample(clock.nowMs())

    override fun start() {
        synchronized(lock) {
            users++
            if (active) return
            active = true
            sample = SignalSample(clock.nowMs())
            try {
                car = Car.createCar(context)
                manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                try {
                    // 기어만 보는 감시도 주행 제한 상태는 함께 받는다. 기어를 못 읽는 차에서도 주행 화면 판단이 되게 하기 위함
                    uxManager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                    driving.value = driving.value.copy(
                        restricted = uxManager?.currentCarUxRestrictions?.isRequiresDistractionOptimization == true,
                    )
                    uxManager?.registerListener(uxListener)
                } catch (_: Exception) { }
                val properties = manager ?: return
                watch(properties, VehiclePropertyIds.GEAR_SELECTION)
                if (!gearOnly) watch(properties, VehiclePropertyIds.PERF_VEHICLE_SPEED)
                if (!gearOnly) watch(properties, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE)
            } catch (_: Exception) {
                sample = SignalSample(clock.nowMs())
            }
        }
    }

    fun refreshPermissions() {
        synchronized(lock) {
            if (!active) return
            val properties = manager ?: return
            callbacks.forEach { try { properties.unregisterCallback(it) } catch (_: Exception) { } }
            callbacks.clear()
            watch(properties, VehiclePropertyIds.GEAR_SELECTION)
            if (!gearOnly) watch(properties, VehiclePropertyIds.PERF_VEHICLE_SPEED)
            if (!gearOnly) watch(properties, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE)
        }
    }

    private fun watch(properties: CarPropertyManager, id: Int) {
        try {
            if (properties.getCarPropertyConfig(id) == null) {
                update(id, null)
                return
            }
            try {
                update(id, properties.getProperty<Any>(id, 0))
            } catch (_: Exception) {
                update(id, null)
            }
            val callback = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    synchronized(lock) { if (active && this in callbacks) update(id, value) }
                }

                override fun onErrorEvent(propId: Int, zone: Int) {
                    synchronized(lock) { if (active && this in callbacks) update(id, null) }
                }
            }
            callbacks.add(callback)
            if (!properties.registerCallback(callback, id, CarPropertyManager.SENSOR_RATE_NORMAL)) {
                update(id, null)
            }
        } catch (_: Exception) {
            update(id, null)
        }
    }

    private fun update(id: Int, value: CarPropertyValue<*>?) {
        val raw = value?.takeIf { it.status == CarPropertyValue.STATUS_AVAILABLE }?.value
        sample = when (id) {
            VehiclePropertyIds.GEAR_SELECTION -> sample.copy(gear = when (raw) {
                VehicleGear.GEAR_PARK -> "P"
                VehicleGear.GEAR_REVERSE -> "R"
                VehicleGear.GEAR_NEUTRAL -> "N"
                VehicleGear.GEAR_DRIVE -> "D"
                else -> (raw as? Int)?.takeIf { it != VehicleGear.GEAR_UNKNOWN }?.let { "기타" }
            })
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> sample.copy(
                speedKmh = (raw as? Float)?.takeIf { it.isFinite() }?.times(3.6f),
            )
            VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE -> sample.copy(
                outsideTempC = (raw as? Float)?.takeIf { it.isFinite() },
            )
            else -> sample
        }
        driving.value = driving.value.copy(gear = sample.gear)
    }

    override fun current(): SignalSample = synchronized(lock) { sample.copy(timeMs = clock.nowMs()) }

    override fun stop() {
        synchronized(lock) {
            if (users == 0) return
            users--
            if (users > 0) return
            active = false
            callbacks.forEach { callback ->
                try { manager?.unregisterCallback(callback) } catch (_: Exception) { }
            }
            callbacks.clear()
            try { uxManager?.unregisterListener() } catch (_: Exception) { }
            uxManager = null
            try { car?.disconnect() } catch (_: Exception) { }
            manager = null
            car = null
            sample = SignalSample(clock.nowMs())
            driving.value = DrivingState()
        }
    }
}
