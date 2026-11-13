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
        CompositionLocalProvider(LocalScale provides (maxWidth.value / if (portrait) 640f else 1280f),
            LocalPortrait provides portrait) { content() }
    }
}

val Int.sdp @Composable get() = (this * LocalScale.current).dp
val Int.ssp @Composable get() = (this * LocalScale.current).sp
