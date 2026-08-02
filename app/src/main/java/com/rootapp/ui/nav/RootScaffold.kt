package com.rootapp.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rootapp.ui.common.PlaceholderScreen
import com.rootapp.ui.common.SkyBackground
import com.rootapp.ui.home.HomeScreen
import com.rootapp.ui.reflection.ReflectionScreen
import com.rootapp.data.SettingsStore
import com.rootapp.ui.moments.MomentsScreen
import com.rootapp.ui.onboarding.OnboardingScreen
import com.rootapp.ui.shield.ShieldScreen
import com.rootapp.ui.stories.StoriesScreen
import com.rootapp.ui.theme.LocalRootPalette
import com.rootapp.ui.theme.RootTheme
import com.rootapp.ui.theme.Sky
import com.rootapp.ui.you.YouScreen
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
fun RootScaffold(currentHour: Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val repo = remember { com.rootapp.data.SupabaseRepository(context) }
    val timeOfDay = remember(currentHour) { Sky.fromHour(currentHour) }
    var minimalist by remember { mutableStateOf(settings.minimalist) }
    var personality by remember { mutableStateOf(settings.personality) }
    var onboarded by remember { mutableStateOf(settings.onboarded) }
    var authed by remember { mutableStateOf(repo.loggedIn) }
    val userName = remember { settings.userName }

    RootTheme(timeOfDay = timeOfDay, minimalist = minimalist) {
        if (!onboarded) {
            OnboardingScreen(onDone = { settings.onboarded = true; onboarded = true })
            return@RootTheme
        }
        if (!authed) {
            com.rootapp.ui.auth.AuthScreen(onAuthed = { authed = true })
            return@RootTheme
        }
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination
        val routeName = currentRoute?.route
        val isNested = routeName == "reflection" || routeName == "voice"

        Box(Modifier.fillMaxSize()) {
        SkyBackground(hour = currentHour, minimalist = minimalist, modifier = Modifier.matchParentSize())
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                    title = { Text(if (routeName == "reflection") "Reflection" else "Root") },
                    navigationIcon = {
                        if (isNested) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { minimalist = !minimalist; settings.minimalist = minimalist }) {
                            Icon(Icons.Outlined.Contrast, contentDescription = "Toggle minimalist mode")
                        }
                    },
                )
            },
            bottomBar = {
                NavigationBar(containerColor = LocalRootPalette.current.surface) {
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
                    .padding(inner),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Tab.HOME.route,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) },
                ) {
                    composable(Tab.HOME.route) {
                        HomeScreen(
                            userName = userName,
                            onStartReflection = { navController.navigate("reflection") },
                            onTalk = { navController.navigate("voice") },
                        )
                    }
                    composable("reflection") { ReflectionScreen(userName = userName) }
                    composable("voice") {
                        com.rootapp.ui.voice.VoiceSessionScreen(
                            userName = userName,
                            onExit = { navController.popBackStack() },
                        )
                    }
                    composable(Tab.SHIELD.route) { ShieldScreen() }
                    composable(Tab.MOMENTS.route) { MomentsScreen() }
                    composable(Tab.STORIES.route) { StoriesScreen() }
                    composable(Tab.YOU.route) {
                        YouScreen(
                            userName = userName,
                            minimalist = minimalist,
                            onMinimalistChange = { minimalist = it; settings.minimalist = it },
                            personality = personality,
                            onPersonalityChange = { personality = it; settings.personality = it },
                            onLogout = { repo.signOut(); authed = false },
                        )
                    }
                }
            }
        }
        }
    }
}
