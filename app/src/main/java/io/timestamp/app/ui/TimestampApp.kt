package io.timestamp.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
fun TimestampApp(model: TimestampViewModel, drivingFlow: StateFlow<DrivingState>, permissions: PermissionState,
                 resumed: Boolean, requestPermissions: (String) -> Unit, startCapture: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val driving by drivingFlow.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var page by rememberSaveable { mutableStateOf("list") }
    var recordId by rememberSaveable { mutableStateOf("") }
    var groupPart by rememberSaveable { mutableStateOf("") }
    var showRepair by rememberSaveable { mutableStateOf(false) }
    var consent by rememberSaveable { mutableStateOf(false) }
    var recordsVersion by rememberSaveable { mutableIntStateOf(0) }
    val screens = key(recordsVersion) { rememberSaveableStateHolder() }
    val group = state.groups.firstOrNull { it.part == groupPart } ?: state.groups.firstOrNull()
    val ready = state.settings?.onboardingCompleted == true && state.settings?.consentAtMs != null && permissions.requiredGranted
    val today by produceState(LocalDate.now()) {
        while (true) { delay(30_000); value = LocalDate.now() }
    }
    val todayCount = state.records.count {
        Instant.ofEpochMilli(it.capture.createdAtMs).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    fun message(value: String) { Toast.makeText(context, value, Toast.LENGTH_LONG).show() }

    LaunchedEffect(error) { error?.let { message(it); model.error.value = null } }
    LaunchedEffect(ready, resumed) { if (ready && resumed) startCapture() }
    LaunchedEffect(driving.showDrivingScreen) { if (driving.showDrivingScreen) showRepair = false }
    BackHandler(enabled = driving.showDrivingScreen || page != "list") {
        if (!driving.showDrivingScreen) page = if (page == "shop") "report" else "list"
    }
    TimestampTheme {
        WindowScale(Modifier.fillMaxSize().background(AppColors.background)) {
            when {
                driving.showDrivingScreen -> DrivingScreen(driving, todayCount)
                state.settings == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                !ready -> OnboardingScreen(permissions, consent, { consent = it }, requestPermissions, {
                    if (!consent) {
                        message("안내 내용을 확인하고 동의해야 시작할 수 있어요.")
                    } else if (!permissions.requiredGranted) {
                        message(when {
                            !permissions.microphone -> "말 한마디로 기록하려면 마이크 권한이 필요해요."
                            else -> "차 상태를 남기려면 차량 속도와 기어 권한이 필요해요."
                        })
                    } else model.finishOnboarding(permissions, consent) { startCapture() }
                }, busy)
                page == "shop" && group != null -> ShopScreen(group, { page = "report" },
                    reminderEnabled(state.settings!!.testDriveReminderParts, group.part), { model.toggleReminder(group.part) }, busy)
                else -> AppShell(page, { destination ->
                    page = destination
                }) {
                    screens.SaveableStateProvider(if (page == "detail") "detail:$recordId" else page) {
                    when (page) {
                        "settings" -> SettingsScreen(driving, busy, state.settings!!.memoInputMode, model::setMemoInputMode) {
                            model.deleteAll {
                                // 지운 기록의 부위나 수리 메모가 저장된 화면 상태에도 남지 않게 한다.
                                recordsVersion++
                                recordId = ""
                                groupPart = ""
                                showRepair = false
                                message("모든 기록을 지웠어요")
                            }
                        }
                        "detail" -> {
                            val record = state.records.firstOrNull { it.capture.id == recordId }
                            if (record == null) {
                                LaunchedEffect(recordId) { page = "list" }
                            } else RecordDetailScreen(record, driving, { page = "list" },
                                { parts -> model.saveParts(recordId, parts) { page = "list" } },
                                { model.delete(recordId) { page = "list" } }, busy)
                        }
                        "report", "shop" -> {
                            ReportScreen(state.groups, group, driving, { groupPart = it }, { page = "shop" },
                                { showRepair = true }, group?.let { reminderEnabled(state.settings!!.testDriveReminderParts, it.part) } == true,
                                { group?.let { model.toggleReminder(it.part) } }, busy)
                            if (showRepair && group != null) RepairDialog(group, { showRepair = false },
                                { date, memo -> model.completeRepair(group, date, memo) { showRepair = false; message("수리 완료로 표시했어요") } }, busy)
                        }
                        else -> RecordListScreen(state.records, state.groups, driving,
                            { recordId = it; page = "detail" }, { groupPart = it; page = "report" })
                    }
                    }
                }
            }
        }
    }
}
