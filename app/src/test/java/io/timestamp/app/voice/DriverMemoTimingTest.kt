package io.timestamp.app.voice

import io.timestamp.app.data.MemoInputMode
import org.junit.Assert.*
import org.junit.Test

class DriverMemoTimingTest {
    private val flow = DriverMemoFlow()
    private val ask = MemoCapturePlan.fromCommand("스탬프", MemoInputMode.ASK_AFTER_RECORDING)
    private val oneShot = MemoCapturePlan.fromCommand("스탬프", MemoInputMode.ONE_SHOT)

    private fun ready() {
        assertTrue(flow.beginAfterSaving("record", ask, 0L))
        assertTrue(flow.promptFinished("record", true, 3_500L))
        assertTrue(flow.listeningStarted(5_000L))
    }

    @Test fun emulatorLateFinalIsKeptAfterTimelySpeechStart() {
        ready()
        assertEquals(13_000L, flow.nextDeadlineMs)
        assertTrue(flow.speechStarted(12_000L))
        assertFalse(flow.expire(13_000L))
        assertEquals(MemoDecision.SaveMemo("record", "끽끽 소리가 나", late = true), flow.onUtterance("끽끽 소리가 나", 13_001L))
    }

    @Test fun graceLastsAtMostSixMoreSeconds() {
        ready()
        assertTrue(flow.speechStarted(12_999L))
        assertFalse(flow.expire(18_999L))
        assertTrue(flow.expire(19_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("너무 늦은 답", 19_001L))
    }

    @Test fun repeatedPartialResultsDoNotExtendGraceForever() {
        ready()
        assertTrue(flow.speechStarted(6_000L))
        assertFalse(flow.speechStarted(12_999L))
        assertFalse(flow.speechStarted(18_999L))
        assertEquals(19_000L, flow.nextDeadlineMs)
        assertTrue(flow.expire(19_000L))
    }

    @Test fun speechStartingAfterTheWindowCannotReviveAReply() {
        ready()
        assertFalse(flow.speechStarted(13_000L))
        assertNull(flow.nextDeadlineMs)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("늦게 시작한 답", 13_001L))
    }

    @Test fun reopeningListeningForDebugAudioDoesNotResetTheWindow() {
        ready()
        assertFalse(flow.listeningStarted(7_000L))
        assertTrue(flow.speechStarted(7_000L))
        assertFalse(flow.listeningStarted(8_000L))
        assertEquals(19_000L, flow.nextDeadlineMs)
        assertEquals(MemoDecision.SaveMemo("record", "드르륵", late = true), flow.onUtterance("드르륵", 14_000L))
    }

    @Test fun noSpeechDoesNotReceiveAnAutomaticGracePeriod() {
        ready()
        assertTrue(flow.expire(13_000L))
    }

    @Test fun speechBeforeThePromptOrListeningIsReadyDoesNotOpenAWindow() {
        flow.beginAfterSaving("record", ask, 0L)
        assertFalse(flow.listeningStarted(0L))
        assertFalse(flow.speechStarted(1L))
        flow.promptFinished("record", true, 0L)
        assertFalse(flow.speechStarted(2L))
        assertEquals(3_000L, flow.nextDeadlineMs)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("아직 준비 전인 답", 3L))
    }

    @Test fun timelyContinuationIsBufferedUntilTheFiveSecondCaptureIsSaved() {
        assertTrue(flow.beginContinuation("record", oneShot, 1_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("끽 소리가 나", 4_999L))
        assertNull(flow.nextDeadlineMs)
        assertEquals(MemoDecision.SaveMemo("record", "끽 소리가 나", continuation = true), flow.captureSaved("record"))
        assertNull(flow.captureSaved("record"))
    }

    @Test fun continuationAfterFourSecondsWithoutSpeechStartIsNotAMemo() {
        flow.beginContinuation("record", oneShot, 1_000L)
        assertEquals(MemoDecision.TimedOut, flow.onUtterance("늦은 말", 5_000L))
        assertNull(flow.captureSaved("record"))
    }

    @Test fun continuationWithLateFinalCanArriveAfterDatabaseSave() {
        flow.beginContinuation("record", oneShot, 1_000L)
        assertTrue(flow.speechStarted(4_999L))
        assertFalse(flow.expire(5_000L))
        assertNull(flow.captureSaved("record"))
        assertEquals(MemoDecision.SaveMemo("record", "끽 소리가 나", continuation = true), flow.onUtterance("끽 소리가 나", 7_000L))
    }

    @Test fun stampInContinuationIsANewCommandAndNotAnAnswer() {
        flow.beginContinuation("record", oneShot, 1_000L)
        assertEquals(MemoDecision.NewCapture("스탬프, 덜컥"), flow.onUtterance("스탬프, 덜컥", 2_000L))
        assertNull(flow.captureSaved("record"))
    }

    @Test fun oneShotWithAMemoDoesNotWaitForContinuation() {
        val plan = MemoCapturePlan.fromCommand("스탬프, 끼익", MemoInputMode.ONE_SHOT)
        assertFalse(flow.beginContinuation("record", plan, 1_000L))
        assertFalse(flow.beginAfterSaving("record", plan, 0L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("다음 말", 2_000L))
    }

    @Test fun acceptsOnlyOneContinuationAndKeepsItAcrossANewCommand() {
        flow.beginContinuation("first", oneShot, 1_000L)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("첫 설명", 2_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("두 번째 설명", 2_001L))
        assertEquals(MemoDecision.NewCapture("스탬프"), flow.onUtterance("스탬프", 2_002L))
        flow.beginContinuation("second", oneShot, 2_002L)
        flow.onUtterance("새 설명", 2_003L)
        assertEquals(MemoDecision.SaveMemo("first", "첫 설명", continuation = true), flow.captureSaved("first"))
        assertEquals(MemoDecision.SaveMemo("second", "새 설명", continuation = true), flow.captureSaved("second"))
    }

    @Test fun cancellingClearsUncommittedContinuations() {
        flow.beginContinuation("record", oneShot, 1_000L)
        flow.onUtterance("답", 2_000L)
        flow.cancel()
        assertNull(flow.captureSaved("record"))
        assertNull(flow.nextDeadlineMs)
    }

    @Test fun aNewCommandDuringLateResultGraceIsStillACommand() {
        ready()
        flow.speechStarted(12_000L)
        assertEquals(MemoDecision.NewCapture("스템프"), flow.onUtterance("스템프", 14_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("앞 기록의 답", 14_001L))
    }
}
