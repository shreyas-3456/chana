// FILE: navigation/NavGraph.kt
package com.chan.mimi.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.ui.components.ChanText
import com.chan.mimi.ui.components.TextVariant
import com.chan.mimi.ui.screens.boards.BoardListScreen
import com.chan.mimi.ui.screens.threads.ThreadListScreen
import com.chan.mimi.ui.theme.ChanGreen

// ============================================================
// ROUTES
// ============================================================

object Routes {
    const val BOARD_LIST  = "board_list"
    const val THREAD_LIST = "thread_list"
}

// ============================================================
// BOTTOM NAV ITEMS
// ============================================================

data class BottomNavItem(
    val label : String,
    val icon  : ImageVector,
    val route : String
)

val bottomNavItems = listOf(
    BottomNavItem("Boards",   Icons.Default.Dashboard, Routes.BOARD_LIST),
    BottomNavItem("/tv/",     Icons.Default.Explore,   Routes.THREAD_LIST),
    BottomNavItem("Saved",    Icons.Default.Bookmark,  "saved"),
    BottomNavItem("Settings", Icons.Default.Settings,  "settings")
)

// ============================================================
// NAV GRAPH
// ============================================================

@Composable
fun ChanNavGraph(
    modifier      : Modifier          = Modifier,
    navController : NavHostController = rememberNavController()
) {
    // ← BoardDto instead of old Board
    var selectedBoard by remember { mutableStateOf<BoardDto?>(null) }

    val currentRoute = navController
        .currentBackStackEntryAsState()
        .value?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        selected = currentRoute == item.route,
                        onClick  = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.BOARD_LIST) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                        icon  = {
                            Icon(
                                imageVector        = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label  = {
                            ChanText(
                                text    = item.label,
                                variant = TextVariant.Meta
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = ChanGreen,
                            selectedTextColor   = ChanGreen,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            indicatorColor      = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.BOARD_LIST,
            modifier         = modifier,
            enterTransition  = { fadeIn(animationSpec = tween(0)) },
            exitTransition   = { fadeOut(animationSpec = tween(0)) }
        ) {
            composable(Routes.BOARD_LIST) {
                BoardListScreen(
                    onBoardClick = { board ->
                        selectedBoard = board          // ← BoardDto now
                        navController.navigate(Routes.THREAD_LIST)
                    }
                )
            }

            composable(Routes.THREAD_LIST) {
                val board = selectedBoard ?: return@composable
                ThreadListScreen(
                    board         = board,             // ← BoardDto now
                    onBackClick   = { navController.popBackStack() },
                    onThreadClick = { }
                )
            }
        }
    }
}