package io.timestamp.app.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

class Speaker(private val context: Context, private val onSpeakingChanged: (Boolean) -> Unit = {}) {
    private data class Utterance(val text: String, val finished: (Boolean) -> Unit)
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var ready = false
    private val pending = ArrayDeque<Utterance>()
    private var active: Utterance? = null
    var isSpeaking: Boolean = false
        private set
    private val stalled = Runnable {
        Log.w(TAG, "안내 음성 완료를 확인하지 못해 되묻기를 취소함")
        failAll()
    }

    fun start() {
        if (engine != null) return
        try {
            val t = TextToSpeech(context, Mode.ON_DEVICE)
            engine = t
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (engine !== t) return@post
                        try {
                            // 초기화가 끝나야 SDK가 설정을 받아들이므로 이 안에서만 설정한다.
                            t.addEventListener(object : EventListener {
                                override fun onStart() = Unit
                                override fun onReady() = Unit
                                override fun onDone() { main.post { if (engine === t) finish(true) } }
                                override fun onStop() { main.post { if (engine === t) finish(false) } }
                                override fun onUpdatedRms(rms: Double) = Unit
                                override fun onError(error: ErrorType) {
                                    main.post {
                                        if (engine === t) {
                                            Log.w(TAG, "TTS 오류 $error")
                                            failAll()
                                        }
                                    }
                                }
                            })
                            t.setLocale(Locale.KOREA)
                            ready = true
                            main.removeCallbacks(stalled)
                            pump()
                        } catch (e: Exception) { Log.w(TAG, "TTS 설정 실패", e); failAll() }
                    }
                }
                override fun onFailure(error: ErrorType) {
                    main.post {
                        if (engine === t) { Log.w(TAG, "TTS 초기화 실패 $error"); failAll() }
                    }
                }
            })
        } catch (e: Exception) { Log.w(TAG, "TTS 준비 실패", e); failAll() }
    }

    fun speak(text: String, finished: (Boolean) -> Unit = {}) {
        if (pending.size == 10) pending.removeFirst().finished(false)
        pending.addLast(Utterance(text, finished))
        setSpeaking(true)
        if (!ready) {
            main.removeCallbacks(stalled)
            main.postDelayed(stalled, 30_000L)
        }
        if (engine == null) start()
        pump()
    }

    private fun pump() {
        if (active != null) return
        if (pending.isEmpty()) { setSpeaking(false); return }
        if (!ready) return
        active = pending.removeFirst()
        main.removeCallbacks(stalled)
        main.postDelayed(stalled, 30_000L)
        try {
            // 문장이 끝났다고 알려야 다음 안내도 정상적으로 재생된다.
            engine?.speak(active!!.text, true)
        } catch (e: Exception) { Log.w(TAG, "TTS 재생 실패", e); failAll() }
    }

    private fun finish(played: Boolean) {
        val utterance = active ?: return
        main.removeCallbacks(stalled)
        active = null
        utterance.finished(played)
        pump()
    }

    private fun setSpeaking(value: Boolean) {
        if (isSpeaking == value) return
        isSpeaking = value
        onSpeakingChanged(value)
    }

    private fun failAll() {
        main.removeCallbacks(stalled)
        val t = engine
        engine = null
        ready = false
        val unfinished = listOfNotNull(active) + pending.toList()
        active = null
        pending.clear()
        try { t?.release() } catch (_: Exception) { }
        unfinished.forEach { it.finished(false) }
        setSpeaking(false)
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        failAll()
    }

    companion object { private const val TAG = "TimeStampCapture" }
}