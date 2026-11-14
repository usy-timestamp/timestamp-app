package io.timestamp.app.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalPortrait = staticCompositionLocalOf { false }
private val LocalScale = staticCompositionLocalOf { 1f }

@Composable
fun WindowScale(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val portrait = maxWidth < maxHeight
        CompositionLocalProvider(LocalScale provides (if (portrait) minOf(maxWidth.value / 640f, maxHeight.value / 1100f) else minOf(maxWidth.value / 1274f, maxHeight.value / 855f)),
            LocalPortrait provides portrait) { content() }
    }
}

val Int.sdp @Composable get() = (this * LocalScale.current).dp
val Int.ssp @Composable get() = (this * LocalScale.current).sp
