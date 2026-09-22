package io.github.rudtjr1106.switchboard.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 테마 색 외에 상태 표시(켜짐·꺼짐·만료·차단)에 쓰는 고정 색 */
data class StatusColors(
    val live: Color,
    val off: Color,
    val expired: Color,
    val blocking: Color,
    val warning: Color,
    val codeBackground: Color,
    val phoneBezel: Color,
)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(
        live = Color(0xFF2E9E5B),
        off = Color(0xFF9AA0A6),
        expired = Color(0xFFE0872B),
        blocking = Color(0xFFD64545),
        warning = Color(0xFFE0B800),
        codeBackground = Color(0xFFF3F4F8),
        phoneBezel = Color(0xFF1B1B1F),
    )
}

private val Indigo = Color(0xFF4A5AE8)
private val IndigoDark = Color(0xFFB4BCFF)

private val LightColors: ColorScheme = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E3FF),
    onPrimaryContainer = Color(0xFF0E1A6B),
    secondary = Color(0xFF5B5F73),
    secondaryContainer = Color(0xFFE1E3F6),
    surface = Color(0xFFFCFCFF),
    surfaceVariant = Color(0xFFEEEFF7),
    surfaceContainer = Color(0xFFF2F3FA),
    surfaceContainerLow = Color(0xFFF7F8FD),
    surfaceContainerHigh = Color(0xFFEBECF4),
    background = Color(0xFFFCFCFF),
    outline = Color(0xFFC6C8D6),
    outlineVariant = Color(0xFFE2E3EE),
    error = Color(0xFFBA1A1A),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = IndigoDark,
    onPrimary = Color(0xFF13217D),
    primaryContainer = Color(0xFF2F3EB8),
    onPrimaryContainer = Color(0xFFE0E1FF),
    secondary = Color(0xFFC4C6DA),
    secondaryContainer = Color(0xFF444859),
    surface = Color(0xFF15161C),
    surfaceVariant = Color(0xFF2A2B33),
    surfaceContainer = Color(0xFF1E1F26),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainerHigh = Color(0xFF26272F),
    background = Color(0xFF15161C),
    outline = Color(0xFF585A69),
    outlineVariant = Color(0xFF34353F),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
)

object Dimens {
    val sidebarWidth = 272.dp
    val previewWidth = 340.dp
    val contentMaxWidth = 720.dp
    val radiusSmall = 8.dp
    val radiusMedium = 12.dp
    val radiusLarge = 16.dp
}

@Composable
fun SwitchboardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val status = if (darkTheme) {
        StatusColors(
            live = Color(0xFF5DD38A),
            off = Color(0xFF7C8089),
            expired = Color(0xFFF2A65A),
            blocking = Color(0xFFFF6B6B),
            warning = Color(0xFFFFD54F),
            codeBackground = Color(0xFF1E1F26),
            phoneBezel = Color(0xFF0A0A0C),
        )
    } else {
        LocalStatusColors.current
    }
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

val MaterialTheme.status: StatusColors
    @Composable get() = LocalStatusColors.current
