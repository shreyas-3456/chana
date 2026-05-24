// FILE: ui/theme/Theme.kt
package com.chan.mimi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ChanColorScheme = darkColorScheme(
    background  = BackgroundDark,
    surface     = SurfaceDark,
    primary     = ChanGreen,
    onBackground = TextPrimary,
    onSurface   = TextPrimary,
)

private val ChanTypography = Typography(
    bodyMedium  = TypographyBody,
    labelMedium = TypographyLabel,
    labelSmall  = TypographyMeta,
    titleSmall  = TypographyUsername,
)

@Composable
fun ChanTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ChanColorScheme,
        typography  = ChanTypography,
        content     = content
    )
}