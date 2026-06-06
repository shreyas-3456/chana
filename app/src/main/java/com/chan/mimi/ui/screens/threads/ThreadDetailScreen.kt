package com.chan.mimi.ui.screens.threads

import android.text.Html
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
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
import coil.compose.AsyncImage
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.repository.SavedThreadsHelper
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.components.copyTextToClipboard
import com.chan.mimi.ui.components.openExternalUrl
import com.chan.mimi.ui.components.sharePlainText
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.animation.core.animateFloat
import android.net.Uri


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    boardTag    : String,
    threadNo    : Long,
    threadTitle : String,
    onBackClick : () -> Unit,
    onSwitchThread: (String, Long, String) -> Unit,
    onOpenBoardThreadList: () -> Unit = {},
    onOpenSaved: () -> Unit,
    watchedThreadsViewModel: WatchedThreadsViewModel,
    bottomBarPadding: androidx.compose.ui.unit.Dp = 0.dp,
    viewModel   : ThreadDetailViewModel = viewModel(key = "$boardTag/$threadNo")
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaved     by viewModel.isSaved.collectAsStateWithLifecycle()
    val isSaving    by viewModel.isSaving.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hasNewPosts by viewModel.hasNewPosts.collectAsStateWithLifecycle()
    val pollCountdown by viewModel.pollCountdown.collectAsStateWithLifecycle()
    val watchedThreads by watchedThreadsViewModel.allWatchedThreads.collectAsStateWithLifecycle()
    val context     = LocalContext.current
    var isWatchedBarExpanded by remember { mutableStateOf(false) }
    val floatingButtonBaseBottom = 56.dp + bottomBarPadding + 8.dp

    val rotationTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rotation")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotationAngle"
    )
    val rotationAngle = if (isRefreshing) rotation else 0f
    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var replyPopup by remember { mutableStateOf<ReplyPopup?>(null) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var activeIndex by remember { mutableIntStateOf(0) }

    val posts = (uiState as? ThreadDetailUiState.Success)?.posts ?: emptyList<PostDto>()
    val displayedPosts = remember(searchQuery, posts) {
        if (searchQuery.isEmpty()) posts
        else posts.filter {
            it.safeComment().contains(searchQuery, ignoreCase = true) ||
                    it.safeName().contains(searchQuery, ignoreCase = true)
        }
    }
    val imagePosts = remember(displayedPosts) {
        displayedPosts.filter { it.hasImage() }
    }
    val boardWatchedThreads = remember(watchedThreads, boardTag) {
        watchedThreads
            .filter { it.boardTag == boardTag }
            .sortedByDescending { it.addedAt }
    }
    val currentWatchedIndex = remember(boardWatchedThreads, threadNo) {
        boardWatchedThreads.indexOfFirst { it.threadNo == threadNo }
    }

    LaunchedEffect(viewerStartIndex, activeIndex) {
        if (viewerStartIndex != null && activeIndex >= 0 && activeIndex < imagePosts.size) {
            val targetPost = imagePosts[activeIndex]
            val originalIndex = displayedPosts.indexOf(targetPost)
            if (originalIndex != -1) {
                val lazyListIndex = originalIndex + 2
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val itemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyListIndex }?.size ?: 0
                val offset = if (viewportHeight > 0) (viewportHeight - itemSize) / 2 else 0
                listState.animateScrollToItem(lazyListIndex, -offset)
            }
        }
    }

    DisposableEffect(boardTag, threadNo) {
        viewModel.startPolling(boardTag, threadNo)
        onDispose { viewModel.stopPolling() }
    }

    LaunchedEffect(boardTag, threadNo, uiState) {
        val successPosts = (uiState as? ThreadDetailUiState.Success)?.posts
        val opPost = successPosts?.firstOrNull { it.id == threadNo } ?: successPosts?.firstOrNull()
        if (opPost != null) {
            val thumbnailUrl = if (opPost.imageId != null && opPost.imageExt != null) {
                "https://t.4cdn.org/$boardTag/${opPost.imageId}s.jpg"
            } else {
                ""
            }
            val title = opPost.safeSubject().ifEmpty { threadNo.toString() }
            watchedThreadsViewModel.addThread(
                boardTag = boardTag,
                threadNo = threadNo,
                title = title,
                thumbnailUrl = thumbnailUrl,
                postCount = successPosts?.size ?: 0
            )
        }
        watchedThreadsViewModel.markSeen(boardTag, threadNo)
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    val showScrollToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }
    val showScrollToBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible < info.totalItemsCount - 3
        }
    }

    var menuExpanded by remember { mutableStateOf(false) }
    val threadUrl = "https://boards.4chan.org/$boardTag/thread/$threadNo"

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    ChanText(
                        text    = "/$boardTag/ - $threadNo",
                        variant = TextVariant.Username
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                        val sweepAngle = (pollCountdown / 30f) * 360f
                        Canvas(modifier = Modifier.size(36.dp)) {
                            drawArc(
                                color      = ChanGreen.copy(alpha = 0.2f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter  = false,
                                style      = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color      = ChanGreen,
                                startAngle = -90f,
                                sweepAngle = sweepAngle,
                                useCenter  = false,
                                style      = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text       = pollCountdown.toString(),
                            color      = ChanGreen,
                            fontSize   = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = { viewModel.reloadNow() },
                        enabled = !isRefreshing && !isSaving
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Refresh,
                            contentDescription = "Reload thread",
                            tint               = MaterialTheme.colorScheme.onBackground,
                            modifier           = Modifier.graphicsLayer { rotationZ = rotationAngle }
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleSave() },
                        enabled = !isRefreshing && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = ChanGreen,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector        = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Save thread",
                                tint               = if (isSaved) ChanGreen else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        DropdownMenu(
                            expanded         = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier         = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                text        = { Text("Scroll to top") },
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, null) },
                                onClick     = {
                                    menuExpanded = false
                                    scope.launch { listState.animateScrollToItem(0) }
                                }
                            )
                            DropdownMenuItem(
                                text        = { Text("Scroll to bottom") },
                                leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, null) },
                                onClick     = {
                                    menuExpanded = false
                                    scope.launch {
                                        val last = listState.layoutInfo.totalItemsCount - 1
                                        if (last >= 0) listState.animateScrollToItem(last)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text        = { Text("Open in Browser") },
                                leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                                onClick     = {
                                    menuExpanded = false
                                    openExternalUrl(context, threadUrl)
                                }
                            )
                            DropdownMenuItem(
                                text        = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick     = {
                                    menuExpanded = false
                                    sharePlainText(context, threadUrl)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
                    .pointerInput(viewerStartIndex) {
                        if (viewerStartIndex != null) return@pointerInput
                        var totalDragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDragX = 0f },
                            onDragEnd = {
                                when {
                                    totalDragX > 120f -> onBackClick()
                                    totalDragX < -120f -> {
                                        val nextWatched = boardWatchedThreads.getOrNull(currentWatchedIndex + 1)
                                        if (nextWatched != null) {
                                            onSwitchThread(nextWatched.boardTag, nextWatched.threadNo, nextWatched.title)
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
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).zIndex(1f)
                    )
                }

                when (uiState) {
                    is ThreadDetailUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ChanGreen)
                        }
                    }
                    is ThreadDetailUiState.Error -> {
                        val msg = (uiState as ThreadDetailUiState.Error).message
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ChanText("Failed to load thread", variant = TextVariant.Body)
                                Spacer(Modifier.height(8.dp))
                                ChanText(msg, variant = TextVariant.Meta)
                                Spacer(Modifier.height(16.dp))
                                ChanButton(text = "RETRY", onClick = { viewModel.startPolling(boardTag, threadNo) })
                            }
                        }
                    }
                    is ThreadDetailUiState.Success -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            contentPadding = PaddingValues(bottom = floatingButtonBaseBottom + 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(key = "search_bar") {
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search in thread", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onBackground)
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = ElevatedDark,
                                        unfocusedContainerColor = ElevatedDark,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = ChanGreen,
                                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                                    )
                                )
                            }
                            item { Spacer(Modifier.height(4.dp)) }
                            items(displayedPosts, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    boardTag = boardTag,
                                    threadNo = threadNo,
                                    allPosts = posts,
                                    showOverflowMenu = true,
                                    onReplyClick = { postNo, source ->
                                        val quoted = posts.find { it.id == postNo }
                                        if (quoted != null) {
                                            replyPopup = ReplyPopup(quotedPost = quoted, sourcePost = source)
                                        }
                                    },
                                    onShowRepliesClick = { targetPost ->
                                        val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                        replyPopup = ReplyPopup(repliesToPost = targetPost, replies = replies)
                                    },
                                    onImageClick = { clickedPost ->
                                        val index = imagePosts.indexOf(clickedPost)
                                        if (index != -1) {
                                            activeIndex = index
                                            viewerStartIndex = index
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = hasNewPosts,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = floatingButtonBaseBottom + 96.dp)
                ) {
                    BadgedBox(
                        badge = { Badge(containerColor = Color(0xFFFF4444)) { Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
                    ) {
                        FloatingActionButton(
                            onClick = { viewModel.applyNewPosts(boardTag, threadNo) },
                            containerColor = Color(0xFFFFAA00),
                            contentColor = Color.White,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Campaign, "New posts", modifier = Modifier.size(24.dp))
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = floatingButtonBaseBottom + 48.dp)
                ) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, "Scroll to top", modifier = Modifier.size(18.dp))
                    }
                }

                AnimatedVisibility(
                    visible = showScrollToBottom,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = floatingButtonBaseBottom)
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            scope.launch {
                                val last = listState.layoutInfo.totalItemsCount - 1
                                if (last >= 0) listState.animateScrollToItem(last)
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, "Scroll to bottom", modifier = Modifier.size(18.dp))
                    }
                }

                if (viewerStartIndex != null) {
                    val viewerItems = remember(imagePosts, posts, boardTag, threadNo) {
                        val sdf = SimpleDateFormat("EEEE yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                        imagePosts.map { post ->
                            val localFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, post.id, post.imageExt ?: "")
                            val fileUrl = if (localFile.exists()) Uri.fromFile(localFile).toString() else post.imageUrl(boardTag)!!
                            val ext = post.imageExt?.removePrefix(".")?.uppercase() ?: ""
                            val itemReplies = posts.filter { p -> p.repliesTo(post.id) }.map { r ->
                                val localReplyFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, r.id, r.imageExt ?: "")
                                val rFileUrl = if (localReplyFile.exists()) Uri.fromFile(localReplyFile).toString() else r.imageUrl(boardTag)
                                val rExt = r.imageExt?.removePrefix(".")?.uppercase() ?: ""
                                ImageViewerItem(
                                    imageUrl = rFileUrl ?: "",
                                    fileUrl = rFileUrl ?: "",
                                    filename = r.filename?.let { "${it}${r.imageExt ?: ""}" } ?: "",
                                    fileInfo = if (r.hasImage()) "$rExt | ${r.fileSizeKb()}  ${r.imageWidth}x${r.imageHeight}" else "",
                                    postUrl = "https://boards.4chan.org/$boardTag/thread/$threadNo#p${r.id}",
                                    username = r.safeName(),
                                    postId = r.id.toString(),
                                    subject = r.safeSubject(),
                                    commentHtml = r.safeComment(),
                                    timeStr = r.unixTime?.let { sdf.format(Date(it * 1000L)) } ?: "",
                                    timeAgo = r.unixTime?.let { relativeTime(it) } ?: ""
                                )
                            }
                            ImageViewerItem(
                                imageUrl = fileUrl,
                                fileUrl = fileUrl,
                                filename = "${post.filename ?: post.imageId}${post.imageExt ?: ""}",
                                fileInfo = "$ext | ${post.fileSizeKb()}  ${post.imageWidth}x${post.imageHeight}",
                                postUrl = "https://boards.4chan.org/$boardTag/thread/$threadNo#p${post.id}",
                                username = post.safeName(),
                                postId = post.id.toString(),
                                subject = post.safeSubject(),
                                commentHtml = post.safeComment(),
                                timeStr = post.unixTime?.let { sdf.format(Date(it * 1000L)) } ?: "",
                                timeAgo = post.unixTime?.let { relativeTime(it) } ?: "",
                                replyCount = itemReplies.size,
                                replies = itemReplies
                            )
                        }
                    }
                    FullscreenImageViewer(
                        items = viewerItems,
                        initialIndex = viewerStartIndex!!,
                        onIndexChanged = { index -> activeIndex = index },
                        onDismiss = { viewerStartIndex = null },
                        onSwipeLeftToRight = { viewerStartIndex = null; onBackClick() }
                    )
                }
            }

            WatchedThreadsBar(
                threads = watchedThreads,
                activeThreadNo = threadNo,
                boardTag = boardTag,
                isExpanded = isWatchedBarExpanded,
                onToggleExpand = { isWatchedBarExpanded = !isWatchedBarExpanded },
                onSwitchThread = { watchedThread -> onSwitchThread(watchedThread.boardTag, watchedThread.threadNo, watchedThread.title) },
                onRemove = { watchedThread -> watchedThreadsViewModel.removeThread(watchedThread.boardTag, watchedThread.threadNo) },
                onTogglePolling = { watchedThread, enabled -> watchedThreadsViewModel.togglePolling(watchedThread.boardTag, watchedThread.threadNo, enabled) },
                modifier = Modifier.align(Alignment.BottomCenter).zIndex(2f).padding(bottom = bottomBarPadding)
            )
        }
    }

    replyPopup?.let { popup ->
        AlertDialog(
            onDismissRequest = { replyPopup = null },
            containerColor   = MaterialTheme.colorScheme.surface,
            title = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    val titlePost = popup.quotedPost ?: popup.repliesToPost
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (titlePost != null) {
                            ChanText(text = ">>${titlePost.id}", variant = TextVariant.Meta, color = TextLink)
                            if (popup.replies.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                ChanText(text = "${popup.replies.size} ${if (popup.replies.size == 1) "reply" else "replies"}", variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    }
                    IconButton(onClick = { replyPopup = null }) {
                        Icon(Icons.Default.Close, "Close", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (popup.quotedPost != null && popup.sourcePost != null) {
                        PopupPostItem(
                            post         = popup.quotedPost,
                            boardTag     = boardTag,
                            threadNo     = threadNo,
                            allPosts     = posts,
                            onReplyClick = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) replyPopup = ReplyPopup(quotedPost = found, sourcePost = source)
                            },
                            onShowRepliesClick = { targetPost ->
                                replyPopup = ReplyPopup(repliesToPost = targetPost, replies = posts.filter { p -> p.repliesTo(targetPost.id) })
                            },
                            onImageClick = { clickedPost ->
                                val index = imagePosts.indexOf(clickedPost)
                                if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                            }
                        )
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            ChanText(text = "  replied by  ", variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        }
                        PopupPostItem(
                            post         = popup.sourcePost,
                            boardTag     = boardTag,
                            threadNo     = threadNo,
                            allPosts     = posts,
                            onReplyClick = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) replyPopup = ReplyPopup(quotedPost = found, sourcePost = source)
                            },
                            onShowRepliesClick = { targetPost ->
                                replyPopup = ReplyPopup(repliesToPost = targetPost, replies = posts.filter { p -> p.repliesTo(targetPost.id) })
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
                                post         = post,
                                boardTag     = boardTag,
                                threadNo     = threadNo,
                                allPosts     = posts,
                                onReplyClick = { postNo, source ->
                                    val found = posts.find { it.id == postNo }
                                    if (found != null) replyPopup = ReplyPopup(quotedPost = found, sourcePost = source)
                                },
                                onShowRepliesClick = { targetPost ->
                                    replyPopup = ReplyPopup(repliesToPost = targetPost, replies = posts.filter { p -> p.repliesTo(targetPost.id) })
                                },
                                onImageClick = { clickedPost ->
                                    val index = imagePosts.indexOf(clickedPost)
                                    if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                                }
                            )
                        }
                        if (popup.replies.isNotEmpty()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                ChanText(text = "  replies  ", variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            }
                            popup.replies.forEachIndexed { i, post ->
                                PopupPostItem(
                                    post         = post,
                                    boardTag     = boardTag,
                                    threadNo     = threadNo,
                                    allPosts     = posts,
                                    onReplyClick = { postNo, source ->
                                        val found = posts.find { it.id == postNo }
                                        if (found != null) replyPopup = ReplyPopup(quotedPost = found, sourcePost = source)
                                    },
                                    onShowRepliesClick = { targetPost ->
                                        replyPopup = ReplyPopup(repliesToPost = targetPost, replies = posts.filter { p -> p.repliesTo(targetPost.id) } )
                                    },
                                    onImageClick = { clickedPost ->
                                        val index = imagePosts.indexOf(clickedPost)
                                        if (index != -1) { activeIndex = index; viewerStartIndex = index; replyPopup = null }
                                    }
                                )
                                if (i < popup.replies.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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

@Composable
fun PostCard(
    post               : PostDto,
    boardTag           : String,
    threadNo           : Long = 0L,
    allPosts           : List<PostDto>  = emptyList(),
    highlightType      : PostHighlightType = PostHighlightType.NONE,
    allowDeletedInteractions: Boolean = false,
    showOverflowMenu  : Boolean = false,
    onReplyClick       : (Long, PostDto) -> Unit = { _, _ -> },
    onShowRepliesClick : (PostDto) -> Unit = {},
    onImageClick       : (PostDto) -> Unit = {}
) {
    val context = LocalContext.current
    val imageUrl = remember(post.imageId, boardTag, threadNo) {
        if (post.hasImage()) {
            val localThumb = SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, post.id)
            if (localThumb.exists()) localThumb.absolutePath else "https://t.4cdn.org/$boardTag/${post.imageId}s.jpg"
        } else null
    }
    val dateStr  = remember(post.unixTime) {
        post.unixTime?.let { SimpleDateFormat("EEEE yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it * 1000L)) } ?: ""
    }
    val timeAgo = remember(post.unixTime) { post.unixTime?.let { relativeTime(it) } ?: "" }
    val postUrl = remember(boardTag, threadNo, post.id) { "https://boards.4chan.org/$boardTag/thread/$threadNo#p${post.id}" }
    val plainComment = remember(post.comment) { Html.fromHtml(post.safeComment(), Html.FROM_HTML_MODE_COMPACT).toString().trim() }
    var menuExpanded by remember { mutableStateOf(false) }

    val replyCount = remember(allPosts, post.id) { allPosts.count { it.repliesTo(post.id) } }

    val addedAccent = Color(0xFF7DFFA2)
    val deletedCardColor = Color(0xFF3A241F)
    val deletedAccent = Color(0xFFFF9A62)
    val cardColor = when {
        highlightType == PostHighlightType.ADDED -> Color(0xFF1F3326)
        post.isDeleted || highlightType == PostHighlightType.DELETED -> deletedCardColor
        else -> MaterialTheme.colorScheme.surface
    }

    ChanCard(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 172.dp), containerColor = cardColor) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(text = post.safeName(), variant = TextVariant.Username, color = when {
                    highlightType == PostHighlightType.ADDED -> addedAccent
                    post.isDeleted || highlightType == PostHighlightType.DELETED -> deletedAccent
                    else -> ChanGreen
                })
                Spacer(Modifier.width(8.dp))
                ChanText(text = post.id.toString(), variant = TextVariant.Meta, color = TextLink)
                if (highlightType == PostHighlightType.ADDED) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = addedAccent.copy(alpha = 0.16f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(text = "NEW", color = addedAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
                if (post.isDeleted) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = deletedAccent.copy(alpha = 0.18f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(text = "DELETED", color = deletedAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(text = timeAgo, variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.width(4.dp))
                if (showOverflowMenu) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Copy Post Link") }, onClick = { copyTextToClipboard(context, "Post link", postUrl); menuExpanded = false })
                            DropdownMenuItem(text = { Text("Copy Text") }, onClick = { copyTextToClipboard(context, "Post text", plainComment.ifEmpty { postUrl }); menuExpanded = false })
                            DropdownMenuItem(text = { Text("Open Post Link") }, onClick = { openExternalUrl(context, postUrl); menuExpanded = false })
                            DropdownMenuItem(text = { Text("Share Post") }, onClick = { sharePlainText(context, postUrl); menuExpanded = false })
                        }
                    }
                } else {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (imageUrl != null) {
            val isVideo = post.imageExt?.endsWith(".webm", ignoreCase = true) == true || post.imageExt?.endsWith(".mp4", ignoreCase = true) == true
            Box(modifier = Modifier.size(148.dp).padding(bottom = 6.dp).clickable { onImageClick(post) }, contentAlignment = Alignment.Center) {
                AsyncImage(model = imageUrl, contentDescription = "Post image", modifier = Modifier.fillMaxSize().then(if (post.isDeleted) Modifier.graphicsLayer { alpha = 0.55f } else Modifier))
                if (isVideo) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(20.dp)) }
                    }
                }
                if (post.isDeleted) {
                    Surface(color = deletedAccent.copy(alpha = 0.9f), shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Text(text = "DELETED FROM THREAD", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                    }
                }
            }
            ChanText(text = "${post.imageExt?.removePrefix(".")?.uppercase()} | ${post.fileSizeKb()}", variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (post.isDeleted) 0.3f else 0.5f))
            Spacer(Modifier.height(8.dp))
        }

        if (post.safeComment().isNotEmpty()) {
            ChanHtmlText(
                html = post.safeComment(),
                modifier = Modifier.fillMaxWidth(),
                onReplyClick = if (post.isDeleted && !allowDeletedInteractions) null else { clickedId -> onReplyClick(clickedId, post) },
                textDecoration = if (post.isDeleted) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(10.dp))
        }

        ChanText(text = dateStr, variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))

        if (!post.isDeleted || (allowDeletedInteractions && replyCount > 0)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (replyCount > 0) {
                    Row(modifier = Modifier.clickable { onShowRepliesClick(post) }.padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "$replyCount", color = ChanGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Icon(Icons.AutoMirrored.Filled.Reply, "View replies", tint = ChanGreen, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                if (!post.isDeleted) {
                    TextButton(onClick = { /* TODO: post composer */ }) {
                        Text(text = "REPLY", color = ChanGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PopupPostItem(
    post               : PostDto,
    boardTag           : String,
    threadNo           : Long = 0L,
    allPosts           : List<PostDto> = emptyList(),
    allowDeletedInteractions: Boolean = false,
    onReplyClick       : (Long, PostDto) -> Unit = { _, _ -> },
    onShowRepliesClick : (PostDto) -> Unit = {},
    onImageClick       : (PostDto) -> Unit = {}
) {
    val context = LocalContext.current
    val timeAgo = remember(post.unixTime) { post.unixTime?.let { relativeTime(it) } ?: "" }
    val replyCount = remember(allPosts, post.id) { allPosts.count { it.repliesTo(post.id) } }
    val deletedAccent = Color(0xFFFF9A62)
    val popupModifier = if (post.isDeleted) Modifier.fillMaxWidth().background(color = Color(0xFF3A241F), shape = MaterialTheme.shapes.small).padding(8.dp) else Modifier.fillMaxWidth()

    Column(modifier = popupModifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(text = post.safeName(), variant = TextVariant.Username, color = if (post.isDeleted) deletedAccent else ChanGreen)
                Spacer(Modifier.width(6.dp))
                ChanText(text = post.id.toString(), variant = TextVariant.Meta, color = TextLink)
                if (post.isDeleted) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = deletedAccent.copy(alpha = 0.18f), shape = MaterialTheme.shapes.extraSmall) {
                        Text(text = "DELETED", color = deletedAccent, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp))
                    }
                }
            }
            ChanText(text = timeAgo, variant = TextVariant.Meta, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(6.dp))
        val imageUrl = remember(post.imageId, boardTag, threadNo) {
            if (post.hasImage()) {
                val localThumb = SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, post.id)
                if (localThumb.exists()) localThumb.absolutePath else "https://t.4cdn.org/$boardTag/${post.imageId}s.jpg"
            } else null
        }
        if (imageUrl != null) {
            val isVideo = post.imageExt?.endsWith(".webm", ignoreCase = true) == true || post.imageExt?.endsWith(".mp4", ignoreCase = true) == true
            Box(modifier = Modifier.size(80.dp).padding(bottom = 4.dp).clickable { onImageClick(post) }, contentAlignment = Alignment.Center) {
                AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().then(if (post.isDeleted) Modifier.graphicsLayer { alpha = 0.55f } else Modifier))
                if (isVideo) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.size(24.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(14.dp)) }
                    }
                }
                if (post.isDeleted) {
                    Surface(color = deletedAccent.copy(alpha = 0.9f), shape = MaterialTheme.shapes.extraSmall, modifier = Modifier.align(Alignment.BottomCenter)) {
                        Text(text = "DELETED", color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
        }
        if (post.safeComment().isNotEmpty()) {
            ChanHtmlText(
                html = post.safeComment(),
                modifier = Modifier.fillMaxWidth(),
                onReplyClick = if (post.isDeleted && !allowDeletedInteractions) null else { clickedId -> onReplyClick(clickedId, post) },
                textDecoration = if (post.isDeleted) TextDecoration.LineThrough else null
            )
        }
        if (replyCount > 0 && (!post.isDeleted || allowDeletedInteractions)) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.clickable { onShowRepliesClick(post) }.padding(vertical = 4.dp, horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$replyCount", color = ChanGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.width(2.dp))
                Icon(Icons.AutoMirrored.Filled.Reply, "View replies", tint = ChanGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}
