package io.timestamp.app.voice

import org.junit.Assert.*
import org.junit.Test

class DriverMemoTimeoutTest {
    private val flow = DriverMemoFlow()
    private val ask = MemoCapturePlan(null, true)

    private fun promptFinished() {
        assertTrue(flow.beginAfterSaving("record", ask, 0L))
        assertTrue(flow.promptFinished("record", true, 4_000L))
    }

    @Test fun noReadySignalStartsEightSecondWindowAfterThreeSeconds() {
        promptFinished()
        assertEquals(7_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.NONE, flow.tick(6_999L))
        assertEquals(MemoWaitEvent.LISTENING_STARTED, flow.tick(7_000L))
        assertEquals(15_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.NONE, flow.tick(14_999L))
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(15_000L))
        assertNull(flow.nextDeadlineMs)
    }

    @Test fun readinessSignalStartsImmediatelyAndFallbackCannotResetIt() {
        promptFinished()
        assertTrue(flow.listeningStarted(5_000L))
        assertEquals(13_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.NONE, flow.tick(7_000L))
        assertFalse(flow.listeningStarted(8_000L))
        assertEquals(13_000L, flow.nextDeadlineMs)
        assertTrue(flow.expire(13_000L))
    }

    @Test fun delayedFallbackTickUsesOriginalTimeInsteadOfOpeningANewWindow() {
        promptFinished()
        assertEquals(MemoWaitEvent.LISTENING_STARTED, flow.tick(10_000L))
        assertEquals(15_000L, flow.nextDeadlineMs)
        assertTrue(flow.expire(15_000L))
    }

    @Test fun delayedReadySignalAlsoUsesOriginalFallbackTime() {
        promptFinished()
        assertTrue(flow.listeningStarted(10_000L))
        assertEquals(15_000L, flow.nextDeadlineMs)
        assertTrue(flow.expire(15_000L))
    }

    @Test fun aMissingPromptCompletionStillEndsAtTwentyFiveSeconds() {
        assertTrue(flow.beginAfterSaving("record", ask, 100L))
        assertEquals(25_100L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.NONE, flow.tick(25_099L))
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(25_100L))
        assertFalse(flow.promptFinished("record", true, 26_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("나중에 한 말", 180_000L))
    }

    @Test fun speechAndLateFinalGraceCannotExceedTheTwentyFiveSecondCap() {
        flow.beginAfterSaving("record", ask, 0L)
        flow.promptFinished("record", true, 20_000L)
        assertTrue(flow.listeningStarted(22_000L))
        assertTrue(flow.speechStarted(24_000L))
        assertEquals(25_000L, flow.nextDeadlineMs)
        assertFalse(flow.expire(24_999L))
        assertTrue(flow.expire(25_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("최종 결과", 25_001L))
    }

    @Test fun promptCompletionNearTheCapDoesNotStartAnotherFullWait() {
        flow.beginAfterSaving("record", ask, 0L)
        flow.promptFinished("record", true, 24_000L)
        assertEquals(25_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(25_000L))
        assertFalse(flow.listeningStarted(27_000L))
    }

    @Test fun timeoutRejectsRepliesAndStillAllowsOnlyNewStampCommands() {
        promptFinished()
        flow.tick(7_000L)
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(15_000L))
        assertFalse(flow.listeningStarted(180_000L))
        assertFalse(flow.speechStarted(180_001L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("한참 뒤의 소리 설명", 180_002L))
        assertEquals(MemoDecision.NewCapture("스탬프"), flow.onUtterance("스탬프", 180_003L))
        assertNull(flow.nextDeadlineMs)
    }

    @Test fun lateUtteranceCannotReviveTheWaitEvenIfTimerCallbackWasDelayed() {
        promptFinished()
        assertEquals(MemoDecision.TimedOut, flow.onUtterance("3분 뒤의 말", 180_000L))
        assertFalse(flow.listeningStarted(180_001L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("다음 말", 180_002L))
    }

    @Test fun lateReadySignalCannotReviveAnAlreadyExpiredUnpolledWait() {
        promptFinished()
        assertFalse(flow.listeningStarted(180_000L))
        assertNull(flow.nextDeadlineMs)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("뒤늦은 답", 180_001L))
    }

    @Test fun repeatedReadinessAfterSttErrorsKeepsTheOriginalRemainingTime() {
        promptFinished()
        flow.listeningStarted(5_000L)
        for (retryTime in listOf(6_000L, 7_000L, 9_000L, 12_000L)) {
            assertEquals(MemoWaitEvent.NONE, flow.tick(retryTime))
            assertFalse(flow.listeningStarted(retryTime))
            assertEquals(13_000L, flow.nextDeadlineMs)
        }
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(13_000L))
        assertFalse(flow.listeningStarted(14_000L))
    }

    @Test fun errorsBeforeReadinessCannotPostponeTheFallback() {
        promptFinished()
        assertEquals(MemoWaitEvent.NONE, flow.tick(5_000L))
        assertEquals(MemoWaitEvent.NONE, flow.tick(6_000L))
        assertEquals(7_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.LISTENING_STARTED, flow.tick(7_000L))
        assertFalse(flow.listeningStarted(10_000L))
        assertEquals(15_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(15_000L))
    }

    @Test fun fallbackStillAcceptsTimelySpeechWithABoundedLateFinal() {
        promptFinished()
        assertTrue(flow.speechStarted(14_000L))
        assertEquals(21_000L, flow.nextDeadlineMs)
        assertFalse(flow.expire(15_000L))
        assertEquals(MemoDecision.SaveMemo("record", "드르륵", late = true), flow.onUtterance("드르륵", 20_999L))
    }

    @Test fun staleTtsCompletionCannotMoveTheHardCapOfANewQuestion() {
        flow.beginAfterSaving("old", ask, 0L)
        flow.beginAfterSaving("new", ask, 5_000L)
        assertFalse(flow.promptFinished("old", true, 10_000L))
        assertEquals(30_000L, flow.nextDeadlineMs)
        assertTrue(flow.promptFinished("new", true, 29_000L))
        assertEquals(30_000L, flow.nextDeadlineMs)
        assertEquals(MemoWaitEvent.TIMED_OUT, flow.tick(30_000L))
    }

    @Test fun hardCapIsCheckedEvenWhileAppSpeechIsStillPlaying() {
        flow.beginAfterSaving("record", ask, 0L)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("앱 안내", 26_000L, speaking = true))
        assertNull(flow.nextDeadlineMs)
        assertEquals(MemoDecision.Ignore, flow.onUtterance("뒤늦은 설명", 26_001L))
    }
}