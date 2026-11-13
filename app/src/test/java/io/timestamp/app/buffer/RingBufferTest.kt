package io.timestamp.app.buffer

import io.timestamp.app.vehicle.Clock
import io.timestamp.app.vehicle.SignalSample
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RingBufferTest {
    private var now = 0L
    private val clock = Clock { now }

    @Test fun retainsOnlyConfiguredTimeWindowIncludingEndpoints() {
        val buffer = RingBuffer(retentionSeconds = 3, clock = clock)
        for (second in 0..10) {
            now = second * 1000L
            buffer.add(SignalSample(now))
        }
        assertEquals(listOf(7000L, 8000L, 9000L, 10000L), buffer.snapshot(0, now).map { it.timeMs })
    }

    @Test fun filtersInclusiveRangeAndSortsLateSamples() {
        now = 10_000
        val buffer = RingBuffer(clock = clock)
        listOf(5000L, 2000L, 4000L, 3000L).forEach { buffer.add(SignalSample(it)) }
        assertEquals(listOf(3000L, 4000L), buffer.snapshot(3000, 4000).map { it.timeMs })
        assertTrue(buffer.snapshot(6000, 7000).isEmpty())
    }

    @Test fun snapshotDiscardsExpiredValuesWithoutNewWrites() {
        val buffer = RingBuffer(clock = clock)
        buffer.add(SignalSample(0))
        now = 30_001
        assertTrue(buffer.snapshot(0, now).isEmpty())
    }

    @Test fun rejectsLateExpiredSamplesAndCopiesMutableLists() {
        now = 40_000
        val buffer = RingBuffer(clock = clock)
        val lights = mutableListOf("ABS")
        buffer.add(SignalSample(39_000, warningLights = lights))
        lights.clear()
        buffer.add(SignalSample(0))
        assertEquals(listOf("ABS"), buffer.snapshot(0, now).single().warningLights)
    }

    @Test fun concurrentWritersAndReadersKeepEveryInRangeSample() {
        val buffer = RingBuffer(clock = clock)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val jobs = (0..3).map { worker ->
                pool.submit {
                    repeat(100) { index ->
                        buffer.add(SignalSample((worker * 100 + index).toLong()))
                        buffer.snapshot(0, 1000)
                    }
                }
            }
            jobs.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(400, buffer.snapshot(0, 1000).size)
        } finally { pool.shutdownNow() }
    }
}
