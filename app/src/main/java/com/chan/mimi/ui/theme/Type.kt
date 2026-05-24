package com.chan.mimi.ui.theme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.chan.mimi.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

val JetBrainsMonoFont = GoogleFont("JetBrains Mono")

val JetBrainsMono = FontFamily(
    Font(
        googleFont   = JetBrainsMonoFont,
        fontProvider = provider,
        weight       = FontWeight.Normal
    ),
    Font(
        googleFont   = JetBrainsMonoFont,
        fontProvider = provider,
        weight       = FontWeight.SemiBold
    ),
    Font(
        googleFont   = JetBrainsMonoFont,
        fontProvider = provider,
        weight       = FontWeight.Bold
    )
)

val TypographyUsername = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize   = 13.sp,
    fontWeight = FontWeight.SemiBold,
    lineHeight = 16.sp
)

val TypographyBody = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize   = 13.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 18.sp
)

val TypographyMeta = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize   = 11.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 14.sp
)

val TypographyLabel = TextStyle(
    fontFamily = JetBrainsMono,
    fontSize   = 12.sp,
    fontWeight = FontWeight.Bold,
    lineHeight = 14.sp
)