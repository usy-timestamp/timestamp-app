package io.timestamp.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.ui.theme.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RepairDialog(group: SymptomGroup, dismiss: () -> Unit, save: (Long, String?) -> Unit, busy: Boolean) {
    var dateText by rememberSaveable(group.part) { mutableStateOf(LocalDate.now().toString()) }
    var memo by rememberSaveable(group.part) { mutableStateOf("") }
    var selected by rememberSaveable(group.part) { mutableStateOf(true) }
    val date = LocalDate.parse(dateText)
    val context = LocalContext.current
    val portrait = LocalPortrait.current
    var picker by remember { mutableStateOf<DatePickerDialog?>(null) }
    // 화면이 사라지면 날짜 선택 창도 닫아 보이지 않는 상호작용이 남지 않게 한다.
    DisposableEffect(Unit) { onDispose { picker?.dismiss() } }
    Dialog({ if (!busy) dismiss() }, DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(Modifier.widthIn(max = 760.sdp).fillMaxWidth(.94f), padding = if (portrait) 24 else 32, popup = true) {
            Column(Modifier.scrollWithBar(), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
                Label("수리 완료로 표시할까요?", 32, bold = true, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Label("${group.name}, 기록 ${group.count}건", AppType.body, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                CheckRow(selected, "이 증상 기록에 적용", !busy) { selected = it }
                @Composable fun dateField(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                        Label("수리 날짜", AppType.secondary, AppColors.textSecondary)
                        Action(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)), {
                            picker = DatePickerDialog(context, { _, year, month, day ->
                                dateText = LocalDate.of(year, month + 1, day).toString()
                            }, date.year, date.monthValue - 1, date.dayOfMonth).also {
                                it.datePicker.maxDate = System.currentTimeMillis()
                                it.show()
                            }
                        }, Modifier.fillMaxWidth(), secondary = true, busy = busy, enabled = !busy)
                    }
                }
                @Composable fun memoField(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                        Label("정비 내용 (선택)", AppType.secondary, AppColors.textSecondary)
                        OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth().heightIn(min = 58.sdp), enabled = !busy,
                            placeholder = { Label("예: 브레이크 패드 교체", AppType.secondary, AppColors.textSecondary) },
                            textStyle = LocalTextStyle.current.copy(fontSize = AppType.body.ssp, lineHeight = 34.ssp), maxLines = 4)
                    }
                }
                if (portrait) {
                    dateField(Modifier.fillMaxWidth())
                    memoField(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.sdp), verticalAlignment = Alignment.Bottom) {
                    dateField(Modifier.weight(1f)); memoField(Modifier.weight(1.3f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                    Action("수리 완료", {
                        save(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), memo.trim().ifBlank { null })
                    }, Modifier.weight(1f), enabled = selected && !busy, busy = busy)
                    Action("취소", dismiss, Modifier.weight(1f), secondary = true, busy = busy, enabled = !busy)
                }
            }
        }
    }
}