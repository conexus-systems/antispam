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
        lineHeight = androidx.compose.ui.unit.sp(64),
        letterSpacing = androidx.compose.ui.unit.sp(-0.25)
    )
    val DisplayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = androidx.compose.ui.unit.sp(52),
        letterSpacing = androidx.compose.ui.unit.sp(0)
    )
    val DisplaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = androidx.compose.ui.unit.sp(44),
        letterSpacing = androidx.compose.ui.unit.sp(0)
    )

    val Header = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = androidx.compose.ui.unit.sp(40),
        letterSpacing = androidx.compose.ui.unit.sp(0)
    )

    val TitleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = androidx.compose.ui.unit.sp(28),
        letterSpacing = androidx.compose.ui.unit.sp(0)
    )
    val TitleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = androidx.compose.ui.unit.sp(24),
        letterSpacing = androidx.compose.ui.unit.sp(0.15)
    )
    val TitleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = androidx.compose.ui.unit.sp(20),
        letterSpacing = androidx.compose.ui.unit.sp(0.1)
    )

    val Body = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = androidx.compose.ui.unit.sp(24),
        letterSpacing = androidx.compose.ui.unit.sp(0.5)
    )
    val LabelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = androidx.compose.ui.unit.sp(20),
        letterSpacing = androidx.compose.ui.unit.sp(0.1)
    )
    val LabelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = androidx.compose.ui.unit.sp(16),
        letterSpacing = androidx.compose.ui.unit.sp(0.5)
    )
    val LabelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = androidx.compose.ui.unit.sp(16),
        letterSpacing = androidx.compose.ui.unit.sp(0.5)
    )

    val Caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = androidx.compose.ui.unit.sp(16),
        letterSpacing = androidx.compose.ui.unit.sp(0.4)
    )
}

val ConexusTypography = Typography(
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