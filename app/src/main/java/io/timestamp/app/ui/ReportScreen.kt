package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState

@Composable
fun ReportScreen(groups: List<SymptomGroup>, group: SymptomGroup?, driving: DrivingState,
                 choose: (String) -> Unit, shop: () -> Unit, repair: () -> Unit, reminder: Boolean,
                 toggleReminder: () -> Unit, busy: Boolean) {
    val portrait = LocalPortrait.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().then(if (portrait) Modifier.verticalScroll(rememberScrollState()) else Modifier),
        verticalArrangement = Arrangement.spacedBy(18.sdp)) {
        if (group == null) {
            Header("리포트", driving = driving)
            Panel(Modifier.fillMaxWidth()) {
                Label("아직 반복 증상이 없어요", 24, bold = true)
                Label("같은 부위로 표시한 기록이 2건 이상이면 리포트를 만들어요.", color = AppColors.textSecondary)
            }
            return@Column
        }
        val firstDay = dayText(group.records.minOf { it.capture.createdAtMs })
        val lastDay = dayText(group.records.maxOf { it.capture.createdAtMs })
        val period = if (firstDay == lastDay) firstDay else "${firstDay}부터 ${lastDay}까지"
        Header(group.name, "기록 ${group.count}건, $period", driving)
        if (groups.size > 1) Box {
            Action("증상 고르기 (${groups.size}개)", { expanded = true }, secondary = true)
            DropdownMenu(expanded, { expanded = false }) {
                groups.forEach { item ->
                    Box(Modifier.widthIn(max = 380.sdp).fillMaxWidth().heightIn(min = 58.sdp)
                        .pressClick { choose(item.part); expanded = false }.padding(16.sdp)) {
                        Label("${item.name} (${item.count}건)")
                    }
                }
            }
        }
        @Composable fun actions() {
            Action("정비소에 보여주기", shop, Modifier.fillMaxWidth())
            Action("수리 완료", repair, Modifier.fillMaxWidth(), secondary = true, enabled = !busy, busy = busy)
        }
        if (portrait) {
            ConditionCard(group, Modifier.fillMaxWidth())
            GroupSummary(group, Modifier.fillMaxWidth())
            DashcamCard(group, Modifier.fillMaxWidth())
            MissingNotice(group)
            actions()
        } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.sdp)) {
            Column(Modifier.weight(1.9f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.sdp)) {
                ConditionCard(group, Modifier.fillMaxWidth())
                DashcamCard(group, Modifier.fillMaxWidth())
                MissingNotice(group)
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.sdp)) {
                GroupSummary(group, Modifier.fillMaxWidth())
                actions()
            }
        }
    }
}

@Composable
fun GroupSummary(group: SymptomGroup, modifier: Modifier = Modifier) {
    val portrait = LocalPortrait.current
    Panel(modifier, padding = 20) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.sdp)) {
            CarDiagram(setOf(group.part), Modifier.width((if (portrait) 64 else 106).sdp)
                .height((if (portrait) 120 else 198).sdp), compact = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.sdp)) {
                Label(group.part, 22, bold = true)
                Label("${group.count}건 모두 같은 부위", AppType.secondary, AppColors.textSecondary)
                if (group.records.all { it.capture.status == "REPAIRED" }) StatusTag("REPAIRED")
            }
        }
        val memos = driverMemos(group)
        if (memos.isNotEmpty()) {
            androidx.compose.material3.HorizontalDivider(color = AppColors.border)
            Label("운전자 메모", AppType.secondary, AppColors.textSecondary)
            memos.forEach { DriverMemoText(it, Modifier.fillMaxWidth()) }
        }
    }
}
