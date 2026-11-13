package io.timestamp.app.vehicle

fun interface Clock {
    fun nowMs(): Long
}

object WallClock : Clock {
    override fun nowMs(): Long = System.currentTimeMillis()
}

data class SignalSample(
    val timeMs: Long,
    val speedKmh: Float? = null,
    val brakePressed: Boolean? = null,
    val gear: String? = null,
    val warningLights: List<String> = emptyList(),
    val outsideTempC: Float? = null,
)

interface VehicleSignalSource {
    fun start()
    fun stop()
    fun current(): SignalSample
}

class FakeSource(private val clock: Clock = WallClock) : VehicleSignalSource {
    @Volatile private var value = SignalSample(clock.nowMs())

    fun set(sample: SignalSample) {
        value = sample.copy(warningLights = sample.warningLights.toList())
    }

    override fun start() = Unit
    override fun stop() = Unit
    override fun current(): SignalSample = value.let {
        it.copy(timeMs = clock.nowMs(), warningLights = it.warningLights.toList())
    }
}
