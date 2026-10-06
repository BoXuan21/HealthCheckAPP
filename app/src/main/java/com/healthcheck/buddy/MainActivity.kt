package com.healthcheck.buddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.healthcheck.buddy.domain.Profile
import com.healthcheck.buddy.ui.screens.BuddyScreen
import com.healthcheck.buddy.ui.screens.OnboardingScreen
import com.healthcheck.buddy.ui.screens.PlanScreen
import com.healthcheck.buddy.ui.screens.ProgressScreen
import com.healthcheck.buddy.ui.screens.SettingsScreen
import com.healthcheck.buddy.ui.screens.TodayScreen
import com.healthcheck.buddy.ui.theme.HealthCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as HealthCheckApp).repo
        setContent {
            HealthCheckTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Loading = Result not yet emitted; null profile = first run.
                    val profile by remember { repo.settings.profile }.collectAsState(initial = Loading)
                    when (val p = profile) {
                        Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        null -> OnboardingScreen()
                        else -> MainScaffold(p)
                    }
                }
            }
        }
    }
}

private val Loading = Profile(name = "\u0000loading")

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Default.Today),
    BUDDY("buddy", "Buddy", Icons.Default.Chat),
    PROGRESS("progress", "Progress", Icons.Default.ShowChart),
    PLAN("plan", "Plan", Icons.Default.Restaurant),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(profile: Profile) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val openSettings = { nav.navigate("settings") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (route) {
                            "settings" -> "Settings"
                            Tab.BUDDY.route -> profile.buddyName
                            else -> Tab.entries.firstOrNull { it.route == route }?.label ?: ""
                        }
                    )
                },
                navigationIcon = {
                    if (route == "settings") IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (route != "settings") IconButton(onClick = openSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        bottomBar = {
            if (route != "settings") NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(Tab.TODAY.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.TODAY.route, modifier = Modifier.padding(padding)) {
            composable(Tab.TODAY.route) { TodayScreen(profile, openSettings) }
            composable(Tab.BUDDY.route) { BuddyScreen(profile, openSettings) }
            composable(Tab.PROGRESS.route) { ProgressScreen(profile, openSettings) }
            composable(Tab.PLAN.route) { PlanScreen(profile, openSettings) }
            composable("settings") { SettingsScreen(profile) }
        }
    }
}
