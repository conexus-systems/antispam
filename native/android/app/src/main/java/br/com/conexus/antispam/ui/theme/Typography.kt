package br.com.conexus.antispam.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tipografia baseada no design system Fluent/Metro (Segoe/Selawik)
object ConexusTypography {
    val DisplayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = (64).sp,
        letterSpacing = (-0.25).sp
    )
    val DisplayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = (52).sp,
        letterSpacing = (0).sp
    )
    val DisplaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = (44).sp,
        letterSpacing = (0).sp
    )

    val Header = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = (40).sp,
        letterSpacing = (0).sp
    )

    val TitleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = (28).sp,
        letterSpacing = (0).sp
    )
    val TitleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = (24).sp,
        letterSpacing = (0.15).sp
    )
    val TitleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = (20).sp,
        letterSpacing = (0.1).sp
    )

    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = (24).sp,
        letterSpacing = (0.5).sp
    )
    val LabelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = (20).sp,
        letterSpacing = (0.1).sp
    )
    val LabelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = (16).sp,
        letterSpacing = (0.5).sp
    )
    val LabelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = (16).sp,
        letterSpacing = (0.5).sp
    )

    val Caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = (16).sp,
        letterSpacing = (0.4).sp
    )
}

val ConexusMaterialTypography = Typography(
    displayLarge = ConexusTypography.DisplayLarge,
    displayMedium = ConexusTypography.DisplayMedium,
    displaySmall = ConexusTypography.DisplaySmall,
    headlineLarge = ConexusTypography.Header,
    headlineMedium = ConexusTypography.TitleLarge,
    headlineSmall = ConexusTypography.TitleMedium,
    titleLarge = ConexusTypography.TitleLarge,
    titleMedium = ConexusTypography.TitleMedium,
    titleSmall = ConexusTypography.TitleSmall,
    bodyLarge = ConexusTypography.Body,
    bodyMedium = ConexusTypography.Body,
    bodySmall = ConexusTypography.Caption,
    labelLarge = ConexusTypography.LabelLarge,
    labelMedium = ConexusTypography.LabelMedium,
    labelSmall = ConexusTypography.LabelSmall
)