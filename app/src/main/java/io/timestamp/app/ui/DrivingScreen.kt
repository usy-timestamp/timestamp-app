package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState

@Composable
fun DrivingScreen(driving: DrivingState, todayCount: Int) {
    val portrait = LocalPortrait.current
    Column(Modifier.fillMaxSize().padding((if (portrait) 24 else 36).sdp),
        verticalArrangement = Arrangement.spacedBy(24.sdp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                LineIcon("mic", Modifier.size(32.sdp))
                Label("타임 스탬프", 20, bold = true)
            }
            GearChip(driving)
        }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.sdp)) {
                LineIcon("mic", Modifier.size(96.sdp))
                Label("기록은 정차 후 확인할 수 있어요", if (portrait) 28 else 36, bold = true)
                Label("이상한 소리가 나면 “타임 스탬프 찍어줘”라고 말해 주세요", 24)
                Tag("오늘 기록 ${todayCount}건", AppColors.textSecondary)
            }
        }
    }
}