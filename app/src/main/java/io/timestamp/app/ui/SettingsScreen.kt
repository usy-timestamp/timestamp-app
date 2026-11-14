package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState
import io.timestamp.app.data.MemoInputMode

@Composable
fun PrivacyNotice() {
    Panel(Modifier.fillMaxWidth()) {
        Label("개인정보 안내", 22, bold = true)
        Label("수집: 기록 시각, 속도, 브레이크 밟음 여부, 기어, 외기온")
        Label("수집: 음성 메모 글, 부위 표시, 수리 기록")
        Label("쓰는 곳: 소리가 나는 조건을 찾고 정비소에 보여 줄 리포트를 만드는 데만 씀")
        Label("보관: 직접 지울 때까지, 기기 안에만")
        Label("동의 거부: 거부할 수 있으며, 거부하면 기록 기능을 사용할 수 없음")
        Label("목소리 녹음 파일은 저장하지 않음")
    }
}

@Composable
fun SettingsScreen(driving: DrivingState, busy: Boolean, memoMode: String,
                   chooseMemoMode: (MemoInputMode) -> Unit, voiceEnabled: Boolean, setVoiceEnabled: (Boolean) -> Unit, deleteAll: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
        Header("설정", driving = driving)
        Panel(Modifier.fillMaxWidth()) {
            CheckRow(voiceEnabled, "음성으로 기록 켜기", !busy, setVoiceEnabled)
        }
        Panel(Modifier.fillMaxWidth()) {
            Label("메모 입력 방식", 22, bold = true)
            Action("기록 후 되묻기", { chooseMemoMode(MemoInputMode.ASK_AFTER_RECORDING) }, Modifier.fillMaxWidth(),
                secondary = memoMode != MemoInputMode.ASK_AFTER_RECORDING.name, busy = busy, enabled = !busy)
            Label("명령만 말하면 기록한 뒤 어떤 소리였는지 물어요. 답은 한 번만 받아요.", color = AppColors.textSecondary)
            Action("한 번에 말하기", { chooseMemoMode(MemoInputMode.ONE_SHOT) }, Modifier.fillMaxWidth(),
                secondary = memoMode != MemoInputMode.ONE_SHOT.name, busy = busy, enabled = !busy)
            Label("“스탬프, 끼익 소리가 나”처럼 말하면 명령 뒤의 설명을 메모로 남겨요.", color = AppColors.textSecondary)
        }
        PrivacyNotice()
        Panel(Modifier.fillMaxWidth()) {
            Label("기록 관리", 22, bold = true)
            Label("모든 기록과 부위 표시, 수리 이력, 확인 이력을 지워요. 지운 내용은 되돌릴 수 없어요.", color = AppColors.textSecondary)
            Action("모든 기록 지우기", { confirm = true }, Modifier.fillMaxWidth(), secondary = true,
                busy = busy, enabled = !busy, danger = true)
        }
    }
    if (confirm) ConfirmDialog("모든 기록을 지울까요?", "기록과 수리 이력, 확인 이력이 모두 지워져요. 되돌릴 수 없어요.", "모두 지우기",
        { confirm = false }, { deleteAll(); confirm = false }, busy)
}
