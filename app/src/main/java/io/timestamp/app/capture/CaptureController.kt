package io.timestamp.app.capture

import io.timestamp.app.buffer.RingBuffer
import io.timestamp.app.data.CaptureEntity
import io.timestamp.app.data.CaptureRecord
import io.timestamp.app.data.CaptureRepository
import io.timestamp.app.data.SampleEntity
import io.timestamp.app.data.TriggerKind
import io.timestamp.app.vehicle.Clock
import io.timestamp.app.vehicle.SignalSample
import io.timestamp.app.vehicle.VehicleSignalSource
import io.timestamp.app.vehicle.WallClock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class CaptureController(
    private val source: VehicleSignalSource,
    private val repository: CaptureRepository,
    private val clock: Clock = WallClock,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val buffer: RingBuffer = RingBuffer(clock = clock),
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onSaved: suspend (CaptureRecord) -> Unit = {},
    private val onError: (Exception) -> Unit = {},
) {
    private val lock = Any()
    private var runningScope: CoroutineScope? = null
    private var lastTriggerMs: Long? = null

    fun start() {
        synchronized(lock) {
            if (runningScope != null) return
            source.start()
            val worker = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]) + dispatcher)
            runningScope = worker
            worker.launch {
                while (isActive) {
                    buffer.add(readSample())
                    delay(1000)
                }
            }
        }
    }

    fun trigger(kind: String, memo: String? = null,
                onAccepted: ((CaptureEntity) -> Unit)? = null,
                onFailure: ((Exception) -> Unit)? = null,
                afterSaved: (suspend (CaptureRecord) -> Unit)? = null): Boolean = synchronized(lock) {
        require(kind in TriggerKind.values)
        val worker = runningScope ?: return false
        val now = clock.nowMs()
        val previous = lastTriggerMs
        if (previous != null && now - previous < 5000L) return false
        lastTriggerMs = now
        val capture = CaptureEntity(newId(), now, kind, memo)
        buffer.add(readSample())
        // 기다리는 동안 링버퍼의 오래된 값이 지워져도 앞 구간은 보존한다.
        val before = buffer.snapshot(now - 25_000, now)
        onAccepted?.invoke(capture)
        worker.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                delay(5000)
                buffer.add(readSample())
                // 실행이 늦어져도 마감 뒤의 값을 과거 시각으로 바꾸어 넣지 않는다.
                val after = buffer.snapshot(now, now + 5000)
                val bySecond = (before + after).sortedBy { it.timeMs }
                    .associateBy { Math.floorDiv(it.timeMs - now, 1000L).toInt() }
                // 아직 모으지 못한 초는 임의의 값으로 채우지 않고 수집 불가로 남긴다.
                val samples = (-25..5).map { offset ->
                    val value = bySecond[offset]
                    SampleEntity(capture.id, offset, value?.speedKmh, value?.brakePressed,
                        value?.gear, value?.warningLights?.joinToString(",").orEmpty(), value?.outsideTempC)
                }
                repository.save(capture, samples)
                val record = CaptureRecord(capture, samples)
                onSaved(record)
                afterSaved?.invoke(record)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(e)
                onFailure?.invoke(e)
            }
        }
        true
    }

    private fun readSample(): SignalSample = try {
        source.current().let { it.copy(warningLights = it.warningLights.toList()) }
    } catch (e: Exception) {
        onError(e)
        SignalSample(clock.nowMs())
    }

    fun stop() {
        synchronized(lock) {
            runningScope?.cancel()
            runningScope = null
            lastTriggerMs = null
            source.stop()
        }
    }
}
