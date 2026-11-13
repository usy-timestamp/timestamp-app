package io.timestamp.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.ui.theme.AppColors

fun driverMemos(group: SymptomGroup) = group.records.mapNotNull {
    it.capture.memo?.trim()?.takeIf(String::isNotBlank)
}.distinct()

// 조건의 뜻이 나뉘는 곳에서 줄을 바꿔 마지막 낱말만 다음 줄에 남지 않게 한다.
fun conditionLines(sentence: String) = sentence.replace("km에서 ", "km에서\n")
    .replace("km로 ", "km로\n").replace("밟을 때", "밟을\u00A0때")

@Composable
fun DriverMemoText(memo: String?, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    val value = memo?.trim()?.takeIf(String::isNotBlank)
    Label(if (value == null) "메모 없음" else "“$value”", 18,
        if (value == null) AppColors.textSecondary else AppColors.textPrimary,
        modifier = modifier, maxLines = maxLines)
}