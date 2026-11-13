package io.timestamp.app.voice

import io.timestamp.app.data.MemoInputMode
import org.junit.Assert.*
import org.junit.Test

class DriverMemoFlowTest {
    private val flow = DriverMemoFlow()
    private val ask = MemoCapturePlan.fromCommand("타임 스탬프 찍어줘", MemoInputMode.ASK_AFTER_RECORDING)

    private fun waiting(id: String = "record", finishedAt: Long = 1_000L) {
        assertTrue(flow.beginAfterSaving(id, ask, 0L))
        assertTrue(flow.promptFinished(id, true, 0L))
        assertTrue(flow.listeningStarted(finishedAt))
    }

    @Test fun askModeOnlyAsksWhenCommandHasNoDescription() {
        assertEquals(MemoCapturePlan(null, true), ask)
        assertEquals(MemoCapturePlan("끼익 소리가 나", false),
            MemoCapturePlan.fromCommand("스탬프, 끼익 소리가 나", MemoInputMode.ASK_AFTER_RECORDING))
    }

    @Test fun oneShotNeverAsksEvenWithoutMemo() {
        assertEquals(MemoCapturePlan(null, false, true), MemoCapturePlan.fromCommand("스탬프", MemoInputMode.ONE_SHOT))
        assertEquals(MemoCapturePlan("덜컥 소리가 나", false), MemoCapturePlan.fromCommand("스템프, 덜컥 소리가 나", MemoInputMode.ONE_SHOT))
        assertFalse(flow.beginAfterSaving("record", MemoCapturePlan(null, false), 0L))
    }

    @Test fun ignoresUnrelatedSpeechWhenNotWaiting() {
        assertEquals(MemoDecision.Ignore, flow.onUtterance("끼익 소리가 나", 0))
    }

    @Test fun commandsStartNewCapturesWhenIdle() {
        assertEquals(MemoDecision.NewCapture("스탬프 찍어줘"), flow.onUtterance("스탬프 찍어줘", 0))
    }

    @Test fun promptWaitIsBoundedAndListeningStartsBeforeTheFallbackWhenReady() {
        flow.beginAfterSaving("record", ask, 0L)
        assertEquals(25_000L, flow.nextDeadlineMs)
        assertFalse(flow.expire(3_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("앱 목소리", 3_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("스탬프", 3_000L))
        assertTrue(flow.promptFinished("record", true, 3_500L))
        assertEquals(6_500L, flow.nextDeadlineMs)
        assertTrue(flow.listeningStarted(5_000L))
        assertFalse(flow.expire(12_999L))
        assertTrue(flow.expire(13_000L))
    }

    @Test fun nextUtteranceIsSavedToTheRightRecordOnce() {
        waiting("first")
        assertEquals(MemoDecision.SaveMemo("first", "오른쪽 뒤에서 드르륵 소리가 나"),
            flow.onUtterance("  오른쪽 뒤에서 드르륵 소리가 나  ", 8_999L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("또 다른 말", 8_999L))
        assertFalse(flow.expire(10_000L))
    }

    @Test fun timeoutHappensAtEightSecondsAndDoesNotAskAgain() {
        waiting()
        assertTrue(flow.expire(9_000L))
        assertFalse(flow.expire(9_001L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("늦은 답", 9_001L))
    }

    @Test fun lateReplyCannotSlipPastAScheduledTimeout() {
        waiting()
        assertEquals(MemoDecision.TimedOut, flow.onUtterance("늦은 답", 9_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("또 다른 답", 9_001L))
    }

    @Test fun commandWhileWaitingIsANewCaptureAndNotAMemo() {
        waiting()
        val command = "스탬프, 이번에는 덜컥 소리"
        assertEquals(MemoDecision.NewCapture(command), flow.onUtterance(command, 2_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("이전 기록의 답", 2_001L))
    }

    @Test fun alternateSpellingAndWhitespaceRemainNewCommands() {
        waiting()
        assertEquals(MemoDecision.NewCapture("타임 스\n템프"), flow.onUtterance("타임 스\n템프", 2_000L))
    }

    @Test fun commandAfterTimeoutStillStartsANewCapture() {
        waiting()
        assertEquals(MemoDecision.NewCapture("스탬프"), flow.onUtterance("스탬프", 9_000L))
    }

    @Test fun emptySpeechDoesNotConsumeTheReply() {
        waiting()
        assertEquals(MemoDecision.Ignore, flow.onUtterance(" \n ", 2_000L))
        assertEquals(MemoDecision.SaveMemo("record", "끼익"), flow.onUtterance("끼익", 2_001L))
    }

    @Test fun ownSpeechIsDiscardedWithoutConsumingTheReply() {
        waiting()
        assertEquals(MemoDecision.Ignore, flow.onUtterance("기록했어요. 어떤 소리였나요?", 2_000L, speaking = true))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("스탬프", 2_000L, speaking = true))
        assertEquals(MemoDecision.SaveMemo("record", "끼익"), flow.onUtterance("끼익", 2_001L))
    }

    @Test fun failedPromptDoesNotStartAReplyWindow() {
        flow.beginAfterSaving("record", ask, 0L)
        assertFalse(flow.promptFinished("record", false, 0L))
        assertFalse(flow.expire(20_000L))
        assertEquals(MemoDecision.Ignore, flow.onUtterance("답", 2_000L))
    }

    @Test fun stalePromptCompletionCannotChangeANewerReplyWindow() {
        flow.beginAfterSaving("old", ask, 0L)
        flow.beginAfterSaving("new", ask, 0L)
        assertFalse(flow.promptFinished("old", true, 0L))
        assertTrue(flow.promptFinished("new", true, 0L))
        assertTrue(flow.listeningStarted(2_000L))
        assertFalse(flow.promptFinished("new", true, 0L))
        assertFalse(flow.expire(9_999L))
        assertTrue(flow.expire(10_000L))
    }

    @Test fun stoppingCancelsThePendingQuestion() {
        waiting()
        flow.cancel()
        assertEquals(MemoDecision.Ignore, flow.onUtterance("답", 2_000L))
        assertFalse(flow.promptFinished("record", true, 0L))
        assertFalse(flow.expire(10_000L))
    }
}
