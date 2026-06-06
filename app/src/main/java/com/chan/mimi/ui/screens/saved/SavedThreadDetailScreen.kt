package com.chan.mimi.ui.screens.saved

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.repository.SavedThreadsHelper
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.screens.threads.PostHighlightType
import com.chan.mimi.ui.screens.threads.PopupPostItem
import com.chan.mimi.ui.screens.threads.PostCard
import com.chan.mimi.ui.screens.threads.relativeTime
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedThreadDetailScreen(
    boardTag: String,
    threadNo: Long,
    threadTitle: String,
    onBackClick: () -> Unit,
    onOpenBoardThreadList: () -> Unit,
    highlightPostId: Long? = null,
    addedHighlightPostIds: List<Long> = emptyList(),
    deletedHighlightPostIds: List<Long> = emptyList(),
    viewModel: SavedThreadDetailViewModel = viewModel(key = "$boardTag/$threadNo")
) {
    val uiState        by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing   by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val pollCountdown  by viewModel.pollCountdown.collectAsStateWithLifecycle()
    val is404          by viewModel.is404.collectAsStateWithLifecycle()

    val context      = LocalContext.current
    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Start / stop polling with the screen lifecycle
    DisposableEffect(boardTag, threadNo) {
        viewModel.startPolling(boardTag, threadNo)
        onDispose { viewModel.stopPolling() }
    }

    // Hide keyboard when scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    var searchQuery    by remember { mutableStateOf("") }
    var replyPopup     by remember { mutableStateOf<com.chan.mimi.ui.screens.threads.ReplyPopup?>(null) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var activeIndex    by remember { mutableStateOf(0) }
    var pendingScrollPostId by remember(boardTag, threadNo, highlightPostId) {
        mutableStateOf(highlightPostId)
    }

    val detail = (uiState as? SavedThreadDetailUiState.Success)?.detail
    val posts  = detail?.posts ?: emptyList()
    val addedHighlightSet = remember(boardTag, threadNo, addedHighlightPostIds) {
        addedHighlightPostIds.toSet()
    }
    val deletedHighlightSet = remember(boardTag, threadNo, deletedHighlightPostIds) {
        deletedHighlightPostIds.toSet()
    }

    val displayedPosts = remember(searchQuery, posts) {
        if (searchQuery.isEmpty()) posts
        else posts.filter {
            it.safeComment().contains(searchQuery, ignoreCase = true) ||
                    it.safeName().contains(searchQuery, ignoreCase = true)
        }
    }

    val imagePosts = remember(displayedPosts) { displayedPosts.filter { it.hasImage() } }

    // Instant scroll sync when image viewer changes
    LaunchedEffect(viewerStartIndex, activeIndex) {
        if (viewerStartIndex != null && activeIndex >= 0 && activeIndex < imagePosts.size) {
            val targetPost    = imagePosts[activeIndex]
            val originalIndex = displayedPosts.indexOf(targetPost)
            if (originalIndex != -1) {
                listState.scrollToItem(originalIndex + 2) // search bar + spacer
            }
        }
    }

    LaunchedEffect(posts, pendingScrollPostId) {
        val targetPost = pendingScrollPostId ?: return@LaunchedEffect
        val originalIndex = displayedPosts.indexOfFirst { it.id == targetPost }
        if (originalIndex != -1) {
            val lazyListIndex = originalIndex + 2 // search bar + spacer
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val itemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyListIndex }?.size ?: 0
            val offset = if (viewportHeight > 0) (viewportHeight - itemSize) / 2 else 0
            listState.animateScrollToItem(lazyListIndex, -offset)
            pendingScrollPostId = null
        }
    }

    // Spinning refresh icon animation
    val rotationTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )
    val rotationAngle = if (isRefreshing) rotation else 0f

    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) < info.totalItemsCount - 3
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ChanText(
                            text    = "/$boardTag/ - $threadNo",
                            variant = TextVariant.Username
                        )
                        ChanText(
                            text    = if (is404) "ARCHIVED / 404" else "SAVED",
                            variant = TextVariant.Meta,
                            color   = if (is404) Color(0xFFFF4444) else ChanGreen
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    // Countdown circle
                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        val sweepAngle = (pollCountdown / 30f) * 360f
                        Canvas(Modifier.size(36.dp)) {
                            drawArc(
                                color      = ChanGreen.copy(alpha = 0.2f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter  = false,
                                style      = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color      = if (is404) Color(0xFFFF4444) else ChanGreen,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter  = false,
                                style      = Stroke(3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text       = pollCountdown.toString(),
                            color      = if (is404) Color(0xFFFF4444) else ChanGreen,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Reload button
                    IconButton(
                        onClick  = { viewModel.reloadNow() },
                        enabled  = !isRefreshing
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.graphicsLayer { rotationZ = rotationAngle }
                        )
                    }
                    // Unsave / bookmark button
                    IconButton(onClick = {
                        viewModel.unsaveThread(boardTag, threadNo)
                        Toast.makeText(context, "Thread removed from saved", Toast.LENGTH_SHORT).show()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector        = Icons.Default.Bookmark,
                            contentDescription = "Remove from saved",
                            tint               = ChanGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(viewerStartIndex) {
                    if (viewerStartIndex != null) return@pointerInput
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX > 120f) {
                                onOpenBoardThreadList()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDragX += dragAmount
                            change.consume()
                        }
                    )
                }
        ) {
            // Refreshing progress bar
            if (isRefreshing) {
                LinearProgressIndicator(
                    color    = ChanGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
                )
            }

            when (uiState) {
                is SavedThreadDetailUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ChanGreen)
                    }
                }
                is SavedThreadDetailUiState.Error -> {
                    val msg = (uiState as SavedThreadDetailUiState.Error).message
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ChanText(msg, variant = TextVariant.Body)
                    }
                }
                is SavedThreadDetailUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // 404 banner
                        if (is404) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFF4444))
                                    .padding(vertical = 8.dp, horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment     = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector        = Icons.Default.Warning,
                                        contentDescription = "404",
                                        tint               = Color.White,
                                        modifier           = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text       = "Thread archived or deleted (404) — showing saved cache",
                                        color      = Color.White,
                                        fontSize   = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            state               = listState,
                            modifier            = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            contentPadding      = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "search_bar") {
                                TextField(
                                    value         = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            "Search in saved thread",
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
                            item { Spacer(Modifier.height(4.dp)) }
                            items(displayedPosts, key = { it.id }) { post ->
                                val highlightType = when {
                                    post.id in addedHighlightSet -> PostHighlightType.ADDED
                                    post.id in deletedHighlightSet -> PostHighlightType.DELETED
                                    else -> PostHighlightType.NONE
                                }
                                PostCard(
                                    post               = post,
                                    boardTag           = boardTag,
                                    threadNo           = threadNo,
                                    allPosts           = posts,
                                    highlightType      = highlightType,
                                    allowDeletedInteractions = true,
                                    showOverflowMenu   = true,
                                    onReplyClick       = { postNo, source ->
                                        val quoted = posts.find { it.id == postNo }
                                        if (quoted != null) {
                                            replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                                quotedPost = quoted,
                                                sourcePost = source
                                            )
                                        }
                                    },
                                    onShowRepliesClick = { targetPost ->
                                        val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                        replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                            repliesToPost = targetPost,
                                            replies       = replies
                                        )
                                    },
                                    onImageClick = { clickedPost ->
                                        val index = imagePosts.indexOf(clickedPost)
                                        if (index != -1) {
                                            activeIndex      = index
                                            viewerStartIndex = index
                                        }
                                    }
                                )
                            }
                            item { Spacer(Modifier.height(8.dp)) }
                        }
                    }
                }
            }

            // ── Scroll FABs (instant) ───────────────────────────────────────
            AnimatedVisibility(
                visible  = showScrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 56.dp)
            ) {
                SmallFloatingActionButton(
                    onClick        = { scope.launch { listState.scrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape          = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
                }
            }

            AnimatedVisibility(
                visible  = showScrollToBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val last = listState.layoutInfo.totalItemsCount - 1
                            if (last >= 0) listState.scrollToItem(last)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape          = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                }
            }

            // ── Fullscreen image viewer overlay ────────────────────────────
            if (viewerStartIndex != null && detail != null) {
                val viewerItems = remember(imagePosts, posts, boardTag, threadNo) {
                    val sdf = java.text.SimpleDateFormat("EEEE yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    imagePosts.map { post ->
                        val localFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, post.id, post.imageExt ?: "")
                        val fileUrl   = if (localFile.exists()) Uri.fromFile(localFile).toString()
                                        else post.imageUrl(boardTag) ?: ""
                        val ext       = post.imageExt?.removePrefix(".")?.uppercase() ?: ""
                        val localThumb = SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, post.id)
                        val thumbUrl = if (localThumb.exists()) {
                            Uri.fromFile(localThumb).toString()
                        } else if (localFile.exists() && post.imageExt?.endsWith(".webm", ignoreCase = true) == false && post.imageExt?.endsWith(".mp4", ignoreCase = true) == false) {
                            Uri.fromFile(localFile).toString()
                        } else {
                            "https://t.4cdn.org/$boardTag/${post.imageId}s.jpg"
                        }

                        val itemReplies = posts.filter { p -> p.repliesTo(post.id) }.map { r ->
                            val rLocalFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, r.id, r.imageExt ?: "")
                            val rFileUrl   = if (rLocalFile.exists()) Uri.fromFile(rLocalFile).toString() else r.imageUrl(boardTag)
                            val rExt       = r.imageExt?.removePrefix(".")?.uppercase() ?: ""
                            val rThumbUrl = if (r.hasImage()) {
                                val rLocalThumb = SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, r.id)
                                if (rLocalThumb.exists()) {
                                    Uri.fromFile(rLocalThumb).toString()
                                } else if (rLocalFile.exists() && r.imageExt?.endsWith(".webm", ignoreCase = true) == false && r.imageExt?.endsWith(".mp4", ignoreCase = true) == false) {
                                    Uri.fromFile(rLocalFile).toString()
                                } else {
                                    "https://t.4cdn.org/$boardTag/${r.imageId}s.jpg"
                                }
                            } else {
                                ""
                            }
                            ImageViewerItem(
                                imageUrl     = rFileUrl ?: "",
                                fileUrl      = rFileUrl ?: "",
                                filename     = r.filename?.let { "$it${r.imageExt ?: ""}" } ?: "",
                                fileInfo     = if (r.hasImage()) "$rExt | ${r.fileSizeKb()}  ${r.imageWidth}x${r.imageHeight}" else "",
                                postUrl      = "https://boards.4chan.org/$boardTag/thread/$threadNo#p${r.id}",
                                username     = r.safeName(),
                                postId       = r.id.toString(),
                                subject      = r.safeSubject(),
                                commentHtml  = r.safeComment(),
                                timeStr      = r.unixTime?.let { sdf.format(java.util.Date(it * 1000L)) } ?: "",
                                timeAgo      = r.unixTime?.let { relativeTime(it) } ?: "",
                                thumbnailUrl = rThumbUrl
                            )
                        }
                        ImageViewerItem(
                            imageUrl     = fileUrl,
                            fileUrl      = fileUrl,
                            filename     = "${post.filename ?: post.imageId}${post.imageExt ?: ""}",
                            fileInfo     = "$ext | ${post.fileSizeKb()}  ${post.imageWidth}x${post.imageHeight}",
                            postUrl      = "https://boards.4chan.org/$boardTag/thread/$threadNo#p${post.id}",
                            username     = post.safeName(),
                            postId       = post.id.toString(),
                            subject      = post.safeSubject(),
                            commentHtml  = post.safeComment(),
                            timeStr      = post.unixTime?.let { sdf.format(java.util.Date(it * 1000L)) } ?: "",
                            timeAgo      = post.unixTime?.let { relativeTime(it) } ?: "",
                            replyCount   = itemReplies.size,
                            replies      = itemReplies,
                            thumbnailUrl = thumbUrl
                        )
                    }
                }
                FullscreenImageViewer(
                    items          = viewerItems,
                    initialIndex   = viewerStartIndex!!,
                    onIndexChanged = { index -> activeIndex = index },
                    onDismiss      = { viewerStartIndex = null },
                    onSwipeLeftToRight = {
                        viewerStartIndex = null
                        onBackClick()
                    }
                )
            }
        }
    }

    // ── Reply popup ────────────────────────────────────────────────────────
    replyPopup?.let { popup ->
        AlertDialog(
            onDismissRequest = { replyPopup = null },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    val titlePost = popup.quotedPost ?: popup.repliesToPost
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (titlePost != null) {
                            ChanText(text = ">>${titlePost.id}", variant = TextVariant.Meta, color = TextLink)
                            if (popup.replies.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                ChanText(
                                    text    = "${popup.replies.size} ${if (popup.replies.size == 1) "reply" else "replies"}",
                                    variant = TextVariant.Meta,
                                    color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { replyPopup = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (popup.quotedPost != null && popup.sourcePost != null) {
                        PopupPostItem(
                            post               = popup.quotedPost,
                            boardTag           = boardTag,
                            threadNo           = threadNo,
                            allPosts           = posts,
                            allowDeletedInteractions = true,
                            onReplyClick       = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(quotedPost = found, sourcePost = source)
                            },
                            onShowRepliesClick = { targetPost ->
                                replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                    repliesToPost = targetPost,
                                    replies       = posts.filter { p -> p.repliesTo(targetPost.id) }
                                )
                            },
                            onImageClick = { clickedPost ->
                                val index = imagePosts.indexOf(clickedPost)
                                if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                            }
                        )
                        Row(
                            modifier          = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            ChanText("  replied by  ", variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        }
                        PopupPostItem(
                            post               = popup.sourcePost,
                            boardTag           = boardTag,
                            threadNo           = threadNo,
                            allPosts           = posts,
                            allowDeletedInteractions = true,
                            onReplyClick       = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(quotedPost = found, sourcePost = source)
                            },
                            onShowRepliesClick = { targetPost ->
                                replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                    repliesToPost = targetPost,
                                    replies       = posts.filter { p -> p.repliesTo(targetPost.id) }
                                )
                            },
                            onImageClick = { clickedPost ->
                                val index = imagePosts.indexOf(clickedPost)
                                if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                            }
                        )
                    } else {
                        val topPost = popup.quotedPost ?: popup.repliesToPost
                        topPost?.let { post ->
                            PopupPostItem(
                                post               = post,
                                boardTag           = boardTag,
                                threadNo           = threadNo,
                                allPosts           = posts,
                                allowDeletedInteractions = true,
                                onReplyClick       = { postNo, source ->
                                    val found = posts.find { it.id == postNo }
                                    if (found != null) replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(quotedPost = found, sourcePost = source)
                                },
                                onShowRepliesClick = { targetPost ->
                                    replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                        repliesToPost = targetPost,
                                        replies       = posts.filter { p -> p.repliesTo(targetPost.id) }
                                    )
                                },
                                onImageClick = { clickedPost ->
                                    val index = imagePosts.indexOf(clickedPost)
                                    if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                                }
                            )
                        }
                        if (popup.replies.isNotEmpty()) {
                            Row(
                                modifier          = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                ChanText("  replies  ", variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            }
                            popup.replies.forEachIndexed { i, post ->
                                PopupPostItem(
                                    post               = post,
                                    boardTag           = boardTag,
                                    threadNo           = threadNo,
                                    allPosts           = posts,
                                    allowDeletedInteractions = true,
                                    onReplyClick       = { postNo, source ->
                                        val found = posts.find { it.id == postNo }
                                        if (found != null) replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(quotedPost = found, sourcePost = source)
                                    },
                                    onShowRepliesClick = { targetPost ->
                                        replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                            repliesToPost = targetPost,
                                            replies       = posts.filter { p -> p.repliesTo(targetPost.id) }
                                        )
                                    },
                                    onImageClick = { clickedPost ->
                                        val index = imagePosts.indexOf(clickedPost)
                                        if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                                    }
                                )
                                if (i < popup.replies.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
