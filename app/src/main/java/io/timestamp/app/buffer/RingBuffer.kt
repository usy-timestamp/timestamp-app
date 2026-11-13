package io.timestamp.app.buffer

import io.timestamp.app.vehicle.Clock
import io.timestamp.app.vehicle.SignalSample
import io.timestamp.app.vehicle.WallClock
import java.util.ArrayDeque

class RingBuffer(
    private val retentionSeconds: Int = 30,
    private val clock: Clock = WallClock,
) {
    init { require(retentionSeconds > 0) }
    private val samples = ArrayDeque<SignalSample>()

    @Synchronized
    fun add(sample: SignalSample) {
        samples.addLast(sample.copy(warningLights = sample.warningLights.toList()))
        discardOld()
    }

    @Synchronized
    fun snapshot(fromMs: Long, toMs: Long): List<SignalSample> {
        require(fromMs <= toMs)
        discardOld()
        return samples.filter { it.timeMs in fromMs..toMs }
            .sortedBy { it.timeMs }
            .map { it.copy(warningLights = it.warningLights.toList()) }
    }

    private fun discardOld() {
        val oldest = clock.nowMs() - retentionSeconds * 1000L
        // 늦게 도착한 값도 있을 수 있어서 맨 앞 값만 검사하지 않는다.
        samples.removeAll { it.timeMs < oldest }
    }
}
