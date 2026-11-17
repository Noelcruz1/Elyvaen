package com.mardous.booming.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Elyvaen palette: Symphony's warm near-black surfaces with Miku teal/pink accents.
 * Kept under the Aurora names so existing preferences and call sites keep working.
 */
val ElyvaenGradient = listOf(
    Color(0xFF071012),
    Color(0xFF0A2325),
    Color(0xFF24162E),
    Color(0xFF3B1730)
)

val AuroraGradient = ElyvaenGradient

val ElyvaenDarkScheme = darkColorScheme(
    primary = Color(0xFF5FE6D8),
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0E3B38),
    onPrimaryContainer = Color(0xFFB6FFF5),
    secondary = Color(0xFFFF8DBB),
    onSecondary = Color(0xFF3B001C),
    secondaryContainer = Color(0xFF4C1932),
    onSecondaryContainer = Color(0xFFFFD9E8),
    tertiary = Color(0xFFC9B8FF),
    onTertiary = Color(0xFF24124A),
    tertiaryContainer = Color(0xFF3A2963),
    onTertiaryContainer = Color(0xFFE9DDFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF08090B),
    onBackground = Color(0xFFEDF6F4),
    surface = Color(0xFF0B0D10),
    onSurface = Color(0xFFEDF6F4),
    surfaceVariant = Color(0xFF192325),
    onSurfaceVariant = Color(0xFFA6B8B5),
    outline = Color(0xFF5D6D6B),
    outlineVariant = Color(0xFF243230),
    inverseSurface = Color(0xFFEDF6F4),
    inverseOnSurface = Color(0xFF10201F),
    inversePrimary = Color(0xFF00695C),
    surfaceDim = Color(0xFF08090B),
    surfaceBright = Color(0xFF263238),
    surfaceContainerLowest = Color(0xFF050607),
    surfaceContainerLow = Color(0xFF0E1114),
    surfaceContainer = Color(0xFF12161A),
    surfaceContainerHigh = Color(0xFF181D21),
    surfaceContainerHighest = Color(0xFF20262B)
)

val AuroraDarkScheme = ElyvaenDarkScheme

val ElyvaenLightScheme = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2E5),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF9C3D64),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9E8),
    onSecondaryContainer = Color(0xFF3B001C),
    tertiary = Color(0xFF5A4A8A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE7DEFF),
    onTertiaryContainer = Color(0xFF1B0F3F),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6FBFA),
    onBackground = Color(0xFF151D1C),
    surface = Color(0xFFF6FBFA),
    onSurface = Color(0xFF151D1C),
    surfaceVariant = Color(0xFFD5E7E3),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    outlineVariant = Color(0xFFBEC9C6),
    inverseSurface = Color(0xFF2A3130),
    inverseOnSurface = Color(0xFFEBF1EF),
    inversePrimary = Color(0xFF5FE6D8),
    surfaceDim = Color(0xFFD6DBDA),
    surfaceBright = Color(0xFFF6FBFA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5F4),
    surfaceContainer = Color(0xFFEAF0EF),
    surfaceContainerHigh = Color(0xFFE4EAE9),
    surfaceContainerHighest = Color(0xFFDEE5E4)
)

val AuroraLightScheme = ElyvaenLightScheme
