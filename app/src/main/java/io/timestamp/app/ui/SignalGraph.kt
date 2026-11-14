package io.timestamp.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.timestamp.app.data.SampleEntity
import io.timestamp.app.ui.theme.*
import kotlin.math.max

@Composable
fun SignalGraph(samples: List<SampleEntity>, modifier: Modifier = Modifier) {
    val axisTextSize = AppType.graphAxis.ssp
    val context = androidx.compose.ui.platform.LocalContext.current
    val axisTypeface = androidx.compose.runtime.remember(context) {
        runCatching { androidx.core.content.res.ResourcesCompat.getFont(context, io.timestamp.app.R.font.asta_sans_regular) }.getOrNull()
    }
    Panel(modifier, padding = 22) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label("말한 순간 앞뒤 30초", AppType.cardTitle, bold = true)
            Label("속도   브레이크", AppType.secondary, AppColors.textSecondary)
        }
        // 가로 대 세로 비율을 고정한다. 높이가 정해지지 않으면 그래프가 납작해져 축이 뒤집혀 보인다
        val colors = AppColors
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f).padding(top = 16.sdp)
            .semantics { contentDescription = "말하기 25초 전부터 5초 후까지의 속도와 브레이크 그래프. 초록 띠는 브레이크를 밟은 구간" }) {
            val left = 42.sdpValue(size.width)
            val right = size.width - 18.sdpValue(size.width)
            val top = 40.sdpValue(size.width)
            val bottom = size.height - 56.sdpValue(size.width)
            val unit = (right - left) / 30f
            fun x(offset: Int) = left + (offset + 25) * unit
            val valid = samples.mapNotNull { it.speedKmh?.takeIf(Float::isFinite) }
            val ceiling = max(60f, valid.maxOrNull() ?: 60f)
            fun y(speed: Float) = bottom - (speed.coerceIn(0f, ceiling) / ceiling) * (bottom - top)
            fun text(value: String, xx: Float, yy: Float, primary: Boolean = false, end: Boolean = false, center: Boolean = false, finding: Boolean = false) {
                drawIntoCanvas { canvas ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = (if (finding) colors.findingText else if (primary) colors.primaryText else colors.textTertiary).toArgb()
                        textSize = axisTextSize.toPx()
                        if (axisTypeface != null) typeface = axisTypeface
                        textAlign = if (end) Paint.Align.RIGHT else if (center) Paint.Align.CENTER else Paint.Align.LEFT
                    }
                    canvas.nativeCanvas.drawText(value, xx, yy, paint)
                }
            }
            drawRect(colors.finding.copy(alpha = .08f), Offset(x(-5), top - 12), Size(x(0) - x(-5), bottom - top + 12))
            text("확인 구간", (x(-5) + x(0)) / 2, top - 18, center = true, finding = true)
            listOf(0f, 30f, 60f).forEach { speed ->
                drawLine(colors.borderStrong, Offset(left, y(speed)), Offset(right, y(speed)), 1f)
                text(speed.toInt().toString(), left - 8, y(speed) + 4, end = true)
            }
            val byOffset = samples.associateBy { it.offsetSec }
            (-25..5).forEach { offset ->
                if (byOffset[offset]?.brakePressed == true) {
                    val start = max(left, x(offset) - unit / 2)
                    val end = minOf(right, x(offset) + unit / 2)
                    drawRect(colors.findingText, Offset(start, bottom - (bottom - top) * .25f),
                        Size(end - start, (bottom - top) * .25f))
                }
                val speed = byOffset[offset]?.speedKmh?.takeIf(Float::isFinite)
                val previous = byOffset[offset - 1]?.speedKmh?.takeIf(Float::isFinite)
                if (speed != null) {
                    drawCircle(colors.textPrimary, 1.5f, Offset(x(offset), y(speed)))
                    if (previous != null) drawLine(colors.textPrimary, Offset(x(offset - 1), y(previous)), Offset(x(offset), y(speed)), 3f * size.width / 604)
                }
            }
            drawLine(colors.primaryText, Offset(x(0), top - 12), Offset(x(0), bottom + 7), 2f * size.width / 604,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 7f)))
            byOffset[0]?.speedKmh?.takeIf(Float::isFinite)?.let { drawCircle(colors.primaryText, 6f * size.width / 604, Offset(x(0), y(it))) }
            listOf(-25 to "25초 전", -15 to "15초 전", -5 to "5초 전", 5 to "5초 후").forEach { (offset, label) ->
                text(label, x(offset), bottom + 28.sdpValue(size.width), end = offset == 5, center = offset in -15..0)
            }
            // 좁은 창에서도 옆 눈금과 겹치지 않게 말한 순간은 한 줄 아래에 둔다.
            text("말한 순간", x(0), size.height - 3, primary = true, center = true)
            text("세로축: 시속 km", left, size.height - 3)
            if (valid.isEmpty()) text("속도 수집 불가", left + 30, top + 30)
        }
    }
}

private fun Int.sdpValue(width: Float) = this * width / 604f
