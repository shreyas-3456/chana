// FILE: ui/screens/threads/ThreadListScreen.kt
package com.chan.mimi.ui.screens.threads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.model.ThreadDto
import com.chan.mimi.data.model.WatchedThread
import com.chan.mimi.data.repository.ChanRepository
import com.chan.mimi.data.repository.SavedThreadsRepository
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import com.chan.mimi.ui.components.ChanHtmlText
import com.chan.mimi.ui.components.QuoteGreen
import com.chan.mimi.ui.components.copyTextToClipboard
import com.chan.mimi.ui.components.openExternalUrl
import com.chan.mimi.ui.components.sharePlainText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadListScreen(
    board         : BoardDto,
    onBackClick   : () -> Unit,
    onThreadClick : (ThreadDto) -> Unit,
    watchedThreads: List<WatchedThread> = emptyList(),
    onOpenWatchedThread: (WatchedThread) -> Unit = {},
    onRemoveWatchedThread: (WatchedThread) -> Unit = {},
    onToggleWatchedPolling: (WatchedThread, Boolean) -> Unit = { _, _ -> },
    onOpenSaved: () -> Unit = {},
    bottomBarPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel     : ThreadViewModel = viewModel()
) {
    // Load catalog when screen first appears
    LaunchedEffect(board.tag) {
        viewModel.loadCatalog(board.tag)
    }

    val context = LocalContext.current
    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing  by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val sortOption    by viewModel.sortOption.collectAsStateWithLifecycle()
    var searchQuery   by remember { mutableStateOf("") }
    var menuExpanded  by remember { mutableStateOf(false) }
    var scrollToTopRequest by remember { mutableStateOf(0) }
    var isWatchedBarExpanded by remember { mutableStateOf(false) }
    val focusManager  = LocalFocusManager.current
    val refreshTransition = rememberInfiniteTransition(label = "threadListRefresh")
    val refreshRotation by refreshTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "threadListRefreshRotation"
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ChanText(
                            text    = "/${board.tag}/",
                            variant = TextVariant.Username,
                            color   = ChanGreen
                        )
                        ChanText(
                            text    = board.title,
                            variant = TextVariant.Meta,
                            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint               = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scrollToTopRequest++
                            viewModel.refreshCatalog(board.tag)
                        },
                        enabled = !isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload threads",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (isRefreshing) refreshRotation else 0f
                            }
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        com.chan.mimi.ui.screens.threads.ThreadSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("Sort by ${option.label}") },
                                leadingIcon = {
                                    if (sortOption == option) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    viewModel.setSortOption(option)
                                    menuExpanded = false
                                    scrollToTopRequest++
                                }
                            )
                        }
                    }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        when (uiState) {

            // ── Loading ───────────────────────────────────────
            is ThreadUiState.Loading -> {
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ChanGreen)
                }
            }

            // ── Error ─────────────────────────────────────────
            is ThreadUiState.Error -> {
                val message = (uiState as ThreadUiState.Error).message
                Box(
                    modifier         = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ChanText(
                            text    = "Failed to load threads",
                            variant = TextVariant.Body
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ChanText(
                            text    = message,
                            variant = TextVariant.Meta
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ChanButton(
                            text    = "RETRY",
                            onClick = { viewModel.refreshCatalog(board.tag) }
                        )
                    }
                }
            }

            // ── Success ───────────────────────────────────────
            is ThreadUiState.Success -> {
                val threads = (uiState as ThreadUiState.Success).threads
                val boardWatchedThreads = remember(watchedThreads, board.tag) {
                    watchedThreads
                        .filter { it.boardTag == board.tag }
                        .sortedByDescending { it.addedAt }
                }
                val displayedThreads = remember(searchQuery, threads) {
                    if (searchQuery.isEmpty()) threads
                    else threads.filter {
                        it.safeSubject().contains(searchQuery, ignoreCase = true) ||
                        it.safeComment().contains(searchQuery, ignoreCase = true)
                    }
                }
                val sortedThreads = remember(displayedThreads, sortOption) {
                    when (sortOption) {
                        com.chan.mimi.ui.screens.threads.ThreadSortOption.REPLY_COUNT -> displayedThreads.sortedByDescending { it.safeReplyCount() }
                        com.chan.mimi.ui.screens.threads.ThreadSortOption.NEWEST -> displayedThreads.sortedByDescending { it.unixTime ?: 0L }
                        com.chan.mimi.ui.screens.threads.ThreadSortOption.OLDEST -> displayedThreads.sortedBy { it.unixTime ?: Long.MAX_VALUE }
                        com.chan.mimi.ui.screens.threads.ThreadSortOption.SUBJECT -> displayedThreads.sortedBy { it.safeSubject().ifBlank { it.safeName() }.lowercase() }
                        com.chan.mimi.ui.screens.threads.ThreadSortOption.IMAGE_COUNT -> displayedThreads.sortedByDescending { it.safeImageCount() }
                    }
                }
                val imageThreads = remember(sortedThreads) {
                    sortedThreads.filter { it.imageId != null && it.imageExt != null }
                }
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
                var activeIndex by remember { mutableStateOf(0) }
                val watchedBarHeight = 56.dp
                val watchedBarBottomPadding = bottomBarPadding
                val savedThreadsRepository = remember(context) {
                    SavedThreadsRepository.getInstance(context.applicationContext)
                }

                LaunchedEffect(scrollToTopRequest) {
                    if (scrollToTopRequest > 0) {
                        listState.scrollToItem(0)
                    }
                }

                // Hide keyboard when user starts scrolling
                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) focusManager.clearFocus()
                }

                // Synchronize background scrolling with active index of viewer
                LaunchedEffect(viewerStartIndex, activeIndex) {
                    if (viewerStartIndex != null && activeIndex >= 0 && activeIndex < imageThreads.size) {
                        val targetThread = imageThreads[activeIndex]
                        val originalIndex = sortedThreads.indexOf(targetThread)
                        if (originalIndex != -1) {
                            val lazyListIndex = originalIndex + 2 // +2 for search bar + spacer
                            val layoutInfo = listState.layoutInfo
                            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                            val itemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyListIndex }?.size ?: 0
                            val offset = if (viewportHeight > 0) {
                                (viewportHeight - itemSize) / 2
                            } else {
                                0
                            }
                            listState.animateScrollToItem(lazyListIndex, -offset)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(board.tag, boardWatchedThreads) {
                            var totalDragX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDragEnd = {
                                    when {
                                        totalDragX > 120f -> onBackClick()
                                        totalDragX < -120f -> {
                                            val latestWatched = boardWatchedThreads.firstOrNull()
                                            if (latestWatched != null) {
                                                onOpenWatchedThread(latestWatched)
                                            } else {
                                                onOpenSaved()
                                            }
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    totalDragX += dragAmount
                                    change.consume()
                                }
                            )
                        }
                ) {
                    if (isRefreshing) {
                        LinearProgressIndicator(
                            color = ChanGreen,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(innerPadding)
                        )
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 8.dp),
                        contentPadding = PaddingValues(bottom = watchedBarHeight + watchedBarBottomPadding + 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── Sticky search bar ──────────────────────────────
                        stickyHeader(key = "search_bar") {
                            // Background covers list content sliding underneath
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(bottom = 4.dp)
                            ) {
                                TextField(
                                    value         = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder   = {
                                        Text(
                                            "Search threads…",
                                            fontSize = 13.sp,
                                            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    Icons.Default.Clear,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(16.dp),
                                                    tint     = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier   = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape  = MaterialTheme.shapes.extraLarge,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor   = ElevatedDark,
                                        unfocusedContainerColor = ElevatedDark,
                                        focusedIndicatorColor   = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor             = ChanGreen,
                                        focusedTextColor        = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor      = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(4.dp)) }

                        items(sortedThreads, key = { it.id }) { thread ->
                            ThreadCard(
                                thread        = thread,
                                boardTag      = board.tag,
                                onThreadClick = onThreadClick,
                                onSaveThread  = { threadToSave ->
                                    scope.launch {
                                        Toast.makeText(context, "Saving thread offline...", Toast.LENGTH_SHORT).show()
                                        try {
                                            val result = ChanRepository.getThread(board.tag, threadToSave.id, forceRefresh = true)
                                            result.fold(
                                                onSuccess = { posts ->
                                                    savedThreadsRepository.saveThread(board.tag, threadToSave, posts)
                                                    Toast.makeText(context, "Thread saved offline!", Toast.LENGTH_SHORT).show()
                                                },
                                                onFailure = { error ->
                                                    Toast.makeText(
                                                        context,
                                                        "Failed to load thread: ${error.message ?: "Unknown error"}",
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                }
                                            )
                                        } catch (e: Exception) {
                                            Toast.makeText(
                                                context,
                                                "Failed to save thread: ${e.message ?: "Unknown error"}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onImageClick  = { clickedThread ->
                                    val index = imageThreads.indexOf(clickedThread)
                                    if (index != -1) {
                                        activeIndex = index
                                        viewerStartIndex = index
                                    }
                                }
                            )
                        }

                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }

                    // Fullscreen Image Viewer Overlay
                    if (viewerStartIndex != null) {
                        val viewerItems = remember(imageThreads, board.tag) {
                            imageThreads.map { thread ->
                                val url = "https://i.4cdn.org/${board.tag}/${thread.imageId}${thread.imageExt}"
                                val ext = thread.imageExt?.removePrefix(".")?.uppercase() ?: ""
                                ImageViewerItem(
                                    imageUrl    = url,
                                    fileUrl     = url,
                                    filename    = "${thread.imageId}${thread.imageExt}",
                                    fileInfo    = if (ext.isNotEmpty()) ext else "",
                                    postUrl     = "https://boards.4chan.org/${board.tag}/thread/${thread.id}",
                                    username    = thread.safeName(),
                                    postId      = thread.id.toString(),
                                    subject     = thread.safeSubject(),
                                    commentHtml = thread.safeComment(),
                                    replyCount  = thread.safeReplyCount(),
                                    imageCount  = thread.safeImageCount()
                                )
                            }
                        }
                        FullscreenImageViewer(
                            items          = viewerItems,
                            initialIndex   = viewerStartIndex!!,
                            onIndexChanged = { index -> activeIndex = index },
                            onDismiss      = { viewerStartIndex = null },
                            onOpenThread   = { item ->
                                viewerStartIndex = null
                                val threadId = item.postId.toLongOrNull()
                                val targetThread = imageThreads.find { it.id == threadId }
                                if (targetThread != null) {
                                    onThreadClick(targetThread)
                                }
                            },
                            onSwipeLeftToRight = {
                                viewerStartIndex = null
                            },
                            onSwipeRightToLeft = {
                                viewerStartIndex = null
                                val currentItem = viewerItems.getOrNull(activeIndex)
                                if (currentItem != null) {
                                    val threadId = currentItem.postId.toLongOrNull()
                                    val targetThread = imageThreads.find { it.id == threadId }
                                    if (targetThread != null) {
                                        onThreadClick(targetThread)
                                    }
                                }
                            }
                        )
                    }

                    WatchedThreadsBar(
                        threads = watchedThreads,
                        activeThreadNo = -1L,
                        boardTag = board.tag,
                        isExpanded = isWatchedBarExpanded,
                        onToggleExpand = { isWatchedBarExpanded = !isWatchedBarExpanded },
                        onSwitchThread = { watchedThread ->
                            onOpenWatchedThread(watchedThread)
                        },
                        onRemove = { watchedThread ->
                            onRemoveWatchedThread(watchedThread)
                        },
                        onTogglePolling = { watchedThread, enabled ->
                            onToggleWatchedPolling(watchedThread, enabled)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = watchedBarBottomPadding)
                    )
                }
            }
        }
    }
}

// ============================================================
// THREAD CARD
// ============================================================

@Composable
fun ThreadCard(
    thread        : ThreadDto,
    boardTag      : String,
    onThreadClick : (ThreadDto) -> Unit,
    onSaveThread  : (ThreadDto) -> Unit,
    onImageClick  : (ThreadDto) -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    val threadUrl = "https://boards.4chan.org/$boardTag/thread/${thread.id}"
    val cleanComment = remember(thread.comment) {
        val raw = thread.safeComment()
        if (raw.isEmpty()) ""
        else Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString().trim()
    }

    // Build thumbnail URL if thread has an image
    val imageUrl = remember(thread.imageId) {
        if (thread.imageId != null) {
            "https://t.4cdn.org/$boardTag/${thread.imageId}s.jpg"
        } else null
    }

    ChanCard(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 168.dp),
        onClick  = { onThreadClick(thread) }
    ) {
        // Row 1 — Username + Post ID + Three dots
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(
                    text    = thread.safeName(),
                    variant = TextVariant.Username,
                    color   = ChanGreen
                )
                Spacer(modifier = Modifier.width(8.dp))
                ChanText(
                    text    = thread.id.toString(),
                    variant = TextVariant.Meta,
                    color   = TextLink
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Thread options",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy Thread Link") },
                        onClick = {
                            copyTextToClipboard(context, "Thread link", threadUrl)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy Text") },
                        onClick = {
                            val textToCopy = if (thread.safeSubject().isNotEmpty()) {
                                "${thread.safeSubject()}\n\n$cleanComment"
                            } else {
                                cleanComment
                            }
                            copyTextToClipboard(context, "Thread text", textToCopy.ifEmpty { threadUrl })
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open Thread Link") },
                        onClick = {
                            openExternalUrl(context, threadUrl)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share Thread") },
                        onClick = {
                            sharePlainText(context, threadUrl)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Saved") },
                        onClick = {
                            onSaveThread(thread)
                            menuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Row 2 — Image + Message
        Row {
            if (imageUrl != null) {
                val isVideo = remember(thread.imageExt) {
                    thread.imageExt?.endsWith(".webm", ignoreCase = true) == true ||
                            thread.imageExt?.endsWith(".mp4", ignoreCase = true) == true
                }
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .padding(end = 12.dp)
                        .clickable { onImageClick(thread) },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model              = imageUrl,
                        contentDescription = "Thread image",
                        modifier           = Modifier.fillMaxSize()
                    )
                    if (isVideo) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector        = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color    = ElevatedDark,
                    shape    = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .size(104.dp)
                        .padding(end = 12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ChanText(text = "NO IMG", variant = TextVariant.Meta)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                if (thread.safeSubject().isNotEmpty()) {
                    ChanText(
                        text     = thread.safeSubject(),
                        variant  = TextVariant.Username,
                        color    = QuoteGreen,              // ← green title
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                ChanHtmlText(                               // ← replaces ChanText
                    html     = thread.safeComment(),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    onPlainTextClick = { onThreadClick(thread) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ChanDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // Row 3 — Reply count + button
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row {
                ChanText(
                    text    = "${thread.safeReplyCount()} Replies", // ← was thread.replyCount
                    variant = TextVariant.Meta
                )
                Spacer(modifier = Modifier.width(12.dp))
                ChanText(
                    text    = "${thread.safeImageCount()} Images",  // ← was thread.imageCount
                    variant = TextVariant.Meta
                )
            }
            ChanButton(
                text    = "LAST REPLIES",
                onClick = { }
            )
        }
    }
}
