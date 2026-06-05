// FILE: ui/screens/threads/ThreadListScreen.kt
package com.chan.mimi.ui.screens.threads

import android.text.Html
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.data.model.ThreadDto
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import com.chan.mimi.ui.components.ChanHtmlText
import com.chan.mimi.ui.components.QuoteGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ThreadListScreen(
    board         : BoardDto,
    onBackClick   : () -> Unit,
    onThreadClick : (ThreadDto) -> Unit,
    viewModel     : ThreadViewModel = viewModel()
) {
    // Load catalog when screen first appears
    LaunchedEffect(board.tag) {
        viewModel.loadCatalog(board.tag)
    }

    val uiState       by viewModel.uiState.collectAsStateWithLifecycle()
    var searchQuery   by remember { mutableStateOf("") }
    val focusManager  = LocalFocusManager.current

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
                            onClick = { viewModel.loadCatalog(board.tag) }
                        )
                    }
                }
            }

            // ── Success ───────────────────────────────────────
            is ThreadUiState.Success -> {
                val threads = (uiState as ThreadUiState.Success).threads
                val displayedThreads = remember(searchQuery, threads) {
                    if (searchQuery.isEmpty()) threads
                    else threads.filter {
                        it.safeSubject().contains(searchQuery, ignoreCase = true) ||
                        it.safeComment().contains(searchQuery, ignoreCase = true)
                    }
                }
                val imageThreads = remember(displayedThreads) {
                    displayedThreads.filter { it.imageId != null && it.imageExt != null }
                }
                val listState = rememberLazyListState()
                val scope = rememberCoroutineScope()
                var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
                var activeIndex by remember { mutableStateOf(0) }

                // Hide keyboard when user starts scrolling
                LaunchedEffect(listState.isScrollInProgress) {
                    if (listState.isScrollInProgress) focusManager.clearFocus()
                }

                // Synchronize background scrolling with active index of viewer
                LaunchedEffect(viewerStartIndex, activeIndex) {
                    if (viewerStartIndex != null && activeIndex >= 0 && activeIndex < imageThreads.size) {
                        val targetThread = imageThreads[activeIndex]
                        val originalIndex = displayedThreads.indexOf(targetThread)
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

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = 8.dp),
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

                        items(displayedThreads) { thread ->
                            ThreadCard(
                                thread        = thread,
                                boardTag      = board.tag,
                                onThreadClick = onThreadClick,
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
    onImageClick  : (ThreadDto) -> Unit
) {
    // Strip HTML tags from comment — 4chan returns HTML in comments
    val cleanComment = remember(thread.comment) {
        val raw = thread.safeComment()
        if (raw.isEmpty()) ""
        else Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    // Build thumbnail URL if thread has an image
    val imageUrl = remember(thread.imageId) {
        if (thread.imageId != null) {
            "https://t.4cdn.org/$boardTag/${thread.imageId}s.jpg"
        } else null
    }

    ChanCard(
        modifier = Modifier.fillMaxWidth(),
        onClick  = { onThreadClick(thread) }
    ) {
        // Row 1 — Username + Post ID
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChanText(
                text    = thread.safeName(),        // ← was thread.name
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

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2 — Image + Message
        Row {
            if (imageUrl != null) {
                val isVideo = remember(thread.imageExt) {
                    thread.imageExt?.endsWith(".webm", ignoreCase = true) == true ||
                            thread.imageExt?.endsWith(".mp4", ignoreCase = true) == true
                }
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(end = 10.dp)
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
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector        = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(16.dp)
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
                        .size(80.dp)
                        .padding(end = 10.dp)
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                ChanHtmlText(                               // ← replaces ChanText
                    html     = thread.safeComment(),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        ChanDivider()
        Spacer(modifier = Modifier.height(8.dp))

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