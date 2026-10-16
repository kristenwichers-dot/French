package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = FrenchLavender,
    onPrimary = Color.White,
    primaryContainer = LavenderContainer,
    onPrimaryContainer = OnLavenderContainer,
    secondary = SlateAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E9F0),
    onSecondaryContainer = FrenchNavy,
    tertiary = WarmGold,
    onTertiary = Color.White,
    tertiaryContainer = GoldContainer,
    onTertiaryContainer = Color(0xFF382600),
    background = WarmCream,
    onBackground = TextPrimary,
    surface = WarmCreamSurface,
    onSurface = TextPrimary,
    surfaceVariant = WarmSand,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFFD6CEBF)
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkLavender,
    onPrimary = Color(0xFF2C1654),
    primaryContainer = DarkLavenderContainer,
    onPrimaryContainer = Color(0xFFEADBFF),
    secondary = Color(0xFFA6B0C4),
    onSecondary = Color(0xFF1D2736),
    secondaryContainer = Color(0xFF353E50),
    onSecondaryContainer = Color(0xFFDDE3F0),
    tertiary = DarkGold,
    onTertiary = Color(0xFF3A2800),
    tertiaryContainer = Color(0xFF5A4100),
    onTertiaryContainer = Color(0xFFFBEBC7),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = Color(0xFF4C4656)
)

@Composable
fun FrenchStudyCompanionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
