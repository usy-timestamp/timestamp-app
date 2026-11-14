package io.timestamp.app.ui

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.timestamp.app.analysis.BodyParts
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.data.CaptureRecord
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState

@Composable
fun RecordListScreen(records: List<CaptureRecord>, groups: List<SymptomGroup>, driving: DrivingState,
                     openRecord: (String) -> Unit, openReport: (String) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val portrait = LocalPortrait.current
    val portraitListState = rememberLazyListState()
    val landscapeListState = rememberLazyListState()
    fun matches(record: CaptureRecord, key: String) = key == "ALL" || record.capture.status == key || (key == "OPEN" && record.capture.status == "RECURRED")
    val visible = records.filter { matches(it, filter) }
    val filters = listOf("ALL" to "전체", "OPEN" to "미해결", "REPAIRED" to "수리 완료", "RESOLVED" to "해결")
    @Composable fun filterRow(entries: List<Pair<String,String>>) {
        Row(Modifier.fillMaxWidth().background(AppColors.surfaceRaised, androidx.compose.foundation.shape.RoundedCornerShape(32.sdp)).padding(4.sdp)) {
            entries.forEach { (key,name) ->
                Box(Modifier.weight(1f).heightIn(min = 64.sdp)
                    .background(if (filter == key) AppColors.selectedFill else androidx.compose.ui.graphics.Color.Transparent,
                        androidx.compose.foundation.shape.RoundedCornerShape(28.sdp))
                    .pressClick { filter = key }.padding(horizontal = 6.sdp, vertical = 16.sdp), Alignment.Center) {
                    Label("$name "+records.count { matches(it, key) }, AppType.caption,
                        AppColors.textPrimary, bold = filter == key)
                }
            }
        }
    }
    @Composable fun summary() {
        groups.firstOrNull()?.let { group ->
            Panel(Modifier.fillMaxWidth(), finding = true) {
                Label("앱이 찾은 반복 증상", AppType.secondary, AppColors.findingText, true)
                Label("${group.name}, ${group.count}번 기록됐어요", 24, bold = true)
                Label(conditionLines(group.reproductionCondition), AppType.secondary)
                Action("리포트 보기", { openReport(group.part) }, Modifier.fillMaxWidth())
            }
        }
    }
    @Composable fun empty() {
        Panel(Modifier.fillMaxWidth()) {
            Label(if (records.isEmpty()) "아직 기록이 없어요. 주행 중 이상한 소리가 나면 “타임 스탬프 찍어줘”라고 말해 주세요." else "이 상태의 기록이 없어요.", color = AppColors.textSecondary)
        }
    }
    if (portrait) LazyColumn(Modifier.fillMaxSize().lazyScrollBar(portraitListState), state = portraitListState, verticalArrangement = Arrangement.spacedBy(16.sdp)) {
        item { Header("기록", "${records.size}건의 순간이 남아 있어요", driving) }
        item { Column(verticalArrangement = Arrangement.spacedBy(10.sdp)) { filterRow(filters) } }
        item { summary() }
        if (visible.isEmpty()) item { empty() }
        items(visible, key = { it.capture.id }) { RecordRow(it, true, openRecord) }
    } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
        Header("기록", "${records.size}건의 순간이 남아 있어요", driving)
        filterRow(filters)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.sdp)) {
            LazyColumn(Modifier.weight(1f).fillMaxHeight().lazyScrollBar(landscapeListState), state = landscapeListState, verticalArrangement = Arrangement.spacedBy(12.sdp)) {
                if (visible.isEmpty()) item { empty() }
                items(visible, key = { it.capture.id }) { RecordRow(it, false, openRecord) }
            }
            Column(Modifier.width(360.sdp).fillMaxHeight().scrollWithBar(), verticalArrangement = Arrangement.spacedBy(16.sdp)) {
                summary()
                Panel {
                    Label("새로 기록하려면 주행 중에", AppType.secondary, AppColors.textSecondary)
                    Label("“타임 스탬프 찍어줘”", 20, AppColors.primaryText, true)
                }
            }
        }
    }
}

@Composable
private fun RecordRow(record: CaptureRecord, portrait: Boolean, open: (String) -> Unit) {
    @Composable fun date() {
        Column {
            Label(dayText(record.capture.createdAtMs), AppType.secondary, bold = true)
            Label(timeText(record.capture.createdAtMs), AppType.caption, AppColors.textSecondary)
        }
    }
    @Composable fun memo(modifier: Modifier) {
        DriverMemoText(record.capture.memo, modifier, maxLines = 3)
    }
    @Composable fun parts() {
        Column(verticalArrangement = Arrangement.spacedBy(5.sdp)) {
            if (record.bodyMarks.isEmpty()) Tag("부위 표시하기", AppColors.primaryText, dashed = true)
            else record.bodyMarks.map { BodyParts.displayName(it.part) }.distinct().forEach { Tag(it) }
        }
    }
    Panel(Modifier.fillMaxWidth().heightIn(min = 58.sdp).pressClick { open(record.capture.id) }, padding = 20) {
        if (portrait) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.sdp)) { date(); memo(Modifier.weight(1f)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { parts(); StatusTag(record.capture.status) }
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.sdp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.sdp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { date(); StatusTag(record.capture.status) }
                memo(Modifier.fillMaxWidth())
                parts()
            }
        }
    }
}