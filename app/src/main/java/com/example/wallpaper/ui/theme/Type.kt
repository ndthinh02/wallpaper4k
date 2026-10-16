package com.example.wallpaper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import com.example.wallpaper.R

val Roboto = FontFamily(
    Font(R.font.roboto_thin, FontWeight.Thin),
    Font(R.font.roboto_extra_light, FontWeight.ExtraLight),
    Font(R.font.roboto_light, FontWeight.Light),
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_semi_bold, FontWeight.SemiBold),
    Font(R.font.roboto_bold, FontWeight.Bold),
    Font(R.font.roboto_extra_bold, FontWeight.ExtraBold),
    Font(R.font.roboto_black, FontWeight.Black),
    Font(R.font.roboto_thin_italic, FontWeight.Thin, FontStyle.Italic),
    Font(R.font.roboto_extra_light_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.roboto_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.roboto_regular_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.roboto_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.roboto_semi_bold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.roboto_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.roboto_extra_bold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.roboto_black_italic, FontWeight.Black, FontStyle.Italic)
)

val WalperTypography = Typography(
    displayLarge = TextStyle(fontFamily = Roboto, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = Roboto, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = Roboto, fontSize = 36.sp, lineHeight = 44.sp),
    titleSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    headlineLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Roboto,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

val Typography = WalperTypography
