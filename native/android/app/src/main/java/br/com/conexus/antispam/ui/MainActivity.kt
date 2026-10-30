package br.com.conexus.antispam.ui

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ads.AdsManager
import br.com.conexus.antispam.database.UserSettings
import br.com.conexus.antispam.domain.SettingsRepository
import br.com.conexus.antispam.ui.history.HistoryScreen
import br.com.conexus.antispam.ui.home.HomeScreen
import br.com.conexus.antispam.ui.lists.ListsScreen
import br.com.conexus.antispam.ui.onboarding.OnboardingScreen
import br.com.conexus.antispam.ui.privacy.PrivacyScreen
import br.com.conexus.antispam.ui.rules.RulesScreen
import br.com.conexus.antispam.ui.settings.SettingsScreen
import br.com.conexus.antispam.ui.theme.DEFAULT_ACCENT
import br.com.conexus.antispam.ui.theme.provideW10MTheme
import br.com.conexus.antispam.ui.theme.useW10MTheme
import br.com.conexus.antispam.ui.verify.VerifyScreen
import br.com.conexus.antispam.ui.w10m.W10MAppBar
import br.com.conexus.antispam.ui.w10m.W10MAppBarButton
import br.com.conexus.antispam.ui.w10m.W10MIcon
import br.com.conexus.antispam.ui.w10m.W10MIcons
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Configurações lidas uma vez para tema e para decidir entre onboarding e app. null = ainda carregando. */
@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings?> = settings.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** Aceita o ajuste de destaque salvo; o antigo padrão azul do banco cede à cor da marca. */
internal fun resolveAccent(saved: String?): String {
    if (saved.isNullOrBlank() || saved.equals(LEGACY_DEFAULT_ACCENT, ignoreCase = true)) return DEFAULT_ACCENT
    return runCatching { AndroidColor.parseColor(saved); saved }.getOrDefault(DEFAULT_ACCENT)
}

internal fun resolveDark(theme: String?, systemDark: Boolean): Boolean = when (theme) {
    "dark" -> true
    "light" -> false
    else -> systemDark
}

private const val LEGACY_DEFAULT_ACCENT = "#1BA1E2"

private object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val VERIFY = "verify"
    const val SETTINGS = "settings"
    const val LISTS = "lists"
    const val RULES = "rules"
    const val PRIVACY = "privacy"
    val tabs = listOf(HOME, HISTORY, VERIFY, SETTINGS)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var adsManager: AdsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adsManager.start(this)
        setContent { AntiSpamApp() }
    }
}

@Composable
private fun AntiSpamApp(viewModel: MainViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val dark = resolveDark(settings?.theme, isSystemInDarkTheme())

    provideW10MTheme(dark = dark, accentHex = resolveAccent(settings?.accent)) {
        MaterialTheme {
            val palette = useW10MTheme().palette
            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                    window.statusBarColor = palette.bg.toArgb()
                    window.navigationBarColor = palette.chrome.toArgb()
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !dark
                    controller.isAppearanceLightNavigationBars = !dark
                }
            }
            Box(modifier = Modifier.fillMaxSize().background(palette.bg)) {
                when (val s = settings) {
                    null -> Unit // carregando: só o fundo, sem piscar o onboarding
                    else -> if (s.onboardingDone) MainContent() else Box(Modifier.fillMaxSize().safeDrawingPadding()) { OnboardingScreen() }
                }
            }
        }
    }
}

@Composable
private fun MainContent() {
    // Criado aqui dentro: depois de "Apagar todos os meus dados" a navegação recomeça na Home.
    val nav = rememberNavController()
    val route by nav.currentBackStackEntryAsState()
    val current = route?.destination?.route

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Box(modifier = Modifier.weight(1f)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenHistory = { nav.navigateTab(Routes.HISTORY) },
                        onOpenSettings = { nav.navigateTab(Routes.SETTINGS) },
                    )
                }
                composable(Routes.HISTORY) { HistoryScreen() }
                composable(Routes.VERIFY) { VerifyScreen() }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onOpenLists = { nav.navigate(Routes.LISTS) },
                        onOpenRules = { nav.navigate(Routes.RULES) },
                        onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                    )
                }
                composable(Routes.LISTS) { ListsScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.RULES) { RulesScreen(onBack = { nav.popBackStack() }) }
                composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
            }
        }
        if (current in Routes.tabs) {
            BottomTabs(current = current, onSelect = { nav.navigateTab(it) })
        }
    }
}

private fun NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun BottomTabs(current: String?, onSelect: (String) -> Unit) {
    val palette = useW10MTheme().palette
    fun tab(route: String, glyph: br.com.conexus.antispam.ui.w10m.W10MIconGlyph, label: String) =
        W10MAppBarButton(
            key = route,
            label = label,
            icon = {
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    W10MIcon(
                        name = glyph,
                        size = 18.dp,
                        color = if (current == route) palette.primary else palette.text,
                    )
                }
            },
            onClick = { onSelect(route) },
            active = current == route,
        )
    W10MAppBar(
        buttons = listOf(
            tab(Routes.HOME, W10MIcons.Shield, stringResource(R.string.nav_home)),
            tab(Routes.HISTORY, W10MIcons.History, stringResource(R.string.nav_history)),
            tab(Routes.VERIFY, W10MIcons.Search, stringResource(R.string.nav_verify)),
            tab(Routes.SETTINGS, W10MIcons.Settings, stringResource(R.string.nav_settings)),
        ),
        modifier = Modifier.navigationBarsPadding(),
    )
}
