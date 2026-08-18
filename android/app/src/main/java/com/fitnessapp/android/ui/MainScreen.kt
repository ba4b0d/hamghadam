package com.fitnessapp.android.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.fitnessapp.android.FitnessApp
import com.fitnessapp.android.R
import com.fitnessapp.android.data.model.ChallengeDeepLink
import com.fitnessapp.android.data.model.toNavRoute
import com.fitnessapp.android.ui.challenges.ChallengeDetailScreen
import com.fitnessapp.android.ui.challenges.ChallengesScreen
import com.fitnessapp.android.ui.dashboard.DashboardScreen
import com.fitnessapp.android.ui.friends.FriendsScreen
import com.fitnessapp.android.ui.hr.HrTestScreen
import com.fitnessapp.android.ui.profile.ProfileScreen
import com.fitnessapp.android.ui.settings.AccountScreen

enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
) {
    DASHBOARD("dashboard", R.string.nav_dashboard, Icons.AutoMirrored.Filled.DirectionsRun),
    CHALLENGES("challenges", R.string.nav_challenges, Icons.Filled.EmojiEvents),
    FRIENDS("friends", R.string.nav_friends, Icons.Filled.People),
    ACCOUNT("account", R.string.nav_settings, Icons.Filled.Person),
}

object ChallengeRoutes {
    const val detail = "challenge/{challengeId}?joinCode={joinCode}&showLeaderboard={showLeaderboard}"
}

/**
 * App shell: bottom navigation + routing between Dashboard / Challenges /
 * Account, HR test screen, and the challenge detail screen (with deep-link handling).
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val app = LocalContext.current.applicationContext as FitnessApp
    val pendingRoute by app.container.notificationRouter.pendingRoute.collectAsState()
    val externalDeepLink by app.container.externalDeepLinks.collectAsState()

    // Push notifications while the app is foreground → navigate to the target
    // challenge (same parser + route builder as deep-link intents).
    LaunchedEffect(pendingRoute) {
        val link = pendingRoute ?: return@LaunchedEffect
        navController.navigate(link.toNavRoute()) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
        }
        app.container.notificationRouter.consumeRoute()
    }

    // External VIEW intents (adb, browser, notification tap) → navigate.
    LaunchedEffect(externalDeepLink) {
        val uri = externalDeepLink ?: return@LaunchedEffect
        val link = ChallengeDeepLink.parse(uri)
        if (link != null) {
            navController.navigate(link.toNavRoute()) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
            }
        }
        app.container.externalDeepLinks.value = null
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = null) },
                        label = { Text(stringResource(dest.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.DASHBOARD.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardScreen(onOpenHrTest = {
                    navController.navigate("hr_test")
                })
            }
            composable("hr_test") {
                HrTestScreen(onBack = { navController.popBackStack() })
            }
            composable(TopLevelDestination.CHALLENGES.route) {
                ChallengesScreen(onOpenChallenge = { id ->
                    navController.navigate("challenge/$id")
                })
            }
            composable(TopLevelDestination.FRIENDS.route) {
                FriendsScreen()
            }
            composable(TopLevelDestination.ACCOUNT.route) {
                AccountScreen()
            }
            composable("profile") {
                ProfileScreen()
            }
            composable(
                route = ChallengeRoutes.detail,
                arguments = listOf(
                    navArgument("challengeId") { type = NavType.LongType },
                    navArgument("joinCode") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("showLeaderboard") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "fitnessapp://challenges/{challengeId}" },
                    navDeepLink { uriPattern = "fitnessapp://challenges/{challengeId}/join?code={joinCode}" },
                    navDeepLink { uriPattern = "fitnessapp://challenges/{challengeId}/leaderboard" },
                ),
            ) { entry ->
                val args = entry.arguments
                ChallengeDetailScreen(
                    challengeId = args?.getLong("challengeId") ?: 0L,
                    autoJoinCode = args?.getString("joinCode"),
                    openLeaderboard = args?.getBoolean("showLeaderboard") == true,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
