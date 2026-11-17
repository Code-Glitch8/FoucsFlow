package com.focusflow.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusflow.app.ui.blocking.BlockingScreen
import com.focusflow.app.ui.blocking.DetectorTestScreen
import com.focusflow.app.ui.common.rememberContainer
import com.focusflow.app.ui.session.FocusScreen
import com.focusflow.app.ui.session.SessionPrepScreen
import com.focusflow.app.ui.settings.SettingsScreen
import com.focusflow.app.ui.subjects.SubjectsScreen
import com.focusflow.app.ui.tasks.TasksScreen
import com.focusflow.app.ui.theme.FocusFlowTheme
import com.focusflow.app.ui.theme.ThemeMode

private const val ROUTE_SUBJECTS = "subjects"
private const val ROUTE_PREPARE = "prepare"
private const val ROUTE_BLOCKING = "blocking"
private const val ROUTE_DETECTOR = "detector"
private const val ROUTE_AI = "ai"

/**
 * App shell: theme, edge-to-edge scaffold and bottom navigation.
 * Tabs whose phase has not been built yet say so plainly instead of pretending to work.
 */
@Composable
fun FocusFlowRoot() {
    val container = rememberContainer()
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.DARK)

    FocusFlowTheme(themeMode) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val current = Destination.fromRoute(backStack?.destination?.route)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Hidden on sub-screens such as Subjects & chapters.
                if (current != null) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Destination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = current == dest,
                                onClick = {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.start.route,
                modifier = Modifier.padding(padding),
            ) {
                composable(Destination.Home.route) { ShellScreen(Destination.Home) }
                composable(Destination.Tasks.route) {
                    TasksScreen(onManageSubjects = { navController.navigate(ROUTE_SUBJECTS) })
                }
                composable(Destination.Focus.route) {
                    FocusScreen(onStartSession = { navController.navigate(ROUTE_PREPARE) })
                }
                composable(Destination.Statistics.route) { ShellScreen(Destination.Statistics) }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        onOpenBlocking = { navController.navigate(ROUTE_BLOCKING) },
                        onOpenAi = { navController.navigate(ROUTE_AI) },
                    )
                }
                composable(ROUTE_BLOCKING) {
                    BlockingScreen(
                        onBack = { navController.popBackStack() },
                        onTestDetector = { navController.navigate(ROUTE_DETECTOR) },
                    )
                }
                composable(ROUTE_AI) { com.focusflow.app.ui.settings.AiSettingsScreen(onBack = { navController.popBackStack() }) }
                composable(ROUTE_DETECTOR) { DetectorTestScreen(onBack = { navController.popBackStack() }) }
                composable(ROUTE_PREPARE) {
                    SessionPrepScreen(
                        onBack = { navController.popBackStack() },
                        onManageSubjects = { navController.navigate(ROUTE_SUBJECTS) },
                    )
                }
                composable(ROUTE_SUBJECTS) { SubjectsScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

@Composable
private fun ShellScreen(destination: Destination) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "${destination.label}\n\nNot built yet — this screen is implemented in a later phase.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
