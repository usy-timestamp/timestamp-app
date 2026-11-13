package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.ui.theme.*

@Composable
fun ShopScreen(group: SymptomGroup, close: () -> Unit, reminder: Boolean, toggleReminder: () -> Unit, busy: Boolean) {
    val portrait = LocalPortrait.current
    Column(Modifier.fillMaxSize().padding((if (portrait) 24 else 40).sdp)
        .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.sdp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Label("정비사님께 보여 드리는 화면", 18, AppColors.textSecondary)
                Label(group.name, 36, bold = true)
            }
            Action("닫기", close, secondary = true)
        }
        if (portrait) {
            ConditionCard(group, Modifier.fillMaxWidth(), shop = true)
            DashcamCard(group, Modifier.fillMaxWidth(), shop = true)
            GroupSummary(group, Modifier.fillMaxWidth())
            MissingNotice(group)
        } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.sdp)) {
            // 카드가 남은 높이에 눌리지 않게 내용 높이를 그대로 재고 화면 전체를 스크롤한다.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
                ConditionCard(group, Modifier.fillMaxWidth(), shop = true)
                MissingNotice(group)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
                DashcamCard(group, Modifier.fillMaxWidth(), shop = true)
                GroupSummary(group, Modifier.fillMaxWidth())
            }
        }
        Action("시운전 알림", toggleReminder, if (portrait) Modifier.fillMaxWidth() else Modifier, enabled = !busy)
        Label("시운전 알림 ${if (reminder) "켜짐" else "꺼짐"}. 알림 기능은 준비 중이며 선택만 저장돼요.", 14, AppColors.textSecondary)
    }
}
