package com.pushlife.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// cores da marca: laranja e amarelo, texto escuro por cima
val Orange = Color(0xFFFF6A00)
val Yellow = Color(0xFFFFC300)
val Ink = Color(0xFF1A1208)

private val LightColors = lightColorScheme(
    primary = Color(0xFFF26200),
    onPrimary = Ink,
    primaryContainer = Color(0xFFFFDCC7),
    onPrimaryContainer = Color(0xFF3A1600),
    secondary = Color(0xFFE0A800),
    onSecondary = Ink,
    secondaryContainer = Color(0xFFFFEDB3),
    onSecondaryContainer = Color(0xFF2E2200),
    background = Color(0xFFFFF8F2),
    onBackground = Color(0xFF1E1814),
    surface = Color(0xFFFFF8F2),
    onSurface = Color(0xFF1E1814),
    surfaceVariant = Color(0xFFF4E6DA),
    onSurfaceVariant = Color(0xFF5A4C42),
    surfaceContainer = Color(0xFFFBEEE4),
    outline = Color(0xFF8E7D71),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF7A1A),
    onPrimary = Ink,
    primaryContainer = Color(0xFF5C2600),
    onPrimaryContainer = Color(0xFFFFDCC7),
    secondary = Yellow,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF4A3800),
    onSecondaryContainer = Color(0xFFFFEDB3),
    background = Color(0xFF151210),
    onBackground = Color(0xFFF3ECE6),
    surface = Color(0xFF151210),
    onSurface = Color(0xFFF3ECE6),
    surfaceVariant = Color(0xFF2B2420),
    onSurfaceVariant = Color(0xFFD5C4B8),
    surfaceContainer = Color(0xFF211C19),
    outline = Color(0xFF9E8D81),
)

private val base = Typography()

// títulos pesados e itálicos, estilo placar de academia
private val AppTypography = base.copy(
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontStyle = FontStyle.Italic),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp),
)

@Composable
fun PushLifeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
