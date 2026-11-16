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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusflow.app.ui.theme.FocusFlowTheme

/**
 * App shell: theme, edge-to-edge scaffold and bottom navigation.
 * Screen bodies are filled in by later phases; until then each tab states
 * plainly what it will contain instead of pretending to work.
 */
@Composable
fun FocusFlowRoot() {
    FocusFlowTheme {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val current = Destination.fromRoute(backStack?.destination?.route)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
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
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Destination.start.route,
                modifier = Modifier.padding(padding),
            ) {
                Destination.entries.forEach { dest ->
                    composable(dest.route) { ShellScreen(dest) }
                }
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
