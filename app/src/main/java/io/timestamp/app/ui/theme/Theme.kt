package io.timestamp.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AppColors {
    val background = Color(0xFF0A0E24)
    val rail = Color(0xFF0F1430)
    val surface = Color(0xFF151A38)
    val surfaceRaised = Color(0xFF0F1430)
    val selected get() = primary
    val border = Color.White.copy(alpha = .08f)
    val borderStrong = Color.White.copy(alpha = .42f)
    val textPrimary = Color(0xFFFFFFFF)
    val textSecondary = Color(0xFFA6AED2)
    val textTertiary = Color(0xFFA6AED2)
    val textOnCard = Color(0xFFFFFFFF)
    val primary = Color(0xFFC6FF4D)
    val onPrimary = Color(0xFF10160A)
    val primaryText = primary
    val finding = primary
    val findingText = primary
    val findingTint = Color(0xFF1C2249)
    val resolved = Color(0xFF43D17A)
    val resolvedText = Color(0xFF43D17A)
    val danger = Color(0xFFFF8A8A)
}

object AppType {
    const val screenTitle = 36
    const val cardTitle = 20
    const val body = 18
    const val secondary = 18
    const val graphAxis = 14
    const val shopSentence = 46
    const val conditionSentence = 42
}

object AppCorners {
    const val card = 21
    const val dialog = 28
    const val button = 999
    const val chip = 10
}

@Composable
fun TimestampTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = AppColors.primary, onPrimary = AppColors.onPrimary,
        background = AppColors.background, surface = AppColors.surface,
        onBackground = AppColors.textPrimary, onSurface = AppColors.textPrimary,
        secondary = AppColors.finding, error = AppColors.danger,
    ), content = content)
}
