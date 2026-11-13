package io.timestamp.app.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale

class VoiceTrigger(private val context: Context, private val onUtterance: (String) -> Unit,
                   private val onListeningStarted: (Long) -> Unit = {},
                   private val onSpeechStarted: (Long) -> Unit = {}) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var engine: SpeechToText? = null
    private var ready = false
    private var wanted = false
    @Volatile private var paused = false
    private val pendingFiles = ArrayDeque<ByteArray>()
    private var feedingThread: Thread? = null
    private var fileSession = false
    private var recoveryScheduled = false
    private val recognition = RecognitionGate()
    private val requestAgain = Runnable { request() }
    private val reconnectAfterError = Runnable {
        recoveryScheduled = false
        if (wanted && !paused) {
            releaseEngine(clearFiles = false)
            connect()
        }
    }
    private val resultStalled = Runnable {
        // 결과가 영영 오지 않을 때만 복구하고 정상적인 늦은 결과는 기다린다.
        if (recognition.awaitingResult) {
            Log.w(TAG, "STT 최종 결과 대기 시간 초과")
            releaseEngine(clearFiles = false)
            connect()
        }
    }

    fun start() { wanted = true; connect() }

    private fun connect() {
        if (!wanted || paused || engine != null) return
        try {
            val s = SpeechToText(context, Mode.ON_DEVICE)
            engine = s
            var lastEpdOn: Long? = null
            var readySignaled = false
            s.addListener(object : ResultListener {
                override fun onUpdated(text: String, isFinal: Boolean) {
                    val now = SystemClock.elapsedRealtime()
                    main.post {
                        if (engine !== s || !ready || paused) return@post
                        if (!isFinal) {
                            if (text.isNotBlank()) speechStarted(now)
                            return@post
                        }
                        recognition.resultFinished()
                        main.removeCallbacks(resultStalled)
                        main.removeCallbacks(reconnectAfterError)
                        recoveryScheduled = false
                        try {
                            Log.i(TAG, "STT 최종 결과: $text")
                            onUtterance(text)
                        } finally { resumeAfterResult(s) }
                    }
                }
                override fun onError(error: ErrorType) {
                    main.post {
                        if (engine === s && !paused) {
                            Log.w(TAG, "STT 오류 $error")
                            recoverFromError()
                        }
                    }
                }
                override fun onUpdatedEpdData(on: Long, off: Long) {
                    if (on <= 0L) return
                    val now = SystemClock.elapsedRealtime()
                    main.post {
                        if (engine === s && ready && !paused && lastEpdOn != on) {
                            lastEpdOn = on
                            speechStarted(now)
                        }
                    }
                }
                override fun onStartedRecognition() {
                    val now = SystemClock.elapsedRealtime()
                    main.post { if (engine === s && ready && !paused) onListeningStarted(now) }
                }
                override fun onEndedRecognition() = Unit
                override fun onReady() {
                    val now = SystemClock.elapsedRealtime()
                    main.post {
                        if (engine === s && !paused) {
                            readySignaled = true
                            if (ready) onListeningStarted(now)
                        }
                    }
                }
                override fun onUpdatedRms(rms: Double) = Unit
            })
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (engine !== s || paused) return@post
                        try {
                            s.setLocale(Locale.KOREA)
                            ready = true
                            if (pendingFiles.isNotEmpty()) sendFile(s, pendingFiles.removeFirst()) else {
                                request()
                                // 초기화 성공보다 먼저 온 준비 알림도 듣기를 요청한 뒤 반영한다.
                                if (readySignaled) onListeningStarted(SystemClock.elapsedRealtime())
                            }
                        } catch (e: Exception) { Log.w(TAG, "STT 설정 실패", e) }
                    }
                }
                override fun onFailure(error: ErrorType) {
                    main.post {
                        if (engine === s && !paused) {
                            Log.w(TAG, "STT 초기화 실패 $error")
                            recoverFromError()
                        }
                    }
                }
            })
        } catch (e: Exception) { Log.w(TAG, "STT 준비 실패", e) }
    }

    private fun recoverFromError() {
        main.removeCallbacks(requestAgain)
        // 파일의 늦은 최종 결과는 잠시 기다리되 오류가 반복돼도 복구 시각은 미루지 않는다.
        if (wanted && !paused && !recoveryScheduled) {
            recoveryScheduled = true
            main.postDelayed(reconnectAfterError, if (fileSession) 6_000L else 1_000L)
        }
    }

    private fun speechStarted(now: Long) {
        val alreadyWaiting = recognition.awaitingResult
        recognition.speechStarted()
        if (!alreadyWaiting) waitForResult()
        onSpeechStarted(now)
    }

    private fun waitForResult() {
        main.removeCallbacks(requestAgain)
        main.removeCallbacks(resultStalled)
        main.postDelayed(resultStalled, 20_000L)
    }

    // 안내 전 인식기를 닫아 늦게 도착한 자기 목소리 결과도 답으로 받지 않는다.
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (value) releaseEngine(clearFiles = false) else connect()
    }

    private fun request() {
        if (!wanted || paused || !ready || engine == null || !recognition.canRestart) return
        fileSession = false
        main.removeCallbacks(reconnectAfterError)
        recoveryScheduled = false
        try { engine?.request() } catch (e: Exception) {
            Log.w(TAG, "STT 듣기 실패", e)
            restartAfterPause()
        }
    }

    private fun restartAfterPause() {
        main.removeCallbacks(requestAgain)
        if (wanted && !paused && ready && engine != null && recognition.canRestart) main.postDelayed(requestAgain, 1000)
    }

    private fun resumeAfterResult(s: SpeechToText) {
        if (engine !== s || paused || !recognition.canRestart) return
        if (pendingFiles.isNotEmpty()) {
            releaseEngine(clearFiles = false)
            connect()
        } else restartAfterPause()
    }

    // 안내 중이나 초기화 중에 넣은 시험 답도 준비가 끝난 뒤 같은 발화 경로로 보낸다.
    fun feedForTest(pcm: ByteArray) {
        if (!wanted) return
        if (pendingFiles.size >= 4) { Log.w(TAG, "시험 음성 대기열이 가득 참"); return }
        pendingFiles.addLast(pcm.copyOf())
        if (!paused) onSpeechStarted(SystemClock.elapsedRealtime())
        if (paused || !recognition.canRestart) return
        // 앞서 마이크를 멈추며 생긴 결과와 시험 파일의 결과를 섞지 않도록 새 인식기를 쓴다.
        releaseEngine(clearFiles = false)
        connect()
    }

    private fun sendFile(s: SpeechToText, pcm: ByteArray) {
        fileSession = true
        recognition.fileStarted()
        waitForResult()
        val now = SystemClock.elapsedRealtime()
        onListeningStarted(now)
        onSpeechStarted(now)
        feedingThread = Thread {
            try {
                var offset = 0
                while (offset < pcm.size) {
                    if (engine !== s || paused) return@Thread
                    val end = minOf(offset + 3200, pcm.size)
                    s.sendAudio(pcm.copyOfRange(offset, end))
                    offset = end
                    Thread.sleep(100)
                }
                main.post {
                    if (engine === s && !paused) {
                        // 전송이 끝나도 최종 결과 전에는 듣기를 다시 시작하지 않는다.
                        recognition.fileSent()
                        waitForResult()
                        try { s.completeAudioSend() } catch (e: Exception) { Log.w(TAG, "시험 음성 마감 실패", e) }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) { Log.w(TAG, "시험 음성 전송 실패", e) }
        }.also { it.start() }
    }

    private fun releaseEngine(clearFiles: Boolean = true) {
        val s = engine
        engine = null
        ready = false
        if (clearFiles) pendingFiles.clear()
        main.removeCallbacksAndMessages(null)
        feedingThread?.interrupt()
        feedingThread = null
        recognition.reset()
        fileSession = false
        recoveryScheduled = false
        try { s?.stop() } catch (_: Exception) { }
        try { s?.release() } catch (_: Exception) { }
    }

    fun stop() { wanted = false; releaseEngine() }

    companion object { private const val TAG = "TimeStampCapture" }
}
