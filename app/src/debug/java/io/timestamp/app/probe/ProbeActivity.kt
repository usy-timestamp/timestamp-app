package io.timestamp.app.probe

import android.Manifest
import android.app.Activity
import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import java.util.Locale
import io.timestamp.app.capture.CaptureService

/**
 * M0 신호 확인 화면.
 * 차량 신호와 음성 기능을 하나씩 불러 보고 결과를 화면과 로그(TimeStampProbe)에 남긴다.
 * 제품 화면이 아니라 개발 확인용이다.
 */
class ProbeActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var output: TextView
    private val lines = ArrayList<String>()

    private var car: Car? = null
    private var properties: CarPropertyManager? = null
    private var vehicle: Vehicle? = null
    private var location: FusedLocation? = null
    private var tts: TextToSpeech? = null
    private var stt: SpeechToText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        say("창 크기 ${resources.displayMetrics.widthPixels} x ${resources.displayMetrics.heightPixels}, 밀도 ${resources.displayMetrics.densityDpi}")

        val wanted = arrayOf(
            "android.car.permission.CAR_SPEED",
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            "pleos.car.permission.FUSED_LOCATION",
        )
        val missing = wanted.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startProbes() else requestPermissions(missing.toTypedArray(), 1)

        val filter = IntentFilter().apply {
            addAction(ACTION_TTS)
            addAction(ACTION_STT)
            addAction(ACTION_STT_STOP)
            addAction(ACTION_STT_FILE)
            addAction(ACTION_DUMP)
        }
        registerReceiver(debugReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        permissions.forEachIndexed { i, p ->
            say("권한 $p ${if (results[i] == PackageManager.PERMISSION_GRANTED) "허용" else "거부"}")
        }
        if (code == 2) {
            startCaptureService()
            return
        }
        startProbes()
    }

    private fun startProbes() {
        probeAaos()
        probePleosVehicle()
        probeLocation()
        probeTts()
        probeStt()
    }

    // ---------- AAOS 표준 API: 기어, 차속, 외기온 ----------

    private fun probeAaos() {
        try {
            car = Car.createCar(this)
            val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            properties = manager
            if (manager == null) {
                say("AAOS CarPropertyManager 없음")
                return
            }
            watchProperty(manager, "기어 GEAR_SELECTION", VehiclePropertyIds.GEAR_SELECTION)
            watchProperty(manager, "차속 PERF_VEHICLE_SPEED", VehiclePropertyIds.PERF_VEHICLE_SPEED)
            watchProperty(manager, "외기온 ENV_OUTSIDE_TEMPERATURE", VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE)

            val ux = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            if (ux != null) {
                say("운전 중 제한 현재값 ${ux.currentCarUxRestrictions}")
                ux.registerListener { say("운전 중 제한 바뀜 $it") }
            }
        } catch (e: Throwable) {
            say("AAOS 연결 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun watchProperty(manager: CarPropertyManager, name: String, id: Int) {
        try {
            val config = manager.getCarPropertyConfig(id)
            if (config == null) {
                say("$name 지원 안 함 (config 없음), 수집 불가")
                return
            }
            say("$name 지원함 access=${config.access} changeMode=${config.changeMode}")
            try {
                val value = manager.getProperty<Any>(id, 0)
                say("$name 현재값 ${describe(id, value)}")
            } catch (e: Throwable) {
                say("$name 읽기 실패 ${e.javaClass.simpleName} ${e.message}")
            }
            val ok = manager.registerCallback(object : CarPropertyManager.CarPropertyEventCallback {
                private var last: String? = null
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    // 같은 값이 반복해서 오므로 값이 달라질 때만 남긴다
                    val now = describe(id, value)
                    if (now == last) return
                    last = now
                    say("$name 바뀜 $now")
                }

                override fun onErrorEvent(propId: Int, zone: Int) {
                    say("$name 오류 이벤트")
                }
            }, id, CarPropertyManager.SENSOR_RATE_NORMAL)
            say("$name 구독 ${if (ok) "성공" else "실패"}")
        } catch (e: Throwable) {
            say("$name 확인 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun describe(id: Int, value: CarPropertyValue<*>?): String {
        if (value == null) return "null"
        val status = when (value.status) {
            CarPropertyValue.STATUS_AVAILABLE -> "AVAILABLE"
            CarPropertyValue.STATUS_UNAVAILABLE -> "UNAVAILABLE"
            else -> "ERROR"
        }
        val raw = value.value
        val text = when {
            id == VehiclePropertyIds.GEAR_SELECTION && raw is Int -> VehicleGear.toString(raw)
            id == VehiclePropertyIds.PERF_VEHICLE_SPEED && raw is Float -> "%.2f m/s (%.1f km/h)".format(raw, raw * 3.6f)
            else -> raw.toString()
        }
        return "$text [$status]"
    }

    // ---------- Pleos Vehicle SDK: 브레이크, 경고등, (참고용) 기어 ----------

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(pressed: Boolean?) {
            say("Pleos 브레이크 바뀜 pressed=$pressed")
        }

        override fun onFailed(e: Exception) {
            say("Pleos 브레이크 구독 실패 ${e.message}")
        }
    }

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) {
            say("Pleos 경고등 바뀜 ${summarize(telltales)}")
        }

        override fun onFailed(e: Exception) {
            say("Pleos 경고등 구독 실패 ${e.message}")
        }
    }

    private fun probePleosVehicle() {
        try {
            val v = Vehicle(this)
            v.initialize()
            vehicle = v
            say("Pleos Vehicle initialize 호출함")
            // 서비스 연결에 시간이 걸릴 수 있어 잠시 뒤에 호출한다
            main.postDelayed({ callPleosVehicle(v) }, 1500)
        } catch (e: Throwable) {
            say("Pleos Vehicle 초기화 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun callPleosVehicle(v: Vehicle) {
        try {
            val brake = v.getBrake()
            brake.checkBrakePedalStatusCapabilities(
                { say("Pleos 브레이크 지원 확인 $it") },
                { say("Pleos 브레이크 지원 확인 실패 ${it.message}") },
            )
            brake.getCurrentBrakePedalStatus(
                { say("Pleos 브레이크 현재값 pressed=$it") },
                { say("Pleos 브레이크 읽기 실패 ${it.message}") },
            )
            brake.registerBrakePedalStatus(brakeListener)
            say("Pleos 브레이크 구독 호출함")
        } catch (e: Throwable) {
            say("Pleos 브레이크 실패 ${e.javaClass.simpleName} ${e.message}")
        }
        try {
            val safety = v.getSafety()
            safety.checkTelltalesCapabilities(
                { say("Pleos 경고등 지원 확인 $it") },
                { say("Pleos 경고등 지원 확인 실패 ${it.message}") },
            )
            safety.getTelltalesInfo(
                { say("Pleos 경고등 현재값 ${summarize(it)}") },
                { say("Pleos 경고등 읽기 실패 ${it.message}") },
            )
            safety.registerTelltales(telltaleListener)
            say("Pleos 경고등 구독 호출함")
        } catch (e: Throwable) {
            say("Pleos 경고등 실패 ${e.javaClass.simpleName} ${e.message}")
        }
        try {
            v.getGear().getCurrentGearSelectionState(
                { say("Pleos 기어(참고) 현재값 $it") },
                { say("Pleos 기어(참고) 읽기 실패 ${it.message}") },
            )
        } catch (e: Throwable) {
            say("Pleos 기어(참고) 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun summarize(telltales: Telltales?): String {
        val all = telltales?.telltales ?: return "null"
        val on = all.filter { it.state != TelltaleState.OFF }
        return "전체 ${all.size}개, 켜진 것 ${on.size}개 ${on.joinToString { "${it.type}=${it.state}" }}"
    }

    // ---------- 위치 ----------

    private val locationListener = object : FusedLocationListener {
        private var count = 0
        override fun onChanged(data: LocationData?) {
            if (data == null) return
            // 위치는 자주 오므로 처음 몇 번만 남긴다
            if (count++ < 3) say("위치 수신 lat=${data.latitude} lng=${data.longitude} heading=${data.heading}")
        }

        override fun onError(e: Exception) {
            say("위치 오류 ${e.message}")
        }
    }

    private fun probeLocation() {
        try {
            val l = FusedLocation(this)
            l.initialize()
            location = l
            main.postDelayed({
                try {
                    say("FusedLocation isSupported=${l.isSupported()}")
                    l.getLastKnownLocation(locationListener)
                    l.registerFusedLocationCallback(locationListener)
                } catch (e: Throwable) {
                    say("FusedLocation 호출 실패 ${e.javaClass.simpleName} ${e.message}")
                }
            }, 1500)
        } catch (e: Throwable) {
            say("FusedLocation 초기화 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    // ---------- 음성 출력 (TTS) ----------

    private fun probeTts() {
        try {
            val t = TextToSpeech(this, ai.pleos.playground.tts.constant.Mode.ON_DEVICE)
            // SDK는 initialize가 끝난 뒤에만 다른 함수를 받는다. 순서를 바꾸면 내부 오류가 난다
            t.initialize(object : ai.pleos.playground.tts.listener.OnInitializeListener {
                override fun onSuccess() {
                    say("TTS 초기화 성공")
                    try {
                        t.addEventListener(object : EventListener {
                            override fun onStart() = say("TTS onStart")
                            override fun onReady() = say("TTS onReady")
                            override fun onDone() = say("TTS onDone")
                            override fun onError(error: ai.pleos.playground.tts.constant.ErrorType) = say("TTS onError $error")
                            override fun onStop() = say("TTS onStop")
                            override fun onUpdatedRms(rms: Double) {}
                        })
                        t.setLocale(Locale.KOREA)
                    } catch (e: Throwable) {
                        say("TTS 설정 실패 ${e.javaClass.simpleName} ${e.message}")
                    }
                }

                override fun onFailure(error: ai.pleos.playground.tts.constant.ErrorType) = say("TTS 초기화 실패 $error")
            })
            tts = t
        } catch (e: Throwable) {
            say("TTS 준비 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun speak(text: String) {
        try {
            say("TTS speak 호출: $text")
            // 두 번째 값은 "문장이 여기서 끝났다"는 표시다. false로 주면 서비스가 다음 말을 계속 기다린다
            tts?.speak(text, true)
        } catch (e: Throwable) {
            say("TTS speak 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    // ---------- 음성 인식 (STT) ----------

    private fun probeStt() {
        try {
            val s = SpeechToText(this, ai.pleos.playground.stt.constant.Mode.ON_DEVICE)
            s.addListener(object : ResultListener {
                override fun onUpdated(text: String, isFinal: Boolean) = say("STT 결과 final=$isFinal text=$text")
                override fun onUpdatedEpdData(on: Long, off: Long) {}
                override fun onStartedRecognition() = say("STT 인식 시작")
                override fun onEndedRecognition() = say("STT 인식 끝")
                override fun onError(error: ai.pleos.playground.stt.constant.ErrorType) = say("STT onError $error")
                override fun onReady() = say("STT onReady")
                override fun onUpdatedRms(rms: Double) {}
            })
            s.initialize(object : ai.pleos.playground.stt.listener.OnInitializeListener {
                override fun onSuccess() {
                    say("STT 초기화 성공")
                    // 앱이 강제로 꺼졌을 때 남은 인식 작업을 정리한다
                    try { s.stop() } catch (_: Throwable) {}
                }
                override fun onFailure(error: ai.pleos.playground.stt.constant.ErrorType) = say("STT 초기화 실패 $error")
            })
            s.setLocale(Locale.KOREA)
            stt = s
        } catch (e: Throwable) {
            say("STT 준비 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun listen() {
        try {
            say("STT request 호출")
            stt?.request()
        } catch (e: Throwable) {
            say("STT request 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    /**
     * 시험용. 디버그 빌드에 넣어 둔 음성 파일(16kHz, 16비트, 모노)을 마이크 대신 인식기에 보낸다.
     * 에뮬레이터 앞에 말할 사람이 없을 때 음성 인식이 되는지 확인하려는 용도다.
     */
    private fun listenFromFile(name: String, callRequestFirst: Boolean) {
        Thread {
            try {
                val bytes = assets.open("stt-test/$name").use { it.readBytes() }
                val pcm = bytes.copyOfRange(44, bytes.size) // WAV 머리말 44바이트를 뗀다
                say("STT 파일 전송 시작 $name ${pcm.size}바이트 request먼저=$callRequestFirst")
                if (callRequestFirst) stt?.request()
                var offset = 0
                val chunk = 3200 // 0.1초 분량
                while (offset < pcm.size) {
                    val end = minOf(offset + chunk, pcm.size)
                    stt?.sendAudio(pcm.copyOfRange(offset, end))
                    offset = end
                    Thread.sleep(100)
                }
                stt?.completeAudioSend()
                say("STT 파일 전송 끝")
            } catch (e: Throwable) {
                say("STT 파일 전송 실패 ${e.javaClass.simpleName} ${e.message}")
            }
        }.start()
    }

    private fun stopListening() {
        try {
            say("STT stop 호출")
            stt?.stop()
        } catch (e: Throwable) {
            say("STT stop 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    // ---------- 화면과 디버그 트리거 ----------

    private val debugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_TTS -> speak(intent.getStringExtra("text") ?: "기록했어요")
                ACTION_STT -> listen()
                ACTION_STT_STOP -> stopListening()
                ACTION_STT_FILE -> listenFromFile(intent.getStringExtra("name") ?: "utt1.wav", intent.getBooleanExtra("request", false))
                ACTION_DUMP -> say("DUMP 요청 받음")
            }
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply { text = "말하기 시험"; setOnClickListener { speak("기록했어요") } })
        buttons.addView(Button(this).apply { text = "듣기 시험"; setOnClickListener { listen() } })
        buttons.addView(Button(this).apply {
            text = "기록 서비스 시작"
            setOnClickListener {
                // 시험 화면의 듣기가 마이크를 잡고 있지 않도록 먼저 정리한다.
                stopListening()
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
                } else {
                    startCaptureService()
                }
            }
        })
        root.addView(buttons)
        output = TextView(this).apply { textSize = 18f }
        root.addView(ScrollView(this).apply { addView(output) })
        setContentView(root)
    }

    private fun startCaptureService() {
        try {
            startForegroundService(Intent(this, CaptureService::class.java))
            say("기록 서비스 시작 요청함")
        } catch (e: Exception) {
            say("기록 서비스 시작 실패 ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun say(line: String) {
        Log.i(TAG, line)
        main.post {
            lines.add(line)
            if (lines.size > 60) lines.removeAt(0)
            output.text = lines.joinToString("\n")
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(debugReceiver) } catch (_: Throwable) {}
        try { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) } catch (_: Throwable) {}
        try { vehicle?.getSafety()?.unregisterTelltales(telltaleListener) } catch (_: Throwable) {}
        try { vehicle?.release() } catch (_: Throwable) {}
        try { location?.unregisterFusedLocationCallback(locationListener) } catch (_: Throwable) {}
        try { location?.release() } catch (_: Throwable) {}
        try { tts?.release() } catch (_: Throwable) {}
        try { stt?.stop() } catch (_: Throwable) {}
        try { stt?.release() } catch (_: Throwable) {}
        try { car?.disconnect() } catch (_: Throwable) {}
        super.onDestroy()
    }

    companion object {
        const val TAG = "TimeStampProbe"
        const val ACTION_TTS = "io.timestamp.app.PROBE_TTS"
        const val ACTION_STT = "io.timestamp.app.PROBE_STT"
        const val ACTION_STT_STOP = "io.timestamp.app.PROBE_STT_STOP"
        const val ACTION_STT_FILE = "io.timestamp.app.PROBE_STT_FILE"
        const val ACTION_DUMP = "io.timestamp.app.PROBE_DUMP"
    }
}
