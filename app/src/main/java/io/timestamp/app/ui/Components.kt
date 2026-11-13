package io.timestamp.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.timestamp.app.ui.theme.*
import io.timestamp.app.vehicle.DrivingState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Label(value: String, size: Int = AppType.body, color: Color = AppColors.textPrimary,
          bold: Boolean = false, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(value, modifier, color, fontSize = size.coerceAtLeast(14).ssp, lineHeight = size.coerceAtLeast(14).ssp * 1.45f,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Panel(modifier: Modifier = Modifier, finding: Boolean = false, padding: Int = 24,
          content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(AppCorners.card.sdp),
        color = if (finding) AppColors.findingTint else AppColors.surface,
        border = BorderStroke((if (finding) 2 else 1).sdp, if (finding) AppColors.finding else AppColors.border)) {
        Column(Modifier.padding(padding.sdp), verticalArrangement = Arrangement.spacedBy(14.sdp), content = content)
    }
}

@Composable
fun Action(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
           secondary: Boolean = false, enabled: Boolean = true, height: Int = 58, danger: Boolean = false) {
    val interaction = remember { MutableInteractionSource() }
    Button(onClick, modifier.sizeIn(minWidth = 58.sdp, minHeight = height.coerceAtLeast(58).sdp).pressEffect(interaction), enabled = enabled,
        interactionSource = interaction, elevation = ButtonDefaults.buttonElevation(0.sdp, 0.sdp, 0.sdp, 0.sdp, 0.sdp),
        shape = RoundedCornerShape(AppCorners.button.sdp),
        border = if (secondary) BorderStroke(1.sdp, AppColors.borderStrong) else null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (secondary) Color.Transparent else AppColors.primary,
            contentColor = if (danger) AppColors.danger else if (secondary) AppColors.textPrimary else AppColors.onPrimary),
        contentPadding = PaddingValues(horizontal = 22.sdp, vertical = 12.sdp)) {
        Label(label, 18, if (!enabled) AppColors.textSecondary else if (danger) AppColors.danger else if (secondary) AppColors.textPrimary else AppColors.onPrimary, bold = !secondary)
    }
}

@Composable
fun Modifier.dashedBorder(color: Color): Modifier {
    val stroke = 1.sdp
    val corner = 10.sdp
    return drawWithContent {
        drawContent()
        val width = stroke.toPx()
        drawRoundRect(color, Offset(width / 2, width / 2), Size(size.width - width, size.height - width),
            CornerRadius(corner.toPx()), style = Stroke(width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(width * 5, width * 4))))
    }
}

@Composable
fun Tag(value: String, color: Color = AppColors.textOnCard, dashed: Boolean = false) {
    Box(Modifier.then(if (dashed) Modifier.dashedBorder(color) else Modifier)
        .background(if (dashed) Color.Transparent else color.copy(alpha = .12f), RoundedCornerShape(AppCorners.chip.sdp))
        .padding(horizontal = 12.sdp, vertical = 7.sdp)) { Label(value, 18, color, maxLines = 2) }
}

fun statusName(status: String) = when (status) {
    "REPAIRED" -> "수리 완료"
    "RESOLVED" -> "해결"
    "RECURRED" -> "재발"
    else -> "미해결"
}

@Composable
fun StatusTag(status: String) = Tag(statusName(status), when (status) {
    "REPAIRED" -> AppColors.resolvedText
    "RESOLVED" -> AppColors.resolvedText
    "RECURRED" -> AppColors.danger
    else -> AppColors.textSecondary
})

@Composable
fun GearChip(state: DrivingState) {
    Surface(shape = CircleShape, color = AppColors.surface, border = BorderStroke(1.sdp, AppColors.border)) {
        Row(Modifier.padding(horizontal = 16.sdp, vertical = 9.sdp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.sdp)) {
            Box(Modifier.size(10.sdp).background(when {
                state.showDrivingScreen -> AppColors.finding
                state.gear == "P" -> AppColors.resolved
                else -> AppColors.textTertiary
            }, CircleShape))
            Label(when {
                state.showDrivingScreen -> "${state.gear ?: ""} 주행 중".trim()
                state.gear == "P" -> "P 정차 중"
                else -> "기어 수집 불가"
            }, 18, AppColors.textSecondary)
        }
    }
}

@Composable
fun Header(title: String, subtitle: String? = null, driving: DrivingState, back: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.sdp)) {
        if (back != null) Action("뒤로", back, secondary = true)
        Column(Modifier.weight(1f)) {
            Label(title, AppType.screenTitle, bold = true)
            subtitle?.let { Label(it, 18, AppColors.textSecondary) }
        }
        GearChip(driving)
    }
}

// 누르는 동안만 크기를 줄여 눌렀다는 사실을 바로 알 수 있게 한다.
fun Modifier.pressEffect(source: MutableInteractionSource): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    graphicsLayer { scaleX = if (pressed) .95f else 1f; scaleY = if (pressed) .95f else 1f }
}

fun Modifier.pressClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressEffect(source).clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

fun Modifier.pressToggle(value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    pressEffect(source).toggleable(value, interactionSource = source, indication = null,
        enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
}

@Composable
fun CheckRow(checked: Boolean, label: String, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.sdp).pressToggle(checked, enabled, onChange),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(58.sdp), Alignment.Center) { Checkbox(checked, null, enabled = enabled) }
        Label(label, 18, modifier = Modifier.weight(1f))
    }
}

@Composable
fun AppShell(page: String, onPage: (String) -> Unit, content: @Composable () -> Unit) {
    val portrait = LocalPortrait.current
    val railWidth = 98.sdp
    val tabHeight = 78.sdp
    // 배치만 바꿔 화면 내용과 입력 상태가 같은 자리에 남게 한다.
    Layout(content = {
        Navigation(page, onPage, portrait)
        Box(Modifier.padding(horizontal = (if (portrait) 24 else 36).sdp,
            vertical = (if (portrait) 20 else 30).sdp)) { content() }
    }) { children, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val navWidth = if (portrait) width else railWidth.roundToPx().coerceAtMost(width)
        val navHeight = if (portrait) tabHeight.roundToPx().coerceAtMost(height) else height
        val nav = children[0].measure(Constraints.fixed(navWidth, navHeight))
        val body = children[1].measure(Constraints.fixed(if (portrait) width else width - navWidth,
            if (portrait) height - navHeight else height))
        layout(width, height) {
            nav.placeRelative(0, if (portrait) height - navHeight else 0)
            body.placeRelative(if (portrait) 0 else navWidth, 0)
        }
    }
}

@Composable
private fun Navigation(page: String, onPage: (String) -> Unit, portrait: Boolean) {
    val entries = listOf("list" to "기록", "report" to "리포트", "settings" to "설정")
    @Composable fun item(key: String, title: String, modifier: Modifier) {
        val selected = page == key || (key == "list" && page == "detail")
        val color = if (selected) AppColors.onPrimary else AppColors.textSecondary
        Column(modifier.sizeIn(minWidth = 58.sdp, minHeight = 58.sdp)
            .background(if (selected) AppColors.primary else Color.Transparent, RoundedCornerShape(16.sdp))
            .pressClick { onPage(key) }, horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            LineIcon(key, Modifier.size(26.sdp), color)
            Label(title, 18, color, bold = selected)
        }
    }
    if (portrait) Row(Modifier.fillMaxSize().background(AppColors.rail).padding(10.sdp),
        horizontalArrangement = Arrangement.spacedBy(10.sdp)) {
        entries.forEach { (key, title) -> item(key, title, Modifier.weight(1f).fillMaxHeight()) }
    } else Column(Modifier.fillMaxSize().background(AppColors.rail).padding(vertical = 24.sdp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.sdp)) {
        Box(Modifier.size(48.sdp).background(AppColors.primary, RoundedCornerShape(14.sdp)), Alignment.Center) {
            LineIcon("mic", Modifier.size(28.sdp), AppColors.onPrimary)
        }
        entries.forEach { (key, title) -> item(key, title, Modifier.size(80.sdp, 68.sdp)) }
    }
}

@Composable
fun LineIcon(kind: String, modifier: Modifier = Modifier, color: Color = AppColors.primaryText) {
    Canvas(modifier) {
        val u = size.width / 48f
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(color, Offset(x * u, y * u), Offset(xx * u, yy * u), 3.2f * u,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        when (kind) {
            "mic" -> {
                drawRoundRect(color, Offset(18 * u, 7 * u), Size(12 * u, 21 * u), CornerRadius(6 * u), style = Stroke(3.4f * u))
                val path = Path().apply { moveTo(11 * u, 23 * u); cubicTo(11 * u, 40 * u, 37 * u, 40 * u, 37 * u, 23 * u) }
                drawPath(path, color, style = Stroke(3.4f * u)); line(24f, 36f, 24f, 42f)
            }
            "list" -> listOf(13f, 24f, 35f).forEach { y -> line(18f, y, 40f, y); drawCircle(color, 1.6f * u, Offset(9 * u, y * u)) }
            "report" -> {
                val path = Path().apply { moveTo(12 * u, 6 * u); lineTo(29 * u, 6 * u); lineTo(37 * u, 14 * u); lineTo(37 * u, 42 * u); lineTo(12 * u, 42 * u); close() }
                drawPath(path, color, style = Stroke(3.2f * u)); line(19f, 23f, 30f, 23f); line(19f, 31f, 30f, 31f)
            }
            else -> {
                drawCircle(color, 6.5f * u, center, style = Stroke(3.2f * u))
                listOf(0f, 45f, 90f, 135f).forEach { degrees ->
                    val radians = Math.toRadians(degrees.toDouble())
                    val dx = kotlin.math.cos(radians).toFloat(); val dy = kotlin.math.sin(radians).toFloat()
                    listOf(-1, 1).forEach { direction ->
                        drawLine(color, center + Offset(dx * 13 * u * direction, dy * 13 * u * direction),
                            center + Offset(dx * 19 * u * direction, dy * 19 * u * direction), 3.2f * u,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
            }
        }
    }
}

private fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
private fun format(ms: Long, pattern: String) = date(ms).format(DateTimeFormatter.ofPattern(pattern, Locale.KOREAN))
fun dayText(ms: Long) = format(ms, "M월 d일")
fun timeText(ms: Long) = format(ms, "a h:mm")
fun dateTimeText(ms: Long) = format(ms, "M월 d일 a h:mm")
fun secondsText(ms: Long) = format(ms, "a h:mm:ss")
