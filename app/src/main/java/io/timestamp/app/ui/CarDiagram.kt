package io.timestamp.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.timestamp.app.analysis.BodyParts
import io.timestamp.app.ui.theme.*

internal data class PartArea(val name: String, val x: Float, val y: Float)

internal object CarDiagramLayout {
    const val width = 120f
    const val height = 224f
    const val pickerHeight = 500
    const val pickerWidth = 560
    const val minimumPickerWidth = 500
    const val pickerPadding = 16
    const val hitSize = 76
    val areas = listOf(
        PartArea("왼쪽 앞바퀴", 17.5f, 52f), PartArea("오른쪽 앞바퀴", 102.5f, 52f),
        PartArea("왼쪽 뒷바퀴", 17.5f, 168f), PartArea("오른쪽 뒷바퀴", 102.5f, 168f),
        PartArea("엔진룸", 60f, 42f), PartArea("대시보드", 60f, 99f),
        PartArea("왼쪽 문", 25f, 127f), PartArea("오른쪽 문", 95f, 127f),
        PartArea("실내", 60f, 137f), PartArea("트렁크", 60f, 196f),
    )

    data class Fit(val unit: Float, val originX: Float, val originY: Float)
    data class Bounds(val left: Float, val top: Float, val width: Float, val height: Float) {
        val right get() = left + width
        val bottom get() = top + height
    }
    data class Placement(val area: PartArea, val target: Bounds, val name: Bounds, val side: Int)

    fun fit(availableWidth: Float, availableHeight: Float): Fit {
        val unit = minOf(availableWidth / width, availableHeight / height)
        return Fit(unit, (availableWidth - unit * width) / 2, (availableHeight - unit * height) / 2)
    }

    fun pickerFit(availableWidth: Float, availableHeight: Float, minimumNameWidth: Float): Fit {
        val fit = fit(availableWidth - 2 * minimumNameWidth, availableHeight)
        return fit.copy(originX = fit.originX + minimumNameWidth)
    }

    fun placements(fit: Fit, availableWidth: Float, hit: Float, nameGap: Float): List<Placement> = areas.map { area ->
        val x = fit.originX + fit.unit * area.x
        val y = fit.originY + fit.unit * area.y
        val top = y - hit / 2
        val side = when {
            area.x < width / 3 -> -1
            area.x > width * 2 / 3 -> 1
            else -> 0
        }
        val target = when (side) {
            -1 -> Bounds(0f, top, x + hit / 2, hit)
            1 -> Bounds(x - hit / 2, top, availableWidth - x + hit / 2, hit)
            else -> Bounds(x - hit / 2, top, hit, hit)
        }
        val name = when (side) {
            -1 -> Bounds(0f, top, fit.originX - nameGap, hit)
            1 -> {
                val left = fit.originX + fit.unit * width + nameGap
                Bounds(left, top, availableWidth - left, hit)
            }
            else -> target
        }
        Placement(area, target, name, side)
    }
}

@Composable
fun CarDiagram(selected: Set<String>, modifier: Modifier = Modifier, onToggle: ((String) -> Unit)? = null, compact: Boolean = false) {
    val parts = selected.map(BodyParts::displayName).toSet()
    val interaction = remember { MutableInteractionSource() }
    val selectable = onToggle != null && !compact
    val nameSize = AppType.body.ssp
    val nameGap = 2.dp
    val minimumNameWidth = with(LocalDensity.current) { nameSize.toDp() * 3 + nameGap * 2 }
    BoxWithConstraints(modifier) {
        val fit = if (selectable) CarDiagramLayout.pickerFit(maxWidth.value, maxHeight.value, minimumNameWidth.value)
            else CarDiagramLayout.fit(maxWidth.value, maxHeight.value)
        val unit = fit.unit.dp
        val originX = fit.originX.dp
        val originY = fit.originY.dp
        val hit = maxOf(CarDiagramLayout.hitSize.dp, CarDiagramLayout.hitSize.sdp)
        val placements = if (selectable) CarDiagramLayout.placements(fit, maxWidth.value, hit.value, nameGap.value) else emptyList()
        Canvas(Modifier.fillMaxSize().pressEffect(interaction).semantics {
            contentDescription = "앞이 위인 차 그림, 선택 부위 "+parts.joinToString().ifEmpty { "없음" }
        }) {
            val drawingX = originX.toPx()
            val drawingY = originY.toPx()
            val drawingUnit = unit.toPx()
            withTransform({ translate(drawingX, drawingY); scale(drawingUnit, drawingUnit, Offset.Zero) }) {
                fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, stroke: Float = 0f) {
                    if (stroke == 0f) drawRoundRect(color, Offset(x,y), Size(w,h), CornerRadius(r))
                    else drawRoundRect(color, Offset(x,y), Size(w,h), CornerRadius(r), style = Stroke(stroke))
                }
                fun outline(chosen: Boolean) = if (chosen || selectable) AppColors.finding.copy(alpha = if (chosen) 1f else .65f)
                    else AppColors.textPrimary.copy(alpha = .3f)
                fun partStroke(chosen: Boolean, width: Float) = Stroke(width,
                    pathEffect = if (selectable && !chosen) PathEffect.dashPathEffect(floatArrayOf(3f, 2f)) else null)
                CarDiagramLayout.areas.filter { it.name.contains("바퀴") }.forEach { area ->
                    val chosen = area.name in parts
                    rect(area.x - 6.5f, area.y - 18, 13f, 36f, 5f, if (chosen) AppColors.finding else AppColors.textSecondary.copy(alpha = .28f))
                }
                val body = Path().apply {
                    moveTo(60f,8f); cubicTo(38f,8f,25f,16f,23f,36f); lineTo(21f,84f); lineTo(21f,172f)
                    cubicTo(21f,198f,32f,214f,60f,214f); cubicTo(88f,214f,99f,198f,99f,172f)
                    lineTo(99f,84f); lineTo(97f,36f); cubicTo(95f,16f,82f,8f,60f,8f); close()
                }
                drawPath(body, AppColors.background)
                drawPath(body, if ("하부" in parts) AppColors.finding else AppColors.textPrimary.copy(alpha = .7f), style = Stroke(2f))
                run {
                    val hood = Path().apply {
                        moveTo(30f,62f); lineTo(32f,35f); quadraticTo(34f,18f,60f,18f)
                        quadraticTo(86f,18f,88f,35f); lineTo(90f,62f); quadraticTo(60f,52f,30f,62f); close()
                    }
                    val chosen = "엔진룸" in parts
                    drawPath(hood, if (chosen) AppColors.finding else AppColors.textPrimary.copy(alpha = .04f))
                    drawPath(hood, outline(chosen), style = partStroke(chosen, 1.5f))
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
                val dashboard = "대시보드" in parts
                rect(36f,92f,48f,15f,4f, if (dashboard) AppColors.finding else AppColors.textPrimary.copy(alpha = .07f))
                drawRoundRect(outline(dashboard), Offset(36f,92f), Size(48f,15f), CornerRadius(4f), style = partStroke(dashboard, 1.2f))
                val cabin = "실내" in parts
                rect(37f,114f,46f,43f,7f, if (cabin) AppColors.finding else AppColors.textPrimary.copy(alpha = .07f))
                drawRoundRect(outline(cabin), Offset(37f,114f), Size(46f,43f), CornerRadius(7f), style = partStroke(cabin, 1.2f))
                listOf(41f,63f).forEach { x ->
                    rect(x,119f,16f,22f,4f,AppColors.textPrimary.copy(alpha = .3f),1f)
                }
                rect(41f,147f,38f,6f,2f,AppColors.textPrimary.copy(alpha = .3f),1f)
                listOf("왼쪽 문" to 22f, "오른쪽 문" to 88f).forEach { (name, x) ->
                    val chosen = name in parts
                    rect(x,102f,10f,51f,4f, if (chosen) AppColors.finding else AppColors.textPrimary.copy(alpha = .07f))
                    drawRoundRect(outline(chosen), Offset(x,102f), Size(10f,51f), CornerRadius(4f), style = partStroke(chosen, 1.2f))
                    rect(x + 4f,114f,2f,10f,1f,AppColors.textPrimary.copy(alpha = .45f))
                }
                val trunk = Path().apply {
                    moveTo(32f,186f); quadraticTo(60f,193f,88f,186f); lineTo(85f,198f)
                    quadraticTo(60f,209f,35f,198f); close()
                }
                val trunkChosen = "트렁크" in parts
                drawPath(trunk, if (trunkChosen) AppColors.finding else AppColors.textPrimary.copy(alpha = .07f))
                drawPath(trunk, outline(trunkChosen), style = partStroke(trunkChosen, 1.2f))
                if ("지붕" in parts) rect(33f,90f,54f,70f,8f,AppColors.finding,2f)
                listOf(40f,80f).forEach { x ->
                    val hoodLine = Path().apply { moveTo(x,20f); quadraticTo(if (x == 40f) 42f else 78f,44f,if (x == 40f) 36f else 84f,62f) }
                    drawPath(hoodLine, AppColors.textPrimary.copy(alpha = .3f), style = Stroke(1.2f))
                }
                listOf(30f,76f).forEach { x ->
                    rect(x,13f,14f,4f,2f,AppColors.textPrimary.copy(alpha = .75f))
                    rect(x,206f,14f,3.5f,1.75f,Color(0xFFFF6B5E))
                }
                listOf(14f,97f).forEach { x -> rect(x,78f,9f,5f,2.5f,AppColors.textPrimary.copy(alpha = .55f)) }
                if (selectable) CarDiagramLayout.areas.filter { it.name.contains("바퀴") }.forEach { area ->
                    val chosen = area.name in parts
                    drawRoundRect(outline(chosen), Offset(area.x - 6.5f, area.y - 18f), Size(13f,36f),
                        CornerRadius(5f), style = partStroke(chosen, 1.4f))
                }
            }
            placements.filter { it.side != 0 }.forEach { placement ->
                val area = placement.area
                val y = (fit.originY + fit.unit * area.y).dp.toPx()
                val nameEdge = if (placement.side < 0) placement.name.right else placement.name.left
                val partEdge = fit.originX + fit.unit * (area.x + placement.side * 7f)
                drawLine(AppColors.borderStrong, Offset(nameEdge.dp.toPx(), y), Offset(partEdge.dp.toPx(), y), 1.dp.toPx())
            }
        }
        placements.forEach { placement ->
            val area = placement.area
            val chosen = area.name in parts
            val target = placement.target
            val name = placement.name
            Box(Modifier.offset(target.left.dp, target.top.dp).size(target.width.dp, target.height.dp)
                .toggleable(chosen, interactionSource = interaction, indication = null,
                    role = Role.Checkbox) { onToggle?.invoke(area.name) }
                .semantics { contentDescription = area.name }) {
                Box(Modifier.offset((name.left - target.left).dp, (name.top - target.top).dp)
                    .size(name.width.dp, name.height.dp), contentAlignment = when (placement.side) {
                        -1 -> Alignment.CenterEnd
                        1 -> Alignment.CenterStart
                        else -> Alignment.Center
                    }) {
                    Text(area.name, Modifier.clearAndSetSemantics {}.then(if (placement.side == 0)
                        Modifier.background(if (chosen) AppColors.finding else AppColors.background, RoundedCornerShape(4.sdp))
                            .padding(2.sdp) else Modifier),
                        color = if (chosen && placement.side == 0) AppColors.onPrimary
                            else if (chosen) AppColors.findingText else AppColors.textPrimary,
                        fontSize = nameSize, lineHeight = nameSize * 1.45f,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                        textAlign = when (placement.side) { -1 -> TextAlign.End; 1 -> TextAlign.Start; else -> TextAlign.Center })
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun PartPicker(selected: Set<String>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier, padding = CarDiagramLayout.pickerPadding) {
        Label("소리가 난 곳을 눌러 주세요", AppType.cardTitle, bold = true)
        CarDiagram(selected, Modifier.fillMaxWidth()
            .height(maxOf(CarDiagramLayout.pickerHeight.dp, CarDiagramLayout.pickerHeight.sdp)), onToggle)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.sdp),
            verticalArrangement = Arrangement.spacedBy(12.sdp)) {
            BodyParts.extraParts.forEach { part ->
                val chosen = part in selected
                val shape = RoundedCornerShape(999.sdp)
                val hit = maxOf(CarDiagramLayout.hitSize.dp, CarDiagramLayout.hitSize.sdp)
                Box(Modifier.sizeIn(minWidth = hit, minHeight = hit)
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
