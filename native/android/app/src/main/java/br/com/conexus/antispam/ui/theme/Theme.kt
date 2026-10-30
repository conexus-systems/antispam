package br.com.conexus.antispam.ui.theme

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import br.com.conexus.antispam.ui.theme.ConexusColors.Primary
import br.com.conexus.antispam.ui.theme.ConexusColors.Surface

@Composable
fun ConexusTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ConexusDarkColors else ConexusLightColors
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = (view.context as? Activity) ?: return@SideEffect
            val window = activity.window
            val primaryColor = colorScheme.primary.toArgb()
            val backgroundColor = colorScheme.background.toArgb()
            window.statusBarColor = if (darkTheme) backgroundColor else primaryColor
            window.navigationBarColor = backgroundColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ConexusMaterialTypography,
        content = content
    )
}