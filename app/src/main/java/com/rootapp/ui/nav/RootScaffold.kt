package com.rootapp.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rootapp.ui.common.PlaceholderScreen
import com.rootapp.ui.home.HomeScreen
import com.rootapp.ui.reflection.ReflectionScreen
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.ui.theme.RootTheme
import com.rootapp.ui.theme.Sky
import java.util.Calendar

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Filled.Home),
    SHIELD("shield", "Shield", Icons.Filled.Menu),
    MOMENTS("moments", "Moments", Icons.Filled.Place),
    STORIES("stories", "Stories", Icons.Filled.DateRange),
    YOU("you", "You", Icons.Filled.Person),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScaffold(userName: String = "Vaibhav", currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    val timeOfDay = remember(currentHour) { Sky.fromHour(currentHour) }
    var minimalist by rememberSaveable { mutableStateOf(false) }

    RootTheme(timeOfDay = timeOfDay, minimalist = minimalist) {
        val palette = LocalRootPalette.current
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Root") },
                    actions = {
                        IconButton(onClick = { minimalist = !minimalist }) {
                            Icon(Icons.Outlined.Contrast, contentDescription = "Toggle minimalist mode")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(palette.bg1, palette.bg2)))
                    .padding(inner),
            ) {
                NavHost(navController = navController, startDestination = Tab.HOME.route) {
                    composable(Tab.HOME.route) {
                        HomeScreen(
                            userName = userName,
                            onStartReflection = { navController.navigate("reflection") },
                        )
                    }
                    composable("reflection") { ReflectionScreen(userName = userName) }
                    composable(Tab.SHIELD.route) {
                        PlaceholderScreen("Shield", "Screen-time insights, AI analysis, and app interrupts.")
                    }
                    composable(Tab.MOMENTS.route) {
                        PlaceholderScreen("Moments", "Geofence nudges and food/sleep logging.")
                    }
                    composable(Tab.STORIES.route) {
                        PlaceholderScreen("Stories", "A finite, calming scroll. The ending is the feature.")
                    }
                    composable(Tab.YOU.route) {
                        PlaceholderScreen("You", "Settings, appearance, personality, and premium.")
                    }
                }
            }
        }
    }
}
