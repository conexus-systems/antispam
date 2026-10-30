package br.com.conexus.antispam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Cores da marca Conexus (baseadas no design system Fluent/Metro)
val ConexusLightColors = lightColorScheme(
    primary = Color(0xFF1BA1E2),        // Azul Conexus
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EFFF),
    onPrimaryContainer = Color(0xFF001F33),
    secondary = Color(0xFF4A6572),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F0),
    onSecondaryContainer = Color(0xFF041F2A),
    tertiary = Color(0xFF6C5B7B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF3EDF7),
    onTertiaryContainer = Color(0xFF24192D),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE0E3E8),
    onSurfaceVariant = Color(0xFF44474C),
    outline = Color(0xFF74777C),
    outlineVariant = Color(0xFFC4C6CC),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF9CCCFF),
    surfaceTint = Color(0xFF1BA1E2)
)

val ConexusDarkColors = darkColorScheme(
    primary = Color(0xFF9CCCFF),
    onPrimary = Color(0xFF00334D),
    primaryContainer = Color(0xFF004B6E),
    onPrimaryContainer = Color(0xFFD6EFFF),
    secondary = Color(0xFFBAC8D4),
    onSecondary = Color(0xFF1D333D),
    secondaryContainer = Color(0xFF344C57),
    onSecondaryContainer = Color(0xFFD6E4F0),
    tertiary = Color(0xFFD8C2E8),
    onTertiary = Color(0xFF3A2C46),
    tertiaryContainer = Color(0xFF51425E),
    onTertiaryContainer = Color(0xFFF3EDF7),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE4E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE4E2E6),
    surfaceVariant = Color(0xFF44474C),
    onSurfaceVariant = Color(0xFFC4C6CC),
    outline = Color(0xFF8E9095),
    outlineVariant = Color(0xFF44474C),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE4E2E6),
    inverseOnSurface = Color(0xFF1A1C1E),
    inversePrimary = Color(0xFF1BA1E2),
    surfaceTint = Color(0xFF9CCCFF)
)

// Cores semânticas para o app
object ConexusColors {
    val Primary = ConexusLightColors.primary
    val OnPrimary = ConexusLightColors.onPrimary
    val PrimaryContainer = ConexusLightColors.primaryContainer
    val OnPrimaryContainer = ConexusLightColors.onPrimaryContainer

    val Secondary = ConexusLightColors.secondary
    val OnSecondary = ConexusLightColors.onSecondary

    val Tertiary = ConexusLightColors.tertiary
    val OnTertiary = ConexusLightColors.onTertiary

    val Error = ConexusLightColors.error
    val OnError = ConexusLightColors.onError
    val ErrorContainer = ConexusLightColors.errorContainer

    val Warning = Color(0xFFF57C00)
    val WarningContainer = Color(0xFFFFF3E0)

    val Success = Color(0xFF2E7D32)
    val SuccessContainer = Color(0xFFE8F5E9)

    val Background = ConexusLightColors.background
    val OnBackground = ConexusLightColors.onBackground
    val Surface = ConexusLightColors.surface
    val OnSurface = ConexusLightColors.onSurface
    val SurfaceVariant = ConexusLightColors.surfaceVariant
    val OnSurfaceVariant = ConexusLightColors.onSurfaceVariant

    val Outline = ConexusLightColors.outline
    val OutlineVariant = ConexusLightColors.outlineVariant
}