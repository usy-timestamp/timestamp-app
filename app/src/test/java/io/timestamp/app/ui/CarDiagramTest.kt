package io.timestamp.app.ui

import io.timestamp.app.analysis.BodyParts
import io.timestamp.app.ui.theme.AppType
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.ceil

class CarDiagramTest {
    @Test fun drawingContainsEveryTouchPartExactlyOnceAndNoExtraButtons() {
        assertEquals(BodyParts.touchParts, CarDiagramLayout.areas.map { it.name })
        assertEquals(10, CarDiagramLayout.areas.map { it.name }.distinct().size)
        assertTrue(CarDiagramLayout.areas.none { it.name in BodyParts.extraParts })
    }

    @Test fun partCentersStayInsideTheirDrawnCarRegions() {
        val regions = mapOf(
            "왼쪽 앞바퀴" to listOf(11f, 34f, 24f, 70f),
            "오른쪽 앞바퀴" to listOf(96f, 34f, 109f, 70f),
            "왼쪽 뒷바퀴" to listOf(11f, 150f, 24f, 186f),
            "오른쪽 뒷바퀴" to listOf(96f, 150f, 109f, 186f),
            "엔진룸" to listOf(32f, 18f, 88f, 58f),
            "대시보드" to listOf(36f, 92f, 84f, 107f),
            "왼쪽 문" to listOf(22f, 102f, 32f, 153f),
            "오른쪽 문" to listOf(88f, 102f, 98f, 153f),
            "실내" to listOf(37f, 114f, 83f, 157f),
            "트렁크" to listOf(35f, 190f, 85f, 203f),
        )
        CarDiagramLayout.areas.forEach { area ->
            val (left, top, right, bottom) = regions.getValue(area.name)
            assertTrue(area.name, area.x in left..right && area.y in top..bottom)
        }
    }

    @Test fun targetWindowsKeepNamesAndTouchAreasInsideDrawingWithoutOverlap() {
        listOf(1682f to 1210f, 840f to 1210f).forEach { (widthPx, heightPx) ->
            listOf(1f, 1.5f, 2f).forEach { density ->
                listOf(1f, 1.3f).forEach { fontScale ->
                    val widthDp = widthPx / density
                    val portrait = widthPx < heightPx
                    val scale = widthDp / if (portrait) 640f else 1280f
                    val padding = 2 * CarDiagramLayout.pickerPadding * scale
                    val drawingWidth = if (portrait) widthDp - 48f * scale - padding
                        else maxOf(CarDiagramLayout.pickerWidth * scale, CarDiagramLayout.minimumPickerWidth.toFloat()) - padding
                    val drawingHeight = maxOf(500f, CarDiagramLayout.pickerHeight * scale)
                    val hit = maxOf(76f, CarDiagramLayout.hitSize * scale)
                    val fontSize = AppType.body * scale * fontScale
                    val fit = CarDiagramLayout.pickerFit(drawingWidth, drawingHeight, fontSize * 3 + 4f)
                    val placements = CarDiagramLayout.placements(fit, drawingWidth, hit, 2f)
                    val description = "$widthPx x $heightPx, density $density, font scale $fontScale"
                    assertEquals(10, placements.size)
                    placements.forEachIndexed { index, placement ->
                        val target = placement.target
                        val name = placement.name
                        val context = "$description ${placement.area.name}"
                        assertTrue(context, target.width >= 76f && target.height >= 76f)
                        listOf(target, name).forEach { bounds ->
                            assertTrue(context, bounds.left >= 0f && bounds.top >= 0f &&
                                bounds.right <= drawingWidth && bounds.bottom <= drawingHeight)
                        }
                        assertTrue(context, name.left >= target.left && name.top >= target.top &&
                            name.right <= target.right && name.bottom <= target.bottom)
                        val textPadding = if (placement.side == 0) 4f * scale else 0f
                        val lines = if (placement.side == 0) {
                            val charactersPerLine = ((name.width - textPadding) / fontSize).toInt()
                            assertTrue(context, charactersPerLine > 0)
                            ceil(placement.area.name.length.toFloat() / charactersPerLine).toInt()
                        } else {
                            assertTrue(context, name.width >= placement.area.name.split(' ').maxOf { it.length } * fontSize)
                            if (placement.area.name.length * fontSize <= name.width) 1 else 2
                        }
                        assertTrue("$context readable name", lines * fontSize * 1.45f + textPadding <= name.height)
                        placements.drop(index + 1).forEach { other ->
                            assertFalse("$context target overlaps ${other.area.name}", overlaps(target, other.target))
                            assertFalse("$context name overlaps ${other.area.name}", overlaps(name, other.name))
                        }
                    }
                }
            }
        }
    }

    private fun overlaps(a: CarDiagramLayout.Bounds, b: CarDiagramLayout.Bounds) =
        a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    @Test fun outsideNamesAndPartCentersBelongToTheSameTarget() {
        val fit = CarDiagramLayout.pickerFit(528f, 500f, 58f)
        val placements = CarDiagramLayout.placements(fit, 528f, 76f, 2f)
        assertEquals(BodyParts.touchParts, placements.map { it.area.name })
        placements.forEach { placement ->
            val x = fit.originX + fit.unit * placement.area.x
            val y = fit.originY + fit.unit * placement.area.y
            assertTrue(placement.area.name, x in placement.target.left..placement.target.right &&
                y in placement.target.top..placement.target.bottom)
            when (placement.side) {
                -1 -> assertTrue(placement.name.right < fit.originX)
                1 -> assertTrue(placement.name.left > fit.originX + fit.unit * CarDiagramLayout.width)
                else -> assertEquals(placement.target, placement.name)
            }
        }
        assertEquals(6, placements.count { it.side != 0 })
        assertEquals(4, placements.count { it.side == 0 })
    }

    @Test fun remindersRecognizeEveryNewPartWithoutMatchingOtherParts() {
        val added = listOf("대시보드", "왼쪽 문", "오른쪽 문", "트렁크", "지붕")
        val saved = added.joinToString("|")
        added.forEach { assertTrue(it, reminderEnabled(saved, it)) }
        assertFalse(reminderEnabled(saved, "실내"))
        assertFalse(reminderEnabled(saved, "하부"))
        assertTrue(reminderEnabled("앞 왼쪽 바퀴|대시보드", "왼쪽 앞바퀴"))
    }
}
