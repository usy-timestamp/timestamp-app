package io.timestamp.app.voice

import io.timestamp.app.data.MemoInputMode

data class MemoCapturePlan(val memo: String?, val askAfterSaving: Boolean, val continueAfterCommand: Boolean = false) {
    companion object {
        fun fromCommand(text: String, mode: MemoInputMode): MemoCapturePlan {
            val memo = stripTimestampCommand(text)
            return MemoCapturePlan(memo, mode == MemoInputMode.ASK_AFTER_RECORDING && memo == null,
                mode == MemoInputMode.ONE_SHOT && memo == null)
        }
    }
}

sealed interface MemoDecision {
    data object Ignore : MemoDecision
    data object TimedOut : MemoDecision
    data class NewCapture(val command: String) : MemoDecision
    data class SaveMemo(val captureId: String, val memo: String,
                        val continuation: Boolean = false, val late: Boolean = false) : MemoDecision
}

enum class MemoWaitEvent { NONE, LISTENING_STARTED, TIMED_OUT }

// 듣기 준비 시간과 답을 글자로 바꾸는 시간을 구분해 제때 말한 답을 버리지 않는다.
class DriverMemoFlow(private val replyTimeoutMs: Long = 8_000L,
                     private val finalGraceMs: Long = 6_000L,
                     private val continuationMs: Long = 4_000L,
                     private val readinessTimeoutMs: Long = 3_000L,
                     private val hardTimeoutMs: Long = 25_000L) {
    private enum class Phase { PROMPTING, WAITING_FOR_LISTENING, LISTENING }
    private data class Pending(val id: String, val continuation: Boolean, var phase: Phase,
                               var deadline: Long? = null, var speechStarted: Boolean = false,
                               val hardDeadline: Long? = null, var readinessDeadline: Long? = null)
    private var pending: Pending? = null
    private val unsaved = mutableSetOf<String>()
    private val queued = mutableMapOf<String, MemoDecision.SaveMemo>()

    val nextDeadlineMs: Long? get() = pending?.let { value ->
        val readiness = if (value.phase == Phase.WAITING_FOR_LISTENING) value.readinessDeadline else null
        listOfNotNull(timeoutAt(value), readiness).minOrNull()
    }
    val waitingForAnswer get() = pending?.continuation == false

    fun beginAfterSaving(id: String, plan: MemoCapturePlan, nowMs: Long): Boolean {
        if (!plan.askAfterSaving) return false
        pending = Pending(id, false, Phase.PROMPTING, hardDeadline = nowMs + hardTimeoutMs)
        return true
    }

    fun beginContinuation(id: String, plan: MemoCapturePlan, nowMs: Long): Boolean {
        if (!plan.continueAfterCommand) return false
        unsaved.add(id)
        pending = Pending(id, true, Phase.LISTENING, nowMs + continuationMs)
        return true
    }

    fun captureSaved(id: String): MemoDecision.SaveMemo? {
        unsaved.remove(id)
        return queued.remove(id)
    }

    fun promptFinished(id: String, played: Boolean, nowMs: Long): Boolean {
        if (expire(nowMs)) return false
        val value = pending ?: return false
        if (value.id != id || value.phase != Phase.PROMPTING) return false
        if (!played) { pending = null; return false }
        value.phase = Phase.WAITING_FOR_LISTENING
        value.readinessDeadline = nowMs + readinessTimeoutMs
        return true
    }

    fun listeningStarted(nowMs: Long): Boolean {
        if (expire(nowMs)) return false
        val value = pending ?: return false
        if (value.phase != Phase.WAITING_FOR_LISTENING) return false
        value.phase = Phase.LISTENING
        // 준비 신호가 늦게 와도 대체 시작 시각을 뒤로 미루지 않는다.
        value.deadline = minOf(nowMs, value.readinessDeadline!!) + replyTimeoutMs
        return true
    }

    fun speechStarted(nowMs: Long): Boolean {
        tick(nowMs)
        val value = pending ?: return false
        val deadline = value.deadline ?: return false
        if (value.phase != Phase.LISTENING || value.speechStarted || nowMs >= deadline) return false
        value.speechStarted = true
        return true
    }

    fun expire(nowMs: Long): Boolean {
        val deadline = pending?.let(::timeoutAt) ?: return false
        if (nowMs < deadline) return false
        pending = null
        return true
    }

    private fun timeoutAt(value: Pending): Long? {
        val replyDeadline = value.deadline ?: value.readinessDeadline?.plus(replyTimeoutMs)
        val finalDeadline = replyDeadline?.plus(if (value.speechStarted) finalGraceMs else 0L)
        return listOfNotNull(value.hardDeadline, finalDeadline).minOrNull()
    }

    fun tick(nowMs: Long): MemoWaitEvent {
        if (expire(nowMs)) return MemoWaitEvent.TIMED_OUT
        val value = pending ?: return MemoWaitEvent.NONE
        val readiness = value.readinessDeadline ?: return MemoWaitEvent.NONE
        if (value.phase != Phase.WAITING_FOR_LISTENING || nowMs < readiness) return MemoWaitEvent.NONE
        // 콜백과 타이머 실행이 모두 늦어져도 처음 정한 시각부터 센다.
        value.phase = Phase.LISTENING
        value.deadline = readiness + replyTimeoutMs
        return MemoWaitEvent.LISTENING_STARTED
    }

    fun onUtterance(text: String, nowMs: Long, speaking: Boolean = false): MemoDecision {
        val timedOut = tick(nowMs) == MemoWaitEvent.TIMED_OUT
        if (speaking || pending?.phase == Phase.PROMPTING) return MemoDecision.Ignore
        if (isTimestampCommand(text)) {
            pending = null
            return MemoDecision.NewCapture(text)
        }
        if (timedOut) return MemoDecision.TimedOut
        val value = pending ?: return MemoDecision.Ignore
        if (value.phase != Phase.LISTENING) return MemoDecision.Ignore
        val memo = text.trim().ifBlank { return MemoDecision.Ignore }
        val result = MemoDecision.SaveMemo(value.id, memo, value.continuation,
            late = !value.continuation && nowMs >= value.deadline!!)
        pending = null
        if (value.id in unsaved) {
            queued[value.id] = result
            return MemoDecision.Ignore
        }
        return result
    }

    fun cancel() {
        pending = null
        unsaved.clear()
        queued.clear()
    }
}
