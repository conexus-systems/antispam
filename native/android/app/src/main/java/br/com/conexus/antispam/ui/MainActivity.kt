package br.com.conexus.antispam.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.conexus.antispam.R
import br.com.conexus.antispam.ui.onboarding.OnboardingScreen
import br.com.conexus.antispam.ui.onboarding.OnboardingViewModel
import br.com.conexus.antispam.ui.w10m.W10MTheme
import br.com.conexus.antispam.ui.w10m.provideW10MTheme
import br.com.conexus.antispam.ui.home.HomeScreen
import br.com.conexus.antispam.ui.history.HistoryScreen
import br.com.conexus.antispam.ui.settings.SettingsScreen
import br.com.conexus.antispam.ui.verify.VerifyScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectAsStateWithLifecycle

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val onboardingViewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val onboardingDone by onboardingViewModel.onboardingDone.collectAsStateWithLifecycle(false)

            // Tema W10M com cor de destaque padrão
            provideW10MTheme(dark = true, accentHex = "#60A917") {
                MaterialTheme {
                    Surface(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        color = useW10MTheme().palette.bg
                    ) {
                        val navController = rememberNavController()
                        NavHost(navController, startDestination = if (onboardingDone) "home" else "onboarding") {
                            composable("onboarding") {
                                OnboardingScreen(onFinish = { onboardingViewModel.completeOnboarding() })
                            }
                            composable("home") {
                                HomeScreen(onNavigate = { route -> navController.navigate(route) })
                            }
                            composable("history") {
                                HistoryScreen()
                            }
                            composable("settings") {
                                SettingsScreen()
                            }
                            composable("verify") {
                                VerifyScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}