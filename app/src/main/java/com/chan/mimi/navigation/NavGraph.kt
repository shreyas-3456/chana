package com.chan.mimi.navigation

import android.net.Uri
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chan.mimi.SavedNotificationTarget
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.ui.components.ChanText
import com.chan.mimi.ui.components.TextVariant
import com.chan.mimi.ui.screens.boards.BoardSearchScreen
import com.chan.mimi.ui.screens.boards.EditBoardsScreen
import com.chan.mimi.ui.screens.home.HomeScreen
import com.chan.mimi.ui.screens.home.HomeViewModel
import com.chan.mimi.ui.screens.threads.ThreadDetailScreen
import com.chan.mimi.ui.screens.threads.ThreadListScreen
import com.chan.mimi.ui.screens.threads.WatchedThreadsViewModel
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.SurfaceDark
import com.chan.mimi.ui.theme.TextPrimary

object Routes {
    const val HOME         = "home"
    const val THREAD_LIST  = "thread_list"
    const val EDIT_BOARDS  = "edit_boards"
    const val BOARD_SEARCH = "board_search"
    const val THREAD_DETAIL = "thread_detail"
    const val SAVED         = "saved"
    const val SAVED_THREAD_DETAIL = "saved_thread_detail"

    fun savedThreadDetailRoute(
        boardTag: String,
        threadNo: Long,
        highlightPostId: Long? = null,
        addedPostIds: List<Long> = emptyList(),
        deletedPostIds: List<Long> = emptyList()
    ): String {
        val addedCsv = Uri.encode(addedPostIds.joinToString(","))
        val deletedCsv = Uri.encode(deletedPostIds.joinToString(","))
        val highlightValue = highlightPostId ?: -1L
        return "$SAVED_THREAD_DETAIL/$boardTag/$threadNo?highlightPostId=$highlightValue&addedPostIds=$addedCsv&deletedPostIds=$deletedCsv"
    }
}

@Composable
fun ChanNavGraph(
    modifier      : Modifier          = Modifier,
    navController : NavHostController = rememberNavController(),
    savedNotificationTarget: SavedNotificationTarget? = null,
    onSavedNotificationHandled: () -> Unit = {}
) {
    var selectedBoard by remember { mutableStateOf<BoardDto?>(null) }

    val homeViewModel: HomeViewModel = viewModel()
    val watchedThreadsViewModel: WatchedThreadsViewModel = viewModel()
    val watchedThreads by watchedThreadsViewModel.allWatchedThreads.collectAsStateWithLifecycle()

    val currentRoute = navController
        .currentBackStackEntryAsState()
        .value?.destination?.route

    // Add these two vars alongside selectedBoard
    var selectedThreadNo    by remember { mutableStateOf(0L) }
    var selectedThreadTitle by remember { mutableStateOf("") }

    fun openBoardThreadList(boardTag: String? = selectedBoard?.tag) {
        val targetBoardTag = boardTag ?: return
        val targetBoard = homeViewModel.savedBoards.value.firstOrNull { it.tag == targetBoardTag }
            ?: selectedBoard?.takeIf { it.tag == targetBoardTag }
            ?: return
        selectedBoard = targetBoard

        if (currentRoute == Routes.THREAD_DETAIL &&
            navController.previousBackStackEntry?.destination?.route == Routes.THREAD_LIST
        ) {
            navController.popBackStack(Routes.THREAD_LIST, false)
        } else {
            navController.navigate(Routes.THREAD_LIST) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(savedNotificationTarget?.requestId) {
        val target = savedNotificationTarget ?: return@LaunchedEffect
        navController.navigate(
            Routes.savedThreadDetailRoute(
                boardTag = target.boardTag,
                threadNo = target.threadNo,
                highlightPostId = target.highlightPostId,
                addedPostIds = target.addedPostIds,
                deletedPostIds = target.deletedPostIds
            )
        ) {
            launchSingleTop = true
        }
        onSavedNotificationHandled()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            val showBottomBar = currentRoute in listOf(
                Routes.HOME,
                Routes.THREAD_LIST,
                Routes.THREAD_DETAIL,
                Routes.SAVED,
                "settings"
            )
            if (showBottomBar) {
                NavigationBar(
                    containerColor = SurfaceDark,   // ← explicit, not MaterialTheme.colorScheme.surface
                    tonalElevation = 0.dp
                ) {
                    // ── Boards tab ───────────────────────────
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick  = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        icon   = { Icon(Icons.Default.Dashboard, contentDescription = "Boards") },
                        label  = { ChanText("Boards", variant = TextVariant.Meta) },
                        colors = navItemColors()
                    )

                    // ── Last visited board tab ───────────────
                    val boardLabel = selectedBoard?.let { "/${it.tag}/" } ?: "/ - /"
                    NavigationBarItem(
                        selected = currentRoute == Routes.THREAD_LIST,
                        enabled  = selectedBoard != null,
                        onClick  = {
                            openBoardThreadList()
                        },
                        icon   = { Icon(Icons.Default.Explore, contentDescription = boardLabel) },
                        label  = { ChanText(boardLabel, variant = TextVariant.Meta) },
                        colors = navItemColors()
                    )

                    // ── Saved tab ────────────────────────────
                    NavigationBarItem(
                        selected = currentRoute == Routes.SAVED,
                        onClick  = {
                            if (currentRoute != Routes.SAVED) {
                                navController.navigate(Routes.SAVED) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                        icon     = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") },
                        label    = { ChanText("Saved", variant = TextVariant.Meta) },
                        colors   = navItemColors()
                    )

                    // ── Settings tab ─────────────────────────
                    NavigationBarItem(
                        selected = currentRoute == "settings",
                        onClick  = { /* TODO */ },
                        icon     = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label    = { ChanText("Settings", variant = TextVariant.Meta) },
                        colors   = navItemColors()
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Routes.HOME,
            modifier         = modifier,
            enterTransition  = { fadeIn(animationSpec = tween(0)) },
            exitTransition   = { fadeOut(animationSpec = tween(0)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onBoardClick = { board ->
                        selectedBoard = board
                        openBoardThreadList(board.tag)
                    },
                    onEditBoards = { navController.navigate(Routes.EDIT_BOARDS) },
                    innerPadding = innerPadding,
                    viewModel    = homeViewModel
                )
            }

            composable(Routes.THREAD_LIST) {
                val board = selectedBoard ?: return@composable
                ThreadListScreen(
                    board         = board,
                    onBackClick   = { navController.popBackStack() },
                    onThreadClick = { thread ->
                        selectedThreadNo    = thread.id
                        selectedThreadTitle = thread.safeSubject().ifEmpty { thread.id.toString() }
                        navController.navigate(Routes.THREAD_DETAIL)
                    },
                    watchedThreads = watchedThreads,
                    onOpenWatchedThread = { watchedThread ->
                        selectedThreadNo = watchedThread.threadNo
                        selectedThreadTitle = watchedThread.title
                        navController.navigate(Routes.THREAD_DETAIL) {
                            launchSingleTop = true
                        }
                    },
                    onRemoveWatchedThread = { watchedThread ->
                        watchedThreadsViewModel.removeThread(watchedThread.boardTag, watchedThread.threadNo)
                    },
                    onToggleWatchedPolling = { watchedThread, enabled ->
                        watchedThreadsViewModel.togglePolling(
                            watchedThread.boardTag,
                            watchedThread.threadNo,
                            enabled
                        )
                    },
                    onOpenSaved = {
                        navController.navigate(Routes.SAVED) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    bottomBarPadding = innerPadding.calculateBottomPadding()
                )
            }
            composable(Routes.THREAD_DETAIL) {
                val board = selectedBoard ?: return@composable
                ThreadDetailScreen(
                    boardTag    = board.tag,
                    threadNo    = selectedThreadNo,
                    threadTitle = selectedThreadTitle,
                    onBackClick = { navController.popBackStack() },
                    onSwitchThread = { newBoardTag, newThreadNo, newTitle ->
                        val matchingBoard = homeViewModel.savedBoards.value.firstOrNull { it.tag == newBoardTag }
                        if (matchingBoard != null) {
                            selectedBoard = matchingBoard
                        }
                        selectedThreadNo = newThreadNo
                        selectedThreadTitle = newTitle
                    },
                    onOpenBoardThreadList = {
                        openBoardThreadList(board.tag)
                    },
                    onOpenSaved = {
                        navController.navigate(Routes.SAVED) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    watchedThreadsViewModel = watchedThreadsViewModel,
                    bottomBarPadding = innerPadding.calculateBottomPadding()
                )
            }

            composable(Routes.EDIT_BOARDS) {
                EditBoardsScreen(
                    onBackClick = { navController.popBackStack() },
                    onSearch    = { navController.navigate(Routes.BOARD_SEARCH) },
                    viewModel   = homeViewModel
                )
            }

            composable(Routes.BOARD_SEARCH) {
                BoardSearchScreen(
                    onBackClick = { navController.popBackStack() },
                    onDone      = { navController.popBackStack(Routes.HOME, false) },
                    viewModel   = homeViewModel
                )
            }

            composable(Routes.SAVED) {
                com.chan.mimi.ui.screens.saved.SavedScreen(
                    innerPadding = innerPadding,
                    onThreadClick = { detail ->
                        navController.navigate(
                            Routes.savedThreadDetailRoute(
                                boardTag = detail.boardTag,
                                threadNo = detail.thread.id
                            )
                        )
                    },
                    onOpenBoardThreadList = {
                        openBoardThreadList()
                    }
                )
            }

            composable(
                route = "${Routes.SAVED_THREAD_DETAIL}/{boardTag}/{threadNo}?highlightPostId={highlightPostId}&addedPostIds={addedPostIds}&deletedPostIds={deletedPostIds}",
                arguments = listOf(
                    androidx.navigation.navArgument("boardTag") { type = androidx.navigation.NavType.StringType },
                    androidx.navigation.navArgument("threadNo") { type = androidx.navigation.NavType.LongType },
                    androidx.navigation.navArgument("highlightPostId") {
                        type = androidx.navigation.NavType.LongType
                        defaultValue = -1L
                    },
                    androidx.navigation.navArgument("addedPostIds") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    },
                    androidx.navigation.navArgument("deletedPostIds") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val boardTag = backStackEntry.arguments?.getString("boardTag") ?: ""
                val threadNo = backStackEntry.arguments?.getLong("threadNo") ?: 0L
                val highlightPostId = backStackEntry.arguments
                    ?.getLong("highlightPostId")
                    ?.takeIf { it > 0L }
                val addedHighlightPostIds = backStackEntry.arguments
                    ?.getString("addedPostIds")
                    .orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                val deletedHighlightPostIds = backStackEntry.arguments
                    ?.getString("deletedPostIds")
                    .orEmpty()
                    .split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                com.chan.mimi.ui.screens.saved.SavedThreadDetailScreen(
                    boardTag    = boardTag,
                    threadNo    = threadNo,
                    threadTitle = threadNo.toString(),
                    onBackClick = { navController.popBackStack() },
                    onOpenBoardThreadList = {
                        openBoardThreadList(boardTag)
                    },
                    highlightPostId = highlightPostId,
                    addedHighlightPostIds = addedHighlightPostIds,
                    deletedHighlightPostIds = deletedHighlightPostIds
                )
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor   = ChanGreen,
    selectedTextColor   = ChanGreen,
    indicatorColor      = Color.Transparent,
    unselectedIconColor = Color(0xFFAAAAAA),   // ← explicit light grey, not TextPrimary.copy(alpha)
    unselectedTextColor = Color(0xFFAAAAAA),   // ← same
)
