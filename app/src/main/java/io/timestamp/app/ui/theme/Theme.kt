package io.timestamp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class AppPalette(val dark: Boolean) {
    val background = if (dark) Color(0xFF0A0E24) else Color(0xFFF7F8FA)
    val rail = if (dark) Color(0xFF0F1430) else Color(0xFFEDEEF2)
    val surface = if (dark) Color(0xFF151A38) else Color.White
    val surfaceRaised = if (dark) Color(0xFF313236) else Color(0xFFEDEEF2)
    val selected get() = primary
    val border = (if (dark) Color.White else Color(0xFF131417)).copy(alpha = .08f)
    val borderStrong = (if (dark) Color.White else Color(0xFF131417)).copy(alpha = .42f)
    val textPrimary = if (dark) Color.White else Color(0xFF131417)
    val textSecondary = textPrimary.copy(alpha = if (dark) .84f else .72f)
    val textTertiary = textPrimary.copy(alpha = if (dark) .64f else .56f)
    val textOnCard = if (dark) Color.White else Color(0xFF131417)
    val primary = Color(0xFFC6FF4D)
    val onPrimary = Color(0xFF10160A)
    val primaryText = if (dark) primary else Color(0xFF385800)
    val finding = primary
    val findingText = primaryText
    val findingTint = if (dark) Color(0xFF1C2249) else Color(0xFFF0F4E8)
    val resolved = if (dark) Color(0xFF00E074) else Color(0xFF007D40)
    val resolvedText = if (dark) Color(0xFF00E074) else Color(0xFF007D40)
    val danger = if (dark) Color(0xFFFF4C28) else Color(0xFFBD2609)
}

val LocalAppColors = androidx.compose.runtime.staticCompositionLocalOf { AppPalette(true) }
val AppColors: AppPalette @Composable get() = LocalAppColors.current

object AppType {
    const val screenTitle = 36
    const val cardTitle = 28
    const val body = 26
    const val secondary = 24
    const val caption = 20
    const val graphAxis = 20
    const val shopSentence = 48
    const val conditionSentence = 40
}

object AppCorners {
    const val card = 21
    const val dialog = 28
    const val button = 999
    const val chip = 10
}

@Composable
fun TimestampTheme(content: @Composable () -> Unit) {
    val palette = AppPalette(androidx.compose.foundation.isSystemInDarkTheme())
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides palette) {
    val scheme = if (palette.dark) darkColorScheme() else androidx.compose.material3.lightColorScheme()
    MaterialTheme(colorScheme = scheme.copy(
        primary = AppColors.primary, onPrimary = AppColors.onPrimary,
        background = AppColors.background, surface = AppColors.surface,
        onBackground = AppColors.textPrimary, onSurface = AppColors.textPrimary,
        secondary = AppColors.finding, error = AppColors.danger,
    ), content = content)
    }
}
