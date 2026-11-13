package io.timestamp.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
        Panel(Modifier.widthIn(max = 640.sdp).fillMaxWidth(.94f), padding = if (portrait) 24 else 32) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.sdp)) {
                Label("수리 완료로 표시할까요?", 28, bold = true)
                CheckRow(selected, "${group.name}, 기록 ${group.count}건", !busy) { selected = it }
                @Composable fun dateField(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                        Label("수리 날짜", 18, AppColors.textSecondary)
                        Action(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)), {
                            picker = DatePickerDialog(context, { _, year, month, day ->
                                dateText = LocalDate.of(year, month + 1, day).toString()
                            }, date.year, date.monthValue - 1, date.dayOfMonth).also {
                                it.datePicker.maxDate = System.currentTimeMillis()
                                it.show()
                            }
                        }, Modifier.fillMaxWidth(), secondary = true, enabled = !busy)
                    }
                }
                @Composable fun memoField(modifier: Modifier) {
                    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.sdp)) {
                        Label("정비 내용 (선택)", 18, AppColors.textSecondary)
                        OutlinedTextField(memo, { memo = it }, Modifier.fillMaxWidth().heightIn(min = 58.sdp), enabled = !busy,
                            placeholder = { Label("예: 브레이크 패드 교체", 18, AppColors.textSecondary) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 18.ssp), maxLines = 4)
                    }
                }
                if (portrait) {
                    dateField(Modifier.fillMaxWidth())
                    memoField(Modifier.fillMaxWidth())
                } else Row(horizontalArrangement = Arrangement.spacedBy(16.sdp), verticalAlignment = Alignment.Bottom) {
                    dateField(Modifier.weight(1f)); memoField(Modifier.weight(1.3f))
                }
                Label("수리 후 같은 조건이 오면 “아직 나나요?”라고 한 번 물어볼게요. 같은 증상은 하루에 한 번까지만 물어요.", 18)
                Label("수리 후 음성 확인은 준비 중이에요. 지금은 수리 완료만 저장해요.", 14, AppColors.textSecondary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                    Action("취소", dismiss, Modifier.weight(1f), secondary = true, enabled = !busy)
                    Action("수리 완료", {
                        save(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), memo.trim().ifBlank { null })
                    }, Modifier.weight(1f), enabled = selected && !busy)
                }
            }
        }
    }
}