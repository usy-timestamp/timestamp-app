package io.timestamp.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import io.timestamp.app.analysis.BodyParts
import io.timestamp.app.ui.theme.*

private data class PartArea(val name: String, val x: Float, val y: Float)
private val areas = listOf(
    PartArea("엔진룸", 60f, 42f), PartArea("실내", 60f, 125f),
    PartArea("왼쪽 앞바퀴", 17.5f, 52f), PartArea("오른쪽 앞바퀴", 102.5f, 52f),
    PartArea("왼쪽 뒷바퀴", 17.5f, 168f), PartArea("오른쪽 뒷바퀴", 102.5f, 168f),
)

@Composable
fun CarDiagram(selected: Set<String>, modifier: Modifier = Modifier, onToggle: ((String) -> Unit)? = null, compact: Boolean = false) {
    val parts = selected.map(BodyParts::displayName).toSet()
    val interaction = remember { MutableInteractionSource() }
    BoxWithConstraints(modifier) {
        val unit = minOf(maxWidth / 120, maxHeight / 224)
        val originX = (maxWidth - unit * 120) / 2
        val originY = (maxHeight - unit * 224) / 2
        Canvas(Modifier.fillMaxSize().pressEffect(interaction).semantics {
            contentDescription = "앞이 위인 차 그림, 선택 부위 "+parts.joinToString().ifEmpty { "없음" }
        }) {
            val u = minOf(size.width / 120f, size.height / 224f)
            withTransform({ translate((size.width - 120 * u) / 2, (size.height - 224 * u) / 2); scale(u, u, Offset.Zero) }) {
                fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, stroke: Float = 0f) {
                    if (stroke == 0f) drawRoundRect(color, Offset(x,y), Size(w,h), CornerRadius(r))
                    else drawRoundRect(color, Offset(x,y), Size(w,h), CornerRadius(r), style = Stroke(stroke))
                }
                areas.filter { it.name.contains("바퀴") }.forEach { area ->
                    val chosen = area.name in parts
                    rect(area.x - 6.5f, area.y - 18, 13f, 36f, 5f, if (chosen) AppColors.finding else AppColors.textSecondary.copy(alpha = .28f))
                    if (chosen) rect(area.x - 11, area.y - 22.5f, 22f, 45f, 9f, AppColors.finding.copy(alpha = .45f), 1.5f)
                }
                val body = Path().apply {
                    moveTo(60f,8f); cubicTo(38f,8f,25f,16f,23f,36f); lineTo(21f,84f); lineTo(21f,172f)
                    cubicTo(21f,198f,32f,214f,60f,214f); cubicTo(88f,214f,99f,198f,99f,172f)
                    lineTo(99f,84f); lineTo(97f,36f); cubicTo(95f,16f,82f,8f,60f,8f); close()
                }
                drawPath(body, AppColors.background)
                drawPath(body, if ("하부" in parts) AppColors.finding else AppColors.textPrimary.copy(alpha = .7f), style = Stroke(2f))
                if ("엔진룸" in parts) {
                    val hood = Path().apply {
                        moveTo(30f,62f); lineTo(32f,35f); quadraticTo(34f,18f,60f,18f)
                        quadraticTo(86f,18f,88f,35f); lineTo(90f,62f); quadraticTo(60f,52f,30f,62f); close()
                    }
                    drawPath(hood, AppColors.finding)
                    drawPath(hood, AppColors.finding.copy(alpha = .45f), style = Stroke(1.5f))
                }
                val front = Path().apply {
                    moveTo(33f,70f); quadraticTo(60f,58f,87f,70f); lineTo(83f,92f); quadraticTo(60f,84f,37f,92f); close()
                }
                val rear = Path().apply {
                    moveTo(37f,159f); quadraticTo(60f,167f,83f,159f); lineTo(87f,177f); quadraticTo(60f,187f,33f,177f); close()
                }
                listOf(front,rear).forEach { window ->
                    drawPath(window, AppColors.textPrimary.copy(alpha = .16f))
                    drawPath(window, AppColors.textPrimary.copy(alpha = .45f), style = Stroke(1.4f))
                }
                val cabin = "실내" in parts
                rect(37f,97f,46f,56f,8f, if (cabin) AppColors.finding else AppColors.textPrimary.copy(alpha = .07f))
                rect(37f,97f,46f,56f,8f, if (cabin) AppColors.finding.copy(alpha = .45f) else AppColors.textPrimary.copy(alpha = .3f),1.2f)
                listOf(40f,80f).forEach { x ->
                    val hoodLine = Path().apply { moveTo(x,20f); quadraticTo(if (x == 40f) 42f else 78f,44f,if (x == 40f) 36f else 84f,62f) }
                    drawPath(hoodLine, AppColors.textPrimary.copy(alpha = .3f), style = Stroke(1.2f))
                }
                listOf(30f,76f).forEach { x ->
                    rect(x,13f,14f,4f,2f,AppColors.textPrimary.copy(alpha = .75f))
                    rect(x,206f,14f,3.5f,1.75f,Color(0xFFFF6B5E))
                }
                listOf(14f,97f).forEach { x -> rect(x,78f,9f,5f,2.5f,AppColors.textPrimary.copy(alpha = .55f)) }
            }
        }
        if (onToggle != null) areas.forEach { area ->
            // 그림이 작아 보여도 손으로 누를 공간은 넓게 둔다.
            val hit = 58.sdp
            Box(Modifier.offset(originX + unit * area.x - hit / 2, originY + unit * area.y - hit / 2)
                .size(hit).toggleable(area.name in parts, interactionSource = interaction, indication = null,
                    role = Role.Checkbox) { onToggle(area.name) }
                .semantics { contentDescription = area.name })
        }
    }
}

@Composable
fun PartPicker(selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Label("소리가 난 곳을 눌러 주세요", AppType.cardTitle, bold = true)
        CarDiagram(selected, Modifier.fillMaxWidth().height(390.sdp), onToggle)
        Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
            BodyParts.extraParts.forEach { part ->
                val chosen = part in selected
                val shape = RoundedCornerShape(999.sdp)
                Box(Modifier.sizeIn(minWidth = 58.sdp, minHeight = 58.sdp)
                    .background(if (chosen) AppColors.finding else Color.Transparent, shape)
                    .border(BorderStroke(1.sdp, if (chosen) AppColors.finding else AppColors.borderStrong), shape)
                    .pressToggle(chosen) { onToggle(part) }.padding(horizontal = 18.sdp, vertical = 12.sdp),
                    contentAlignment = Alignment.Center) {
                    Label(part, 18, if (chosen) AppColors.onPrimary else AppColors.textPrimary)
                }
            }
        }
        Label(selected.joinToString(", ").ifEmpty { "여러 곳을 고를 수 있어요" }, 18, AppColors.textSecondary)
    }
}
