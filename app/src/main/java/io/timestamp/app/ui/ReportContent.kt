package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.ui.theme.*

fun speedText(group: SymptomGroup) = group.speedRange?.let { it.displayText }
fun brakeText(group: SymptomGroup) = group.brakeText
fun reminderEnabled(parts: String, part: String) = part in parts.split('|').map(io.timestamp.app.analysis.BodyParts::displayName)

@Composable
fun ConditionCard(group: SymptomGroup, modifier: Modifier = Modifier, shop: Boolean = false) {
    Panel(modifier, finding = true, padding = if (shop) 36 else 28) {
        Label("소리가 나는 조건",
            if (shop) 20 else 18, AppColors.findingText, true)
        // 큰 글씨 화면에서는 낱말 중간에서 줄이 바뀌지 않게 속도 뒤에서 줄을 나눈다
        val sentence = conditionLines(group.reproductionCondition)
        Label(sentence, if (shop) AppType.shopSentence else if (LocalPortrait.current) 36 else AppType.conditionSentence, bold = true)
        if (shop) {
            Spacer(Modifier.height(20.sdp))
            Label("${group.count}번의 기록을 바탕으로 정리했어요.\n기어: ${group.gear ?: "수집 불가"}", 20, AppColors.textOnCard)
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
            ValueCell("속도", speedText(group), Modifier.weight(1.2f))
            ValueCell("브레이크", brakeText(group), Modifier.weight(1.2f))
            ValueCell("기어", group.gear, Modifier.weight(.7f))
        }
    }
}

@Composable
fun DashcamCard(group: SymptomGroup, modifier: Modifier = Modifier, shop: Boolean = false) {
    Panel(modifier, padding = 26) {
        Label("블랙박스에서 볼 구간", AppType.cardTitle, bold = true)
        Label("말하기 5초 전부터 말한 순간까지", 18, AppColors.textSecondary)
        group.dashcamWindows.forEach { window ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.sdp)) {
                Label(dayText(window.fromMs), if (shop) 21 else 18, AppColors.textSecondary)
                Label("${secondsText(window.fromMs)}부터 5초", if (shop) 21 else 18, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DriverQuotes(group: SymptomGroup, modifier: Modifier = Modifier, shop: Boolean = false) {
    val memos = driverMemos(group)
    if (memos.isEmpty()) return
    Panel(modifier) {
        Label("운전자 메모", AppType.cardTitle, bold = true)
        memos.forEach { memo ->
            Label("“$memo”", if (shop) 21 else 18, AppColors.textOnCard)
        }
    }
}

@Composable
fun MissingNotice(group: SymptomGroup) {
    if (group.unavailableItems.isNotEmpty()) {
        Label("${group.unavailableItems.joinToString(", ")}은 수집 불가로 리포트에서 빠졌어요.", 18, AppColors.textTertiary)
    }
    val incomplete = group.records.any { record ->
        (-5..-1).any { offset -> record.samples.none { it.offsetSec == offset && it.speedKmh != null && it.brakePressed != null && it.gear != null } }
    }
    if (incomplete) Label("일부 시각의 차량 정보가 없어 읽힌 값만으로 정리했어요.", 18, AppColors.textTertiary)
}
