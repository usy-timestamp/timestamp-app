package io.timestamp.app.capture

import io.timestamp.app.data.CaptureEntity
import io.timestamp.app.data.CaptureRecord
import io.timestamp.app.data.CaptureRepository
import io.timestamp.app.data.InMemoryCaptureRepository
import io.timestamp.app.data.SampleEntity
import io.timestamp.app.data.TriggerKind
import io.timestamp.app.vehicle.Clock
import io.timestamp.app.vehicle.FakeSource
import io.timestamp.app.vehicle.SignalSample
import io.timestamp.app.vehicle.VehicleSignalSource
import io.timestamp.app.data.MemoInputMode
import io.timestamp.app.voice.DriverMemoFlow
import io.timestamp.app.voice.MemoCapturePlan
import io.timestamp.app.voice.MemoDecision
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureControllerTest {
    private class Fixture(val scope: TestScope) {
        val clock = Clock { scope.testScheduler.currentTime }
        val source = FakeSource(clock)
        val repository = InMemoryCaptureRepository()
        val saved = mutableListOf<CaptureRecord>()
        val controller = CaptureController(source, repository, clock, scope,
            StandardTestDispatcher(scope.testScheduler), onSaved = { saved.add(it) })

        fun start() { controller.start(); scope.runCurrent() }
        fun tick() {
            source.set(SignalSample(clock.nowMs(), speedKmh = (clock.nowMs() / 1000 + 1).toFloat(),
                brakePressed = true, gear = "D", warningLights = listOf("ABS"), outsideTempC = 25f))
            scope.advanceTimeBy(1000)
            scope.runCurrent()
        }
    }

    @Test fun memoQuestionCallbackRunsOnlyAfterTheRecordHasBeenSaved() = runTest {
        val f = Fixture(this)
        f.start()
        val completed = mutableListOf<String>()
        assertTrue(f.controller.trigger(TriggerKind.VOICE) { record ->
            assertNotNull(f.repository.find(record.capture.id))
            assertEquals(31, record.samples.size)
            assertEquals(1, f.saved.size)
            completed.add(record.capture.id)
        })
        assertFalse(f.controller.trigger(TriggerKind.VOICE) { fail("거절한 기록은 되묻지 않아야 한다") })
        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue(completed.isEmpty())
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(listOf(f.repository.all().single().capture.id), completed)
        f.controller.stop()
    }

    @Test fun oneShotContinuationBeforeCaptureSaveUpdatesOnlyThatCapture() = runTest {
        val f = Fixture(this)
        val flow = DriverMemoFlow()
        val plan = MemoCapturePlan.fromCommand("스탬프", MemoInputMode.ONE_SHOT)
        var accepted = 0
        f.start()
        assertTrue(f.controller.trigger(TriggerKind.VOICE, onAccepted = { capture ->
            accepted++
            assertTrue(flow.beginContinuation(capture.id, plan, testScheduler.currentTime))
        }) { record ->
            val memo = flow.captureSaved(record.capture.id)!!
            assertTrue(memo.continuation)
            assertTrue(f.repository.updateMemo(memo.captureId, memo.memo))
        })
        assertFalse(f.controller.trigger(TriggerKind.VOICE, onAccepted = { fail("거절한 기록은 이어질 말을 기다리지 않아야 한다") }))
        advanceTimeBy(1_000L)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("끽 소리가 나", testScheduler.currentTime))
        assertEquals(0, f.repository.count())
        advanceTimeBy(4_000L)
        runCurrent()
        val record = f.repository.all().single()
        assertEquals("끽 소리가 나", record.capture.memo)
        assertEquals(31, record.samples.size)
        assertEquals(1, accepted)
        f.controller.stop()
    }

    @Test fun storesExactly25SecondsBeforeAnd5AfterWithTriggerTime() = runTest {
        val f = Fixture(this)
        f.start()
        repeat(32) { f.tick() }
        assertTrue(f.controller.trigger(TriggerKind.VOICE, "타임 스탬프 찍어줘"))
        repeat(4) { f.tick() }
        assertEquals(0, f.repository.count())
        f.tick()
        val record = f.repository.recent().single()
        assertEquals(32_000L, record.capture.createdAtMs)
        assertEquals(TriggerKind.VOICE, record.capture.trigger)
        assertEquals("타임 스탬프 찍어줘", record.capture.memo)
        assertEquals((-25..5).toList(), record.samples.map { it.offsetSec })
        assertEquals(7f, record.samples.first().speedKmh)
        assertEquals(32f, record.samples.single { it.offsetSec == 0 }.speedKmh)
        assertEquals(37f, record.samples.last().speedKmh)
        assertTrue(record.samples.all { it.brakePressed == true && it.gear == "D" &&
            it.warningLights == "ABS" && it.outsideTempC == 25f })
        assertEquals(1, f.saved.size)
        f.controller.stop()
    }

    @Test fun rejectsDuplicatesThrough4999MsAndAcceptsAt5000Ms() = runTest {
        val f = Fixture(this)
        f.start()
        assertTrue(f.controller.trigger(TriggerKind.DEBUG, "처음"))
        advanceTimeBy(4999)
        assertFalse(f.controller.trigger(TriggerKind.VOICE, "중복"))
        advanceTimeBy(1)
        assertTrue(f.controller.trigger(TriggerKind.DEBUG, "다음"))
        runCurrent()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(2, f.repository.count())
        assertEquals(setOf("처음", "다음"), f.repository.recent().map { it.capture.memo }.toSet())
        f.controller.stop()
    }

    @Test fun keepsUnavailableValuesNullAndDoesNotInventWarmupHistory() = runTest {
        val f = Fixture(this)
        f.start()
        f.controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(5000)
        runCurrent()
        val record = f.repository.recent().single()
        assertNull(record.capture.latitude)
        assertNull(record.capture.longitude)
        assertEquals("OPEN", record.capture.status)
        assertEquals(31, record.samples.size)
        record.samples.forEach {
            assertNull(it.speedKmh)
            assertNull(it.brakePressed)
            assertNull(it.gear)
            assertNull(it.outsideTempC)
            assertEquals("", it.warningLights)
        }
        f.controller.stop()
    }

    @Test fun nonAlignedTriggerPreservesOldestBucketWhileBufferExpires() = runTest {
        val f = Fixture(this)
        f.start()
        repeat(32) { f.tick() }
        advanceTimeBy(500)
        f.controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(5000)
        runCurrent()
        val record = f.repository.recent().single()
        assertEquals(32_500L, record.capture.createdAtMs)
        assertEquals(8f, record.samples.first().speedKmh)
        assertNotNull(record.samples.last().speedKmh)
        f.controller.stop()
    }

    @Test fun stopCancelsPendingCaptureAndLeavesCallerScopeUsable() = runTest {
        val f = Fixture(this)
        assertFalse(f.controller.trigger(TriggerKind.DEBUG))
        f.start()
        f.controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(2000)
        f.controller.stop()
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(0, f.repository.count())
        assertTrue(f.saved.isEmpty())
        f.controller.start()
        assertTrue(f.controller.trigger(TriggerKind.DEBUG))
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, f.repository.count())
        f.controller.stop()
    }

    @Test fun sourceFailureStillSavesUnavailableSamples() = runTest {
        val repository = InMemoryCaptureRepository()
        val source = object : VehicleSignalSource {
            override fun start() = Unit
            override fun stop() = Unit
            override fun current(): SignalSample = throw SecurityException("권한 없음")
        }
        val controller = CaptureController(source, repository, Clock { testScheduler.currentTime }, this,
            StandardTestDispatcher(testScheduler))
        controller.start()
        controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, repository.count())
        assertTrue(repository.recent().single().samples.all { it.speedKmh == null })
        controller.stop()
    }

    @Test fun failedSaveDoesNotAnnounceSuccessAndNextCaptureCanSave() = runTest {
        val memory = InMemoryCaptureRepository()
        var fail = true
        var announcements = 0
        var errors = 0
        val repository = object : CaptureRepository by memory {
            override suspend fun save(capture: CaptureEntity, samples: List<SampleEntity>) {
                if (fail) throw IllegalStateException("저장 실패")
                memory.save(capture, samples)
            }
        }
        val clock = Clock { testScheduler.currentTime }
        val controller = CaptureController(FakeSource(clock), repository, clock, this,
            StandardTestDispatcher(testScheduler), onSaved = { announcements++ }, onError = { errors++ })
        controller.start()
        controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(0, announcements)
        assertEquals(1, errors)
        fail = false
        controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(5000)
        runCurrent()
        assertEquals(1, announcements)
        assertEquals(1, memory.count())
        controller.stop()
    }

    @Test fun delayedCompletionNeverBackdatesValuesOutsideTheWindow() = runTest {
        var delayMs = 0L
        val clock = Clock { testScheduler.currentTime + delayMs }
        val source = FakeSource(clock)
        val repository = InMemoryCaptureRepository()
        val controller = CaptureController(source, repository, clock, this,
            StandardTestDispatcher(testScheduler))
        source.set(SignalSample(0, speedKmh = 10f, brakePressed = false))
        controller.start()
        runCurrent()
        controller.trigger(TriggerKind.DEBUG)
        advanceTimeBy(4999)
        delayMs = 1000
        source.set(SignalSample(clock.nowMs(), speedKmh = 99f, brakePressed = true))
        advanceTimeBy(1)
        runCurrent()
        val samples = repository.recent().single().samples
        assertNull(samples.last().speedKmh)
        assertFalse(samples.any { it.speedKmh == 99f })
        assertEquals(false, samples.single { it.offsetSec == 0 }.brakePressed)
        controller.stop()
    }
    @Test fun gleoColdCaptureSavesSymptomAfterFiveSeconds() = runTest {
        val f = Fixture(this)
        f.controller.start()
        runCurrent()
        var completed = false
        assertTrue(f.controller.trigger(TriggerKind.GLEO, "끼익 소리") { completed = true })
        advanceTimeBy(4999)
        runCurrent()
        assertFalse(completed)
        assertEquals(0, f.repository.count())
        advanceTimeBy(1)
        runCurrent()
        assertTrue(completed)
        val record = f.repository.recent().single()
        assertEquals(TriggerKind.GLEO, record.capture.trigger)
        assertEquals("끼익 소리", record.capture.memo)
        assertTrue(record.samples.filter { it.offsetSec < 0 }.all { it.speedKmh == null })
        f.controller.stop()
    }
}
