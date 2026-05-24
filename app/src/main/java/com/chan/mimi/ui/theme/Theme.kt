package com.chan.mimi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ChanColorScheme = darkColorScheme(
    background           = BackgroundDark,
    onBackground         = TextPrimary,
    surface              = SurfaceDark,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceDark,
    onSurfaceVariant     = TextPrimary,
    primary              = ChanGreen,
    onPrimary            = BackgroundDark,
    primaryContainer     = ChanGreen,
    onPrimaryContainer   = BackgroundDark,
    secondary            = ChanGreen,
    onSecondary          = BackgroundDark,
    secondaryContainer   = SurfaceDark,
    onSecondaryContainer = TextPrimary,
    tertiary             = ChanGreen,
    onTertiary           = BackgroundDark,
    outline              = ElevatedDark,
    outlineVariant       = ElevatedDark,
)

private val ChanTypography = Typography(
    bodyMedium  = TypographyBody,
    labelMedium = TypographyLabel,
    labelSmall  = TypographyMeta,
    titleSmall  = TypographyUsername,
)

@Composable
fun ChanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChanColorScheme,
        typography  = ChanTypography,
        content     = content
    )
}