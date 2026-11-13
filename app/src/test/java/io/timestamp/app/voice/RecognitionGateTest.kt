package io.timestamp.app.voice

import org.junit.Assert.*
import org.junit.Test

class RecognitionGateTest {
    private val gate = RecognitionGate()

    @Test fun fileTransferCompletionDoesNotAllowRestartBeforeFinalResult() {
        gate.fileStarted()
        assertFalse(gate.canRestart)
        gate.fileSent()
        assertTrue(gate.awaitingResult)
        assertFalse(gate.canRestart)
        gate.resultFinished()
        assertTrue(gate.canRestart)
    }

    @Test fun finalDuringAFileDoesNotRestartOrLoseTheSecondUtterance() {
        gate.fileStarted()
        gate.resultFinished()
        assertFalse(gate.canRestart)
        gate.fileSent()
        assertFalse(gate.canRestart)
        gate.resultFinished()
        assertTrue(gate.canRestart)
    }

    @Test fun microphoneSpeechAlsoWaitsForItsFinalResult() {
        gate.speechStarted()
        assertFalse(gate.canRestart)
        gate.resultFinished()
        assertTrue(gate.canRestart)
    }

    @Test fun flowTimeoutDoesNotResetTheRecognitionSession() {
        val flow = DriverMemoFlow()
        flow.beginAfterSaving("record", MemoCapturePlan(null, true), 0L)
        flow.promptFinished("record", true, 0L)
        flow.listeningStarted(0L)
        gate.fileStarted()
        gate.fileSent()
        assertTrue(flow.expire(8_000L))
        assertFalse(gate.canRestart)
        gate.resultFinished()
        assertTrue(gate.canRestart)
    }

    @Test fun timelyFileStartAndLateFinalAreAcceptedTogether() {
        val flow = DriverMemoFlow()
        flow.beginAfterSaving("record", MemoCapturePlan(null, true), 0L)
        flow.promptFinished("record", true, 0L)
        flow.listeningStarted(0L)
        gate.fileStarted()
        flow.speechStarted(7_000L)
        gate.fileSent()
        assertFalse(flow.expire(8_000L))
        assertFalse(gate.canRestart)
        gate.resultFinished()
        assertEquals(MemoDecision.SaveMemo("record", "드르륵", late = true), flow.onUtterance("드르륵", 8_001L))
        assertTrue(gate.canRestart)
    }

    @Test fun lifecycleResetClearsBothTransferAndRecognition() {
        gate.fileStarted()
        gate.reset()
        assertTrue(gate.canRestart)
        assertFalse(gate.awaitingResult)
        assertFalse(gate.sendingFile)
    }
}