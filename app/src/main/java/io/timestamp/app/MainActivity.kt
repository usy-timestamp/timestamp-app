package io.timestamp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.timestamp.app.capture.CaptureService
import io.timestamp.app.ui.PermissionState
import io.timestamp.app.ui.TimestampApp
import io.timestamp.app.ui.TimestampViewModel
import io.timestamp.app.vehicle.GearWatcher
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var gearWatcher: GearWatcher
    private var permissions by mutableStateOf(PermissionState())
    private var resumed by mutableStateOf(false)
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshPermissions()
        gearWatcher.refreshPermissions()
        if (it.values.any { allowed -> !allowed }) {
            Toast.makeText(this, "허용하지 않은 권한은 기기 설정에서 다시 켤 수 있어요.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gearWatcher = GearWatcher(this, gearOnly = true)
        refreshPermissions()
        // 주행을 시작하면 화면을 스스로 내려 내비나 다른 앱을 가리지 않는다. 기록은 서비스가 계속 받는다.
        // 달리는 중에 운전자가 직접 앱을 열었을 때는 내리지 않고 안내 화면을 보여 준다.
        lifecycleScope.launch {
            var wasDriving: Boolean? = null
            gearWatcher.state.collect { state ->
                if (state.gear == null && !state.restricted) return@collect
                val driving = state.showDrivingScreen
                if (wasDriving == false && driving && resumed) leaveScreen()
                wasDriving = driving
            }
        }
        setContent {
            val model: TimestampViewModel = viewModel()
            TimestampApp(model, gearWatcher.state, permissions, resumed, ::requestPermissions, ::startCapture)
        }
    }

    override fun onStart() { super.onStart(); gearWatcher.start() }
    override fun onResume() {
        super.onResume()
        refreshPermissions()
        gearWatcher.refreshPermissions()
        resumed = true
    }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onStop() { gearWatcher.stop(); super.onStop() }

    // 차량 화면에서는 창을 뒤로 보내는 요청이 통하지 않아 창을 닫는다. 그러면 내비 같은 앞서 보던 화면이 나온다.
    private fun leaveScreen() {
        if (BuildConfig.DEBUG) android.util.Log.i("TimeStampCapture", "주행 시작, 화면을 내림")
        finishAndRemoveTask()
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun refreshPermissions() {
        permissions = PermissionState(granted(Manifest.permission.RECORD_AUDIO), granted(CAR_SPEED),
            granted(CAR_POWERTRAIN))
    }

    private fun requestPermissions(kind: String) {
        val wanted = when (kind) {
            "microphone" -> arrayOf(Manifest.permission.RECORD_AUDIO)
            "car" -> arrayOf(CAR_SPEED, CAR_POWERTRAIN)
            else -> return
        }
        permissionRequest.launch(wanted.filterNot(::granted).toTypedArray())
    }

    private fun startCapture() {
        if (!resumed || !permissions.requiredGranted) return
        try {
            ContextCompat.startForegroundService(this, Intent(this, CaptureService::class.java))
        } catch (_: Exception) {
            Toast.makeText(this, "기록 준비를 시작하지 못했어요. 앱을 다시 열어 주세요.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val CAR_SPEED = "android.car.permission.CAR_SPEED"
        private const val CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"
    }
}
