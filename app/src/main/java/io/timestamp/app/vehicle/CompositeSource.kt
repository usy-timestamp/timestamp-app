package io.timestamp.app.vehicle

class CompositeSource(
    private val aaos: VehicleSignalSource,
    private val pleos: VehicleSignalSource,
    private val clock: Clock = WallClock,
) : VehicleSignalSource {
    override fun start() { aaos.start(); pleos.start() }
    override fun stop() { pleos.stop(); aaos.stop() }

    override fun current(): SignalSample {
        val standard = aaos.current()
        val extra = pleos.current()
        return standard.copy(
            timeMs = clock.nowMs(), brakePressed = extra.brakePressed,
            warningLights = extra.warningLights.toList(),
        )
    }
}
