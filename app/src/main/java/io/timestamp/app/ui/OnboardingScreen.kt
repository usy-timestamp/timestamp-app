package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import io.timestamp.app.ui.theme.*

data class PermissionState(val microphone: Boolean = false, val carSpeed: Boolean = false,
                           val carPowertrain: Boolean = false) {
    val requiredGranted get() = microphone && carSpeed && carPowertrain
}

@Composable
fun OnboardingScreen(permissions: PermissionState, consent: Boolean, agree: (Boolean) -> Unit,
                     request: (String) -> Unit, start: () -> Unit, busy: Boolean) {
    val portrait = LocalPortrait.current
    @Composable fun introduction() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.sdp)) {
            LineIcon("mic", Modifier.size(48.sdp))
            Label("타임 스탬프", 24, bold = true)
        }
        Label("이상한 소리가 나면\n말 한마디로 그 순간을 남겨요", 36, bold = true)
        Panel(Modifier.fillMaxWidth()) {
            Label("운전 중에 이렇게 말하세요", AppType.secondary, AppColors.textSecondary)
            Label("“타임 스탬프 찍어줘”", 30, AppColors.primaryText, true)
            Label("스탬프, 타임 스탬프라고만 말해도 돼요", AppType.secondary)
            Label("기록한 뒤 어떤 소리였는지 한 번 물어봐요. 설정에서 바꿀 수 있어요.", AppType.secondary)
            Label("말하기 전후 30초의 속도, 브레이크, 기어가 저장돼요. 소리는 말하기 몇 초 전에 났으니 앞부분까지 남깁니다.", AppType.secondary, AppColors.textSecondary)
        }
    }
    @Composable fun permissionsAndConsent() {
        Label("시작하려면 허용해 주세요", 22, bold = true)
        PermissionCard("마이크", "한마디를 알아듣기 위해 (필수)", permissions.microphone) { request("microphone") }
        PermissionCard("차량 속도와 기어", "그 순간 차 상태를 남기기 위해 (필수)", permissions.carSpeed && permissions.carPowertrain) { request("car") }
        PrivacyNotice()
        CheckRow(consent, "위 내용을 확인했고 동의합니다", !busy, agree)
        if (!consent) Label("안내 내용을 확인하고 동의해야 시작할 수 있어요.", AppType.secondary, AppColors.textSecondary)
        Box(Modifier.fillMaxWidth()) {
            Action("시작하기", start, Modifier.fillMaxWidth(), busy = busy, enabled = consent && !busy)
            if (!consent && !busy) Box(Modifier.matchParentSize().pressClick { start() })
        }
    }
    if (portrait) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.sdp),
        verticalArrangement = Arrangement.spacedBy(20.sdp)) {
        introduction()
        permissionsAndConsent()
    } else Row(Modifier.fillMaxSize().padding(horizontal = 44.sdp, vertical = 36.sdp),
        horizontalArrangement = Arrangement.spacedBy(36.sdp)) {
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.sdp)) { introduction() }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.sdp)) { permissionsAndConsent() }
    }
}

@Composable
private fun PermissionCard(title: String, why: String, granted: Boolean, request: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), padding = 18) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.sdp)) {
            Column(Modifier.weight(1f)) { Label(title, 20, bold = true); Label(why, AppType.secondary, AppColors.textSecondary) }
            if (granted) Label("허용됨", AppType.secondary, AppColors.resolved, true) else Action("허용", request)
        }
    }
}
