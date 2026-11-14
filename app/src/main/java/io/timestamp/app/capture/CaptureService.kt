package io.timestamp.app.capture

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import io.timestamp.app.BuildConfig
import io.timestamp.app.data.AppDatabase
import io.timestamp.app.data.AppSettingsEntity
import io.timestamp.app.data.CaptureRecord
import io.timestamp.app.data.CaptureRepository
import io.timestamp.app.data.RoomCaptureRepository
import io.timestamp.app.data.TriggerKind
import io.timestamp.app.data.MemoInputMode
import io.timestamp.app.MainActivity
import io.timestamp.app.vehicle.VehicleConnection
import io.timestamp.app.vehicle.CompositeSource
import io.timestamp.app.vehicle.PleosSource
import io.timestamp.app.voice.Speaker
import io.timestamp.app.voice.VoiceTrigger
import io.timestamp.app.voice.DriverMemoFlow
import io.timestamp.app.voice.MemoCapturePlan
import io.timestamp.app.voice.MemoDecision
import io.timestamp.app.voice.MemoWaitEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: CaptureRepository
    private lateinit var controller: CaptureController
    private lateinit var speaker: Speaker
    private lateinit var voice: VoiceTrigger
    private val gearWatcher by lazy { io.timestamp.app.vehicle.GearWatcher(this, gearOnly = true) }
    private var parkedSince: Long? = null
    private var idleTimer: Job? = null
    private var idle = false
    private var gleoRequests = 0
    private var speechDisabled = false
    private var receiverRegistered = false
    private var microphoneForeground = false
    private var settingsLoaded = false
    private var settings = AppSettingsEntity()
    private val memoFlow = DriverMemoFlow()
    private var memoTimeout: Job? = null
    private var destroyed = false
    private var voiceRequestVersion = 0L

    override fun onCreate() {
        super.onCreate()
        showForeground(false)
        repository = RoomCaptureRepository(AppDatabase.get(this).captureDao())
        voice = VoiceTrigger(this, ::onVoiceUtterance, ::onListeningStarted, ::onSpeechStarted) {
            speechDisabled = true
            applyVoiceSetting()
        }
        speaker = Speaker(this) { speaking ->
            if (!destroyed) voice.setPaused(speaking)
        }.also { it.start() }
        controller = CaptureController(
            source = CompositeSource(VehicleConnection.get(this), PleosSource(this)),
            repository = repository, scope = serviceScope,
            onSaved = { record ->
                logRecord(record)
                withContext(Dispatchers.Main.immediate) {
                    if (record.capture.trigger !in setOf(TriggerKind.VOICE, TriggerKind.GLEO) && settings.announceCapture) speaker.speak("기록했어요")
                }
            },
            onError = { Log.e(TAG, "기록 처리 실패", if (BuildConfig.DEBUG) it else null) },
        )
        serviceScope.launch {
            gearWatcher.state.collect { state ->
                idleTimer?.cancel()
                if (state.gear == "P") {
                    if (parkedSince == null) parkedSince = SystemClock.elapsedRealtime()
                    idleTimer = serviceScope.launch {
                        delay((PARK_IDLE_MS - (SystemClock.elapsedRealtime() - parkedSince!!)).coerceAtLeast(0))
                        applyVoiceSetting()
                    }
                } else parkedSince = null
                applyVoiceSetting()
            }
        }
        if (BuildConfig.DEBUG) {
            ContextCompat.registerReceiver(this, debugReceiver, IntentFilter().apply {
                addAction(ACTION_CAPTURE)
                addAction(ACTION_DUMP)
                addAction(ACTION_VOICE_FILE)
                addAction(ACTION_SEED)
            }, ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        serviceScope.launch {
            try {
                val saved = repository.settings()
                repository.saveSettings(saved.copy(
                    microphoneGranted = granted(Manifest.permission.RECORD_AUDIO),
                    carSpeedGranted = granted("android.car.permission.CAR_SPEED"),
                    carPowertrainGranted = granted("android.car.permission.CAR_POWERTRAIN"),
                ))
                repository.observeSettings().collect {
                    settings = it
                    settingsLoaded = true
                    applyVoiceSetting()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { Log.e(TAG, "설정 읽기 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null) }
        }
    }

    private fun applyVoiceSetting() {
        if (!settingsLoaded) return
        if (gleoRequests > 0) return
        if (!settings.onboardingCompleted || settings.consentAtMs == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val activity = captureActivity(settings.voiceTriggerEnabled && !speechDisabled,
            granted(Manifest.permission.RECORD_AUDIO), parkedSince, SystemClock.elapsedRealtime())
        if (activity == CaptureActivity.PARKED_IDLE) {
            if (!idle) {
                idle = true
                voice.stop()
                speaker.stop()
                memoFlow.cancel()
                memoTimeout?.cancel()
                controller.stop()
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            microphoneForeground = false
            return
        }
        if (activity == CaptureActivity.ACTIVE) {
            idle = false
            gearWatcher.start()
            controller.start()
            try {
                showForeground(true)
                voice.start()
            } catch (e: Exception) {
                Log.w(TAG, "음성 듣기 시작 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null)
                voice.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            memoFlow.cancel()
            memoTimeout?.cancel()
            voice.stop()
            speaker.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun onListeningStarted(now: Long) {
        if (!speaker.isSpeaking) {
            advanceMemoWait(now)
            if (memoFlow.listeningStarted(now)) Log.i(TAG, "답 듣기 시작")
            scheduleMemoTimeout()
        }
    }

    private fun onSpeechStarted(now: Long) {
        advanceMemoWait(now)
        if (!speaker.isSpeaking && memoFlow.speechStarted(now)) {
            if (memoFlow.waitingForAnswer) Log.i(TAG, "답 말 시작 감지")
            scheduleMemoTimeout()
        }
    }

    private fun advanceMemoWait(now: Long) {
        val answer = memoFlow.waitingForAnswer
        when (memoFlow.tick(now)) {
            MemoWaitEvent.LISTENING_STARTED -> Log.i(TAG, "답 듣기 시작")
            MemoWaitEvent.TIMED_OUT -> Log.i(TAG, if (answer) "되묻기 시간 초과" else "이어진 말 대기 시간 초과")
            MemoWaitEvent.NONE -> Unit
        }
    }

    private fun scheduleMemoTimeout() {
        memoTimeout?.cancel()
        val deadline = memoFlow.nextDeadlineMs ?: return
        memoTimeout = serviceScope.launch {
            delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            advanceMemoWait(SystemClock.elapsedRealtime())
            scheduleMemoTimeout()
            // 기다림만 끝내고 인식기를 다시 열지 않아 도착 중인 최종 결과를 보존한다.
        }
    }

    private fun onVoiceUtterance(text: String) {
        advanceMemoWait(SystemClock.elapsedRealtime())
        when (val decision = memoFlow.onUtterance(text, SystemClock.elapsedRealtime(), speaker.isSpeaking)) {
            MemoDecision.Ignore -> Unit
            MemoDecision.TimedOut -> Log.i(TAG, "메모 입력 시간 초과")
            is MemoDecision.NewCapture -> {
                memoTimeout?.cancel()
                serviceScope.launch {
                    try {
                        // 설정을 매번 읽어 방금 고른 입력 방식으로 이번 기록을 처리한다.
                        val current = repository.settings()
                        val plan = MemoCapturePlan.fromCommand(decision.command, MemoInputMode.valueOf(current.memoInputMode))
                        val version = voiceRequestVersion + 1
                        controller.trigger(TriggerKind.VOICE, plan.memo, onAccepted = { capture ->
                            voiceRequestVersion = version
                            // 신호 저장까지 5초가 걸리므로 이어질 말은 기록 시작 때부터 기다린다.
                            if (memoFlow.beginContinuation(capture.id, plan, SystemClock.elapsedRealtime())) scheduleMemoTimeout()
                        }) { record ->
                            withContext(Dispatchers.Main.immediate) {
                                memoFlow.captureSaved(record.capture.id)?.let { saveVoiceMemo(it) }
                                if (version == voiceRequestVersion) onVoiceRecordSaved(record, plan)
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) { Log.e(TAG, "메모 입력 설정 읽기 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null) }
                }
            }
            is MemoDecision.SaveMemo -> {
                memoTimeout?.cancel()
                serviceScope.launch { saveVoiceMemo(decision) }
            }
        }
    }

    private suspend fun saveVoiceMemo(decision: MemoDecision.SaveMemo) {
        try {
            // 기다리는 동안 기록을 지웠다면 되살리지 않고 그대로 끝낸다.
            if (repository.updateMemo(decision.captureId, decision.memo)) {
                if (decision.late) Log.i(TAG, "늦게 온 답 받음")
                if (BuildConfig.DEBUG) Log.i(TAG, if (decision.continuation) "이어진 말을 메모로 저장: ${decision.memo}" else "메모 저장: ${decision.memo}")
                if (!decision.continuation) speaker.speak("메모했어요")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { Log.e(TAG, "메모 저장 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null) }
    }

    private fun onVoiceRecordSaved(record: CaptureRecord, plan: MemoCapturePlan) {
        if (destroyed || plan.continueAfterCommand) return
        val canAsk = settings.voiceTriggerEnabled && granted(Manifest.permission.RECORD_AUDIO)
        if (canAsk && memoFlow.beginAfterSaving(record.capture.id, plan, SystemClock.elapsedRealtime())) {
            scheduleMemoTimeout()
            Log.i(TAG, "되묻기 시작")
            speaker.speak("기록했어요. 어떤 소리였나요?") { played ->
                if (!destroyed) {
                    advanceMemoWait(SystemClock.elapsedRealtime())
                    memoFlow.promptFinished(record.capture.id, played, SystemClock.elapsedRealtime())
                    scheduleMemoTimeout()
                }
            }
        } else if (settings.announceCapture) speaker.speak("기록했어요")
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun showForeground(microphone: Boolean) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "차량 기록", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_save).setContentTitle("타임 스탬프 기록 중")
            .setContentText("차량 신호를 준비하고 있어요").setContentIntent(open)
            .setOngoing(true).addAction(Notification.Action.Builder(null, "기록 멈추기", stop).build()).build()
        if (Build.VERSION.SDK_INT >= 34) {
            var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (microphone) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            startForeground(1, notification, types)
        } else if (Build.VERSION.SDK_INT >= 30) {
            startForeground(1, notification,
                if (microphone) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        } else {
            startForeground(1, notification)
        }
        microphoneForeground = microphone
    }

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!BuildConfig.DEBUG) return
            when (intent.action) {
                ACTION_SEED -> {
                    val pending = goAsync()
                    serviceScope.launch {
                        try {
                            // 고정 이름을 써서 여러 번 요청해도 예시가 계속 늘어나지 않게 한다.
                            DebugSeed.insert(repository)
                            Log.i(TAG, "화면 확인용 기록 추가")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "예시 기록 저장 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null)
                        } finally { pending.finish() }
                    }
                }
                ACTION_CAPTURE -> {
                    val accepted = controller.trigger(TriggerKind.DEBUG, intent.getStringExtra("memo"))
                    Log.i(TAG, "디버그 기록 요청 accepted=$accepted")
                }
                ACTION_VOICE_FILE -> {
                    // 디버그 빌드에만 들어 있는 시험 음성으로 음성 기록 흐름을 확인한다
                    try {
                        val name = intent.getStringExtra("name") ?: "utt1.wav"
                        val bytes = assets.open("stt-test/$name").use { it.readBytes() }
                        voice.feedForTest(bytes.copyOfRange(44, bytes.size))
                        Log.i(TAG, "시험 음성 전송 $name")
                    } catch (e: Exception) { Log.w(TAG, "시험 음성 열기 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null) }
                }
                ACTION_DUMP -> {
                    val pending = goAsync()
                    serviceScope.launch {
                        try {
                            Log.i(TAG, "저장된 기록 수=${repository.count()}")
                            repository.recent(3).forEach { logRecord(it) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "기록 조회 실패", if (io.timestamp.app.BuildConfig.DEBUG) e else null)
                        } finally { pending.finish() }
                    }
                }
            }
        }
    }

    private fun logRecord(record: CaptureRecord) {
        if (!BuildConfig.DEBUG) return
        val before = record.samples.filter { it.offsetSec in -5..-1 }
        val speeds = before.mapNotNull { it.speedKmh }
        val speedRange = if (speeds.isEmpty()) "수집 불가" else "${speeds.minOrNull()}..${speeds.maxOrNull()} km/h"
        val brakeSeconds = before.count { it.brakePressed == true }
        val brakeKnown = before.count { it.brakePressed != null }
        Log.i(TAG, "captureId=${record.capture.id} 시각=${record.capture.createdAtMs} " +
            "trigger=${record.capture.trigger} samples=${record.samples.size} " +
            "직전5초 속도=$speedRange 브레이크=${brakeSeconds}초 수집=${brakeKnown}초")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!microphoneForeground) showForeground(false)
        if (intent?.action == io.timestamp.app.gleo.RECORD_VEHICLE_MOMENT) {
            @Suppress("DEPRECATION")
            val callback = intent.getParcelableExtra<android.os.ResultReceiver>("callback")
            val symptom = intent.getStringExtra("symptom")
            gleoRequests++
            serviceScope.launch {
                var answered = false
                fun reply(status: String, message: String, mode: String = "voice", id: String? = null) {
                    if (answered) return
                    answered = true
                    callback?.send(0, android.os.Bundle().apply {
                        putString("response", io.timestamp.app.gleo.gleoResponse(status, message, mode, id))
                    })
                    gleoRequests--
                    applyVoiceSetting()
                }
                try {
                    val current = repository.settings()
                    if (!current.onboardingCompleted || current.consentAtMs == null) {
                        reply("fail", "Please open the app and complete the privacy consent first.")
                        return@launch
                    }
                    if (parkedSince != null) {
                        parkedSince = SystemClock.elapsedRealtime()
                        idleTimer?.cancel()
                        idleTimer = serviceScope.launch { delay(PARK_IDLE_MS); applyVoiceSetting() }
                    }
                    idle = false
                    gearWatcher.start()
                    controller.start()
                    val plan = MemoCapturePlan(symptom, symptom == null && current.memoInputMode == MemoInputMode.ASK_AFTER_RECORDING.name)
                    val timeout = serviceScope.launch {
                        delay(6100)
                        reply("fail", "Recording could not be confirmed in time.")
                    }
                    val accepted = controller.trigger(TriggerKind.GLEO, symptom, onFailure = {
                        serviceScope.launch { timeout.cancel(); reply("fail", "The vehicle state could not be saved.") }
                    }) { record ->
                        withContext(Dispatchers.Main.immediate) {
                            timeout.cancel()
                            if (answered) return@withContext
                            val ask = plan.askAfterSaving && settings.voiceTriggerEnabled && !speechDisabled && granted(Manifest.permission.RECORD_AUDIO)
                            reply("success", "The vehicle state was recorded.", if (ask) "silent" else "voice", record.capture.id)
                            if (ask) onVoiceRecordSaved(record, plan)
                        }
                    }
                    if (!accepted) { timeout.cancel(); reply("fail", "A recording is already in progress. Please try again shortly.") }
                } catch (_: Exception) { reply("fail", "The vehicle state could not be recorded.") }
            }
        } else if (intent?.action == ACTION_STOP) stopSelf() else applyVoiceSetting()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        idleTimer?.cancel()
        gearWatcher.stop()
        memoFlow.cancel()
        memoTimeout?.cancel()
        if (receiverRegistered) unregisterReceiver(debugReceiver)
        controller.stop()
        voice.stop()
        speaker.stop()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val TAG = "TimeStampCapture"
        const val ACTION_CAPTURE = "io.timestamp.app.DEBUG_CAPTURE"
        const val ACTION_DUMP = "io.timestamp.app.DEBUG_DUMP"
        const val ACTION_VOICE_FILE = "io.timestamp.app.DEBUG_VOICE_FILE"
        const val ACTION_SEED = "io.timestamp.app.DEBUG_SEED"
        private const val ACTION_STOP = "io.timestamp.app.STOP_CAPTURE"
        private const val CHANNEL = "timestamp_capture"
    }
}
