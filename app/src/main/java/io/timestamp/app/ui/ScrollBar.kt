package io.timestamp.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.timestamp.app.ui.theme.AppColors
import io.timestamp.app.ui.theme.sdp

fun Modifier.scrollWithBar(state: ScrollState? = null): Modifier = composed {
    val scroll = state ?: rememberScrollState()
    val width = 4.sdp
    val inset = 4.sdp
    val minimum = 48.sdp
    val colors = AppColors
    val color = colors.textPrimary.copy(alpha = if (colors.dark) .24f else .20f)
    drawWithContent {
        drawContent()
        val maximum = scroll.maxValue
        if (maximum > 0 && maximum != Int.MAX_VALUE && size.height > 0f) {
            val length = (size.height * size.height / (size.height + maximum))
                .coerceIn(minimum.toPx().coerceAtMost(size.height), size.height)
            val top = (size.height - length) * (scroll.value.toFloat() / maximum).coerceIn(0f, 1f)
            drawRoundRect(color, Offset(size.width - inset.toPx() - width.toPx(), top),
                Size(width.toPx(), length), CornerRadius(width.toPx() / 2))
        }
    }.verticalScroll(scroll)
}

fun Modifier.lazyScrollBar(state: LazyListState): Modifier = composed {
    val width = 4.sdp
    val inset = 4.sdp
    val minimum = 48.sdp
    val colors = AppColors
    val color = colors.textPrimary.copy(alpha = if (colors.dark) .24f else .20f)
    drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if ((state.canScrollForward || state.canScrollBackward) && items.isNotEmpty() &&
            info.totalItemsCount > 0 && size.height > 0f) {
            val visible = items.sumOf { item ->
                val start = maxOf(item.offset, info.viewportStartOffset)
                val end = minOf(item.offset + item.size, info.viewportEndOffset)
                ((end - start).coerceAtLeast(0).toFloat() / item.size.coerceAtLeast(1)).toDouble()
            }.toFloat()
            val firstSize = items.firstOrNull { it.index == state.firstVisibleItemIndex }?.size ?: 1
            val position = state.firstVisibleItemIndex +
                state.firstVisibleItemScrollOffset.toFloat() / (firstSize + info.mainAxisItemSpacing).coerceAtLeast(1)
            val length = (size.height * visible / info.totalItemsCount)
                .coerceIn(minimum.toPx().coerceAtMost(size.height), size.height)
            val progress = if (!state.canScrollForward) 1f else
                (position / (info.totalItemsCount - visible).coerceAtLeast(1f)).coerceIn(0f, 1f)
            drawRoundRect(color, Offset(size.width - inset.toPx() - width.toPx(), (size.height - length) * progress),
                Size(width.toPx(), length), CornerRadius(width.toPx() / 2))
        }
    }
}
