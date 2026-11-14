package io.timestamp.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.timestamp.app.analysis.BodyParts
import io.timestamp.app.data.CaptureRecord
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState
import kotlin.math.roundToInt

@Composable
fun RecordDetailScreen(record: CaptureRecord, driving: DrivingState, back: () -> Unit,
                       save: (Set<String>) -> Unit, delete: () -> Unit, busy: Boolean) {
    var partsText by rememberSaveable(record.capture.id) {
        mutableStateOf(record.bodyMarks.map { BodyParts.displayName(it.part) }.distinct().joinToString("|"))
    }
    val selected = partsText.split('|').filter(String::isNotBlank).toSet()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val trigger = record.samples.firstOrNull { it.offsetSec == 0 }
    val portrait = LocalPortrait.current
    val toggle: (String) -> Unit = { part ->
        partsText = (if (part in selected) selected - part else selected + part).joinToString("|")
    }
    @Composable fun values() {
        val cells = listOf("말한 순간 속도" to trigger?.speedKmh?.takeIf(Float::isFinite)?.let { "시속 ${it.roundToInt()}km" },
            "브레이크" to trigger?.brakePressed?.let { if (it) "밟음" else "안 밟음" },
            "기어" to trigger?.gear, "외기온" to trigger?.outsideTempC?.takeIf(Float::isFinite)?.let { "${it}°C" })
        Column(verticalArrangement = Arrangement.spacedBy(12.sdp)) {
            cells.chunked(if (portrait) 2 else 4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                    row.forEach { (title,value) -> ValueCell(title, value, Modifier.weight(1f)) }
                }
            }
        }
    }
    @Composable fun actions() {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
            Action("이 기록 지우기", { confirmDelete = true }, Modifier.weight(1f), secondary = true, busy = busy, enabled = !busy)
            Action("부위 저장", { save(selected) }, Modifier.weight(1f), busy = busy, enabled = !busy)
        }
    }
    Column(Modifier.fillMaxSize().then(if (portrait) Modifier.scrollWithBar() else Modifier),
        verticalArrangement = Arrangement.spacedBy(20.sdp)) {
        Header("${dateTimeText(record.capture.createdAtMs)} 기록", driving = driving, back = back)
        Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
            Label("운전자 메모", AppType.secondary, AppColors.textSecondary)
            DriverMemoText(record.capture.memo, Modifier.weight(1f))
        }
        if (portrait) {
            PartPicker(selected, toggle, Modifier.fillMaxWidth())
            SignalGraph(record.samples, Modifier.fillMaxWidth())
            values()
            actions()
        } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.sdp)) {
            Column(Modifier.width(maxOf(CarDiagramLayout.pickerWidth.sdp, CarDiagramLayout.minimumPickerWidth.dp))
                .fillMaxHeight().scrollWithBar()) {
                PartPicker(selected, toggle, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(1f).fillMaxHeight().scrollWithBar(), verticalArrangement = Arrangement.spacedBy(16.sdp)) {
                SignalGraph(record.samples, Modifier.fillMaxWidth())
                values()
                actions()
            }
        }
    }
    if (confirmDelete) ConfirmDialog("이 기록을 지울까요?", "지운 기록과 부위 표시는 되돌릴 수 없어요.", "지우기",
        { confirmDelete = false }, delete, busy)
}

@Composable
fun ValueCell(title: String, value: String?, modifier: Modifier = Modifier) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(13.sdp)
    androidx.compose.material3.Surface(modifier, color = AppColors.surfaceRaised, shape = shape) {
        Column(Modifier.padding(horizontal = 14.sdp, vertical = 16.sdp), verticalArrangement = Arrangement.spacedBy(4.sdp)) {
            Label(title, AppType.caption, AppColors.textSecondary)
            Label(value ?: "수집 불가", AppType.secondary, if (value == null) AppColors.textSecondary else AppColors.textPrimary, value != null)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, dismiss: () -> Unit, accept: () -> Unit, busy: Boolean) {
    androidx.compose.ui.window.Dialog({ if (!busy) dismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(Modifier.widthIn(max = 760.sdp).fillMaxWidth(.94f), popup = true) {
            Column(Modifier.scrollWithBar(), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
                Label(title, 32, bold = true, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Label(message, AppType.body, AppColors.textSecondary, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                    Action(confirm, accept, Modifier.weight(1f), busy = busy, enabled = !busy)
                    Action("취소", dismiss, Modifier.weight(1f), secondary = true, busy = busy, enabled = !busy)
                }
            }
        }
    }
}
