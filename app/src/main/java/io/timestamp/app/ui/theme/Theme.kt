package io.timestamp.app.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.timestamp.app.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

data class AppPalette(val dark: Boolean) {
    // 야간 배경은 Pleos 디자인 토큰(background_secondary #131417, background_primary #1D1E21, popup #313236)을 따른다
    val background = if (dark) Color(0xFF131417) else Color(0xFFF7F8FA)
    val rail = if (dark) Color(0xFF1D1E21) else Color(0xFFEDEEF2)
    val surface = if (dark) Color(0xFF1D1E21) else Color.White
    val surfaceRaised = if (dark) Color(0xFF313236) else Color(0xFFEDEEF2)
    val popup = if (dark) Color(0xFF313236) else Color.White
    val selected get() = selectedFill
    val border = (if (dark) Color.White else Color(0xFF131417)).copy(alpha = .08f)
    val borderStrong = (if (dark) Color.White else Color(0xFF131417)).copy(alpha = .42f)
    val textPrimary = if (dark) Color.White else Color(0xFF131417)
    val buttonFill = textPrimary.copy(alpha = if (dark) .12f else .06f)
    val textSecondary = textPrimary.copy(alpha = if (dark) .84f else .72f)
    // 흐린 글자도 명암 대비 4.5:1 이상 (Pleos UI Design Guide)
    val textTertiary = textPrimary.copy(alpha = .64f)
    val textOnCard = if (dark) Color.White else Color(0xFF131417)
    // 버튼과 탭은 Pleos 기본 컴포넌트처럼 무채색. 강조 버튼은 Basic button Secondary(진한 회색)
    val primary = if (dark) Color(0xFFE0E1E6) else Color(0xFF44464E) // button_filled_enabled = basic_600
    val onPrimary = if (dark) Color(0xFF131417) else Color.White
    // 선택된 탭과 메뉴: Tabs의 Selected indicator(밝은 표시)
    val selectedFill = if (dark) Color(0xFF44464E) else Color.White
    val onSelected = textPrimary
    // 강조색은 42dot 테마의 Informative active 초록. 라임은 앱 아이콘에만 쓴다
    val finding = if (dark) Color(0xFF00E074) else Color(0xFF02C265) // informative_active
    val onFinding = Color(0xFF131417)
    val findingText = if (dark) Color(0xFF00E074) else Color(0xFF007D40)
    val primaryText = findingText
    val findingTint = if (dark) Color(0xFF17271E) else Color(0xFFE8F7EE)
    val resolved = if (dark) Color(0xFF00E074) else Color(0xFF007D40)
    val resolvedText = if (dark) Color(0xFF00E074) else Color(0xFF007D40)
    val dangerText = if (dark) Color(0xFFFF7559) else Color(0xFFBD2609)
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
    const val button = 18
    const val chip = 10
}

/** Pleos Connect 시스템 UI 글꼴 (Asta Sans, SIL OFL 1.1, docs/ASTA_SANS_OFL.txt) */
val AstaSans = FontFamily(
    Font(R.font.asta_sans_regular, FontWeight.Normal),
    Font(R.font.asta_sans_medium, FontWeight.Medium),
    Font(R.font.asta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.asta_sans_bold, FontWeight.Bold),
)

@Composable
fun TimestampTheme(content: @Composable () -> Unit) {
    val palette = AppPalette(androidx.compose.foundation.isSystemInDarkTheme())
    androidx.compose.runtime.CompositionLocalProvider(LocalAppColors provides palette) {
    val scheme = if (palette.dark) darkColorScheme() else androidx.compose.material3.lightColorScheme()
    val base = Typography()
    MaterialTheme(colorScheme = scheme.copy(
        primary = AppColors.primary, onPrimary = AppColors.onPrimary,
        background = AppColors.background, surface = AppColors.surface,
        onBackground = AppColors.textPrimary, onSurface = AppColors.textPrimary,
        secondary = AppColors.finding, error = AppColors.danger,
    ), typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = AstaSans), displayMedium = base.displayMedium.copy(fontFamily = AstaSans), displaySmall = base.displaySmall.copy(fontFamily = AstaSans),
        headlineLarge = base.headlineLarge.copy(fontFamily = AstaSans), headlineMedium = base.headlineMedium.copy(fontFamily = AstaSans), headlineSmall = base.headlineSmall.copy(fontFamily = AstaSans),
        titleLarge = base.titleLarge.copy(fontFamily = AstaSans), titleMedium = base.titleMedium.copy(fontFamily = AstaSans), titleSmall = base.titleSmall.copy(fontFamily = AstaSans),
        bodyLarge = base.bodyLarge.copy(fontFamily = AstaSans), bodyMedium = base.bodyMedium.copy(fontFamily = AstaSans), bodySmall = base.bodySmall.copy(fontFamily = AstaSans),
        labelLarge = base.labelLarge.copy(fontFamily = AstaSans), labelMedium = base.labelMedium.copy(fontFamily = AstaSans), labelSmall = base.labelSmall.copy(fontFamily = AstaSans),
    )) {
        ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = AstaSans), content)
    }
    }
}
