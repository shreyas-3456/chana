package com.chan.mimi.ui.screens.threads

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.zIndex

// ── Reply popup state ─────────────────────────────────────────
data class ReplyPopup(
    val quotedPost    : com.chan.mimi.data.model.PostDto? = null,
    val sourcePost    : com.chan.mimi.data.model.PostDto? = null,
    val repliesToPost : com.chan.mimi.data.model.PostDto? = null,
    val replies       : List<com.chan.mimi.data.model.PostDto> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    boardTag    : String,
    threadNo    : Long,
    threadTitle : String,
    onBackClick : () -> Unit,
    viewModel   : ThreadDetailViewModel = viewModel(key = "$boardTag/$threadNo")
) {
    val uiState     by viewModel.uiState.collectAsStateWithLifecycle()
    val isSaved     by viewModel.isSaved.collectAsStateWithLifecycle()
    val isSaving    by viewModel.isSaving.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val hasNewPosts by viewModel.hasNewPosts.collectAsStateWithLifecycle()
    val pollCountdown by viewModel.pollCountdown.collectAsStateWithLifecycle()
    val context     = LocalContext.current

    val rotationTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationAngle"
    )
    val rotationAngle = if (isRefreshing) rotation else 0f
    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Search query lives here so it survives recomposition across uiState changes
    var searchQuery by remember { mutableStateOf("") }

    // Reply popup — null means hidden
    var replyPopup by remember { mutableStateOf<ReplyPopup?>(null) }

    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var activeIndex by remember { mutableStateOf(0) }

    val posts = (uiState as? ThreadDetailUiState.Success)?.posts ?: emptyList()
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

    // Synchronize background scrolling with active index of viewer
    LaunchedEffect(viewerStartIndex, activeIndex) {
        if (viewerStartIndex != null && activeIndex >= 0 && activeIndex < imagePosts.size) {
            val targetPost = imagePosts[activeIndex]
            val originalIndex = displayedPosts.indexOf(targetPost)
            if (originalIndex != -1) {
                val lazyListIndex = originalIndex + 2 // +2 for search bar and spacer
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

    DisposableEffect(boardTag, threadNo) {
        viewModel.startPolling(boardTag, threadNo)
        onDispose { viewModel.stopPolling() }
    }

    // Hide keyboard/cursor when user starts scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }
    val showScrollToBottom by remember {
        derivedStateOf {
            val info        = listState.layoutInfo
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
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // ── Animated countdown ring ───────────────────────
                    Box(
                        modifier         = Modifier.size(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
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

                    // Reload
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

                    // Bookmark
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
                                imageVector        = if (isSaved) Icons.Default.Bookmark
                                else Icons.Default.BookmarkBorder,
                                contentDescription = "Save thread",
                                tint               = if (isSaved) ChanGreen
                                else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Three-dot menu
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
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
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(threadUrl))
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text        = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick     = {
                                    menuExpanded = false
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, threadUrl)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(intent, "Share thread")
                                    )
                                }
                            )
                        }
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
                // Swipe right → go back (only when fullscreen viewer is NOT open)
                .pointerInput(viewerStartIndex) {
                    if (viewerStartIndex != null) return@pointerInput
                    var totalDragX = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDragX = 0f },
                        onDragEnd = {
                            if (totalDragX > 120f) onBackClick()
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
                        .align(Alignment.TopCenter)
                        .zIndex(1f)
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
                            ChanButton(
                                text    = "RETRY",
                                onClick = { viewModel.startPolling(boardTag, threadNo) }
                            )
                        }
                    }
                }

                is ThreadDetailUiState.Success -> {

                    LazyColumn(
                        state               = listState,
                        modifier            = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ── Search bar scrolls with content ──────────────
                        item(key = "search_bar") {
                            TextField(
                                value         = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder   = {
                                    Text(
                                        "Search in thread",
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
                            PostCard(
                                post              = post,
                                boardTag          = boardTag,
                                threadNo          = threadNo,
                                allPosts          = posts,
                                onReplyClick      = { postNo, source ->
                                    // >>postNo tapped — show that post as quoted along with the source post
                                    val quoted = posts.find { it.id == postNo }
                                    if (quoted != null) {
                                        replyPopup = ReplyPopup(
                                            quotedPost = quoted,
                                            sourcePost = source
                                        )
                                    }
                                },
                                onShowRepliesClick = { targetPost ->
                                    // ←← icon tapped — show target post + all posts replying to it
                                    val replies = posts.filter { p ->
                                        p.repliesTo(targetPost.id)
                                    }
                                    replyPopup = ReplyPopup(
                                        repliesToPost = targetPost,
                                        replies       = replies
                                    )
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
                        item { Spacer(Modifier.height(8.dp)) }
                    }
                }
            }

            // ── New posts bell badge ──────────────────────────────────
            AnimatedVisibility(
                visible  = hasNewPosts,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 104.dp) // above both scroll FABs
            ) {
                BadgedBox(
                    badge = {
                        Badge(containerColor = Color(0xFFFF4444)) {
                            Text("!", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    FloatingActionButton(
                        onClick        = { viewModel.applyNewPosts(boardTag, threadNo) },
                        containerColor = Color(0xFFFFAA00),
                        contentColor   = Color.White,
                        shape          = MaterialTheme.shapes.extraLarge,
                        modifier       = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Campaign,
                            contentDescription = "New posts",
                            modifier           = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ── Scroll to top ─────────────────────────────────────────
            AnimatedVisibility(
                visible  = showScrollToTop,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 56.dp)
            ) {
                SmallFloatingActionButton(
                    onClick        = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape          = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll to top",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Scroll to bottom ──────────────────────────────────────
            AnimatedVisibility(
                visible  = showScrollToBottom,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 8.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val last = listState.layoutInfo.totalItemsCount - 1
                            if (last >= 0) listState.animateScrollToItem(last)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape          = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Fullscreen Image Viewer Overlay
            if (viewerStartIndex != null) {
                val viewerItems = remember(imagePosts, posts, boardTag, threadNo) {
                    val sdf = java.text.SimpleDateFormat("EEEE yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    imagePosts.map { post ->
                        val localFile = com.chan.mimi.data.repository.SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, post.id, post.imageExt ?: "")
                        val fileUrl = if (localFile.exists()) {
                            android.net.Uri.fromFile(localFile).toString()
                        } else {
                            post.imageUrl(boardTag)!!
                        }
                        val ext     = post.imageExt?.removePrefix(".")?.uppercase() ?: ""
                        val replyPosts = posts.filter { p ->
                            p.repliesTo(post.id)
                        }
                        val itemReplies = replyPosts.map { r ->
                            val localReplyFile = com.chan.mimi.data.repository.SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, r.id, r.imageExt ?: "")
                            val rFileUrl = if (localReplyFile.exists()) {
                                android.net.Uri.fromFile(localReplyFile).toString()
                            } else {
                                r.imageUrl(boardTag)
                            }
                            val rExt     = r.imageExt?.removePrefix(".")?.uppercase() ?: ""
                            ImageViewerItem(
                                imageUrl    = rFileUrl ?: "",
                                fileUrl     = rFileUrl ?: "",
                                filename    = r.filename?.let { "${it}${r.imageExt ?: ""}" } ?: "",
                                fileInfo    = if (r.hasImage()) "$rExt | ${r.fileSizeKb()}  ${r.imageWidth}x${r.imageHeight}" else "",
                                postUrl     = "https://boards.4chan.org/$boardTag/thread/$threadNo#p${r.id}",
                                username    = r.safeName(),
                                postId      = r.id.toString(),
                                subject     = r.safeSubject(),
                                commentHtml = r.safeComment(),
                                timeStr     = r.unixTime?.let { sdf.format(java.util.Date(it * 1000L)) } ?: "",
                                timeAgo     = r.unixTime?.let { relativeTime(it) } ?: ""
                            )
                        }
                        ImageViewerItem(
                            imageUrl    = fileUrl,
                            fileUrl     = fileUrl,
                            filename    = "${post.filename ?: post.imageId}${post.imageExt ?: ""}",
                            fileInfo    = "$ext | ${post.fileSizeKb()}  ${post.imageWidth}x${post.imageHeight}",
                            postUrl     = "https://boards.4chan.org/$boardTag/thread/$threadNo#p${post.id}",
                            username    = post.safeName(),
                            postId      = post.id.toString(),
                            subject     = post.safeSubject(),
                            commentHtml = post.safeComment(),
                            timeStr     = post.unixTime?.let { sdf.format(java.util.Date(it * 1000L)) } ?: "",
                            timeAgo     = post.unixTime?.let { relativeTime(it) } ?: "",
                            replyCount  = itemReplies.size,
                            replies     = itemReplies
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

    // ── Reply popup dialog ────────────────────────────────────────
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
                            ChanText(
                                text    = ">>${titlePost.id}",
                                variant = TextVariant.Meta,
                                color   = TextLink
                            )
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
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

                    if (popup.quotedPost != null && popup.sourcePost != null) {
                        // Scenario A: Clicked on a reply link (>>A) in post B
                        // Render quotedPost (post A) at the top
                        PopupPostItem(
                            post         = popup.quotedPost,
                            boardTag     = boardTag,
                            threadNo     = threadNo,
                            allPosts     = posts,
                            onReplyClick = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) {
                                    replyPopup = ReplyPopup(
                                        quotedPost = found,
                                        sourcePost = source
                                    )
                                }
                            },
                            onShowRepliesClick = { targetPost ->
                                val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                replyPopup = ReplyPopup(
                                    repliesToPost = targetPost,
                                    replies       = replies
                                )
                            },
                            onImageClick = { clickedPost ->
                                val index = imagePosts.indexOf(clickedPost)
                                if (index != -1) {
                                    activeIndex = index
                                    viewerStartIndex = index
                                    replyPopup = null
                                }
                            }
                        )

                        // Visual connector between quoted post and the reply
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            ChanText(
                                text    = "  replied by  ",
                                variant = TextVariant.Meta,
                                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        }

                        // Render sourcePost (post B, the current reply)
                        PopupPostItem(
                            post         = popup.sourcePost,
                            boardTag     = boardTag,
                            threadNo     = threadNo,
                            allPosts     = posts,
                            onReplyClick = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) {
                                    replyPopup = ReplyPopup(
                                        quotedPost = found,
                                        sourcePost = source
                                    )
                                }
                            },
                            onShowRepliesClick = { targetPost ->
                                val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                replyPopup = ReplyPopup(
                                    repliesToPost = targetPost,
                                    replies       = replies
                                )
                            },
                            onImageClick = { clickedPost ->
                                val index = imagePosts.indexOf(clickedPost)
                                if (index != -1) {
                                    activeIndex = index
                                    viewerStartIndex = index
                                    replyPopup = null
                                }
                            }
                        )
                    } else {
                        // Scenario B: Clicked on a replies count box (repliesToPost + replies list)
                        val topPost = popup.quotedPost ?: popup.repliesToPost
                        topPost?.let { post ->
                            PopupPostItem(
                                post         = post,
                                boardTag     = boardTag,
                                threadNo     = threadNo,
                                allPosts     = posts,
                                onReplyClick = { postNo, source ->
                                    val found = posts.find { it.id == postNo }
                                    if (found != null) {
                                        replyPopup = ReplyPopup(
                                            quotedPost = found,
                                            sourcePost = source
                                        )
                                    }
                                },
                                onShowRepliesClick = { targetPost ->
                                    val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                    replyPopup = ReplyPopup(
                                        repliesToPost = targetPost,
                                        replies       = replies
                                    )
                                },
                                onImageClick = { clickedPost ->
                                    val index = imagePosts.indexOf(clickedPost)
                                    if (index != -1) {
                                        activeIndex = index
                                        viewerStartIndex = index
                                        replyPopup = null
                                    }
                                }
                            )
                        }

                        if (popup.replies.isNotEmpty()) {
                            Row(
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                ChanText(
                                    text    = "  replies  ",
                                    variant = TextVariant.Meta,
                                    color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
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
                                        if (found != null) {
                                            replyPopup = ReplyPopup(
                                                quotedPost = found,
                                                sourcePost = source
                                            )
                                        }
                                    },
                                    onShowRepliesClick = { targetPost ->
                                        val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                        replyPopup = ReplyPopup(
                                            repliesToPost = targetPost,
                                            replies       = replies
                                        )
                                    },
                                    onImageClick = { clickedPost ->
                                        val index = imagePosts.indexOf(clickedPost)
                                        if (index != -1) {
                                            activeIndex = index
                                            viewerStartIndex = index
                                            replyPopup = null
                                        }
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

// ============================================================
// POST CARD
// ============================================================

@Composable
fun PostCard(
    post               : PostDto,
    boardTag           : String,
    threadNo           : Long = 0L,
    allPosts           : List<PostDto>  = emptyList(),
    onReplyClick       : (Long, PostDto) -> Unit = { _, _ -> },
    onShowRepliesClick : (PostDto) -> Unit = {},
    onImageClick       : (PostDto) -> Unit = {}
) {
    val context = LocalContext.current
    val imageUrl = remember(post.imageId, boardTag, threadNo) {
        if (post.hasImage()) {
            val localThumb = com.chan.mimi.data.repository.SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, post.id)
            if (localThumb.exists()) {
                localThumb.absolutePath
            } else {
                "https://t.4cdn.org/$boardTag/${post.imageId}s.jpg"
            }
        } else null
    }
    val dateStr  = remember(post.unixTime) {
        post.unixTime?.let {
            val sdf = SimpleDateFormat("EEEE yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.format(Date(it * 1000L))
        } ?: ""
    }
    val timeAgo = remember(post.unixTime) {
        post.unixTime?.let { relativeTime(it) } ?: ""
    }

    // Count how many posts in the thread reply to this post
    val replyCount = remember(allPosts, post.id) {
        allPosts.count { it.repliesTo(post.id) }
    }

    // Deleted posts get a red-tinted background
    val cardModifier = if (post.isDeleted) {
        Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFFFF4444).copy(alpha = 0.07f),
                shape = MaterialTheme.shapes.medium
            )
    } else {
        Modifier.fillMaxWidth()
    }

    ChanCard(modifier = cardModifier) {

        // ── Header ──────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(
                    text    = post.safeName(),
                    variant = TextVariant.Username,
                    color   = if (post.isDeleted) Color(0xFFFF6B6B) else ChanGreen
                )
                Spacer(Modifier.width(8.dp))
                ChanText(
                    text    = post.id.toString(),
                    variant = TextVariant.Meta,
                    color   = TextLink
                )
                // [DELETED] badge
                if (post.isDeleted) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color  = Color(0xFFFF4444).copy(alpha = 0.15f),
                        shape  = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text       = "DELETED",
                            color      = Color(0xFFFF4444),
                            fontSize   = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(
                    text    = timeAgo,
                    variant = TextVariant.Meta,
                    color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = null,
                    tint     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Image ────────────────────────────────────────
        if (imageUrl != null) {
            val isVideo = remember(post.imageExt) {
                post.imageExt?.endsWith(".webm", ignoreCase = true) == true ||
                        post.imageExt?.endsWith(".mp4", ignoreCase = true) == true
            }
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 4.dp)
                    .then(
                        // Deleted posts: image is not clickable
                        if (post.isDeleted) Modifier
                        else Modifier.clickable { onImageClick(post) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (post.isDeleted) {
                    // Greyed-out placeholder for deleted media
                    Surface(
                        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape    = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector        = Icons.Default.DeleteForever,
                                    contentDescription = "Deleted media",
                                    tint               = Color(0xFFFF4444).copy(alpha = 0.6f),
                                    modifier           = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text     = if (isVideo) "Video\ndeleted" else "Image\ndeleted",
                                    color    = Color(0xFFFF4444).copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    AsyncImage(
                        model              = imageUrl,
                        contentDescription = "Post image",
                        modifier           = Modifier.fillMaxSize()
                    )
                    if (isVideo) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector        = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            ChanText(
                text    = "${post.imageExt?.removePrefix(".")?.uppercase()} | ${post.fileSizeKb()}",
                variant = TextVariant.Meta,
                color   = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (post.isDeleted) 0.3f else 0.5f
                )
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Comment ──────────────────────────────────────
        if (post.safeComment().isNotEmpty()) {
            ChanHtmlText(
                html         = post.safeComment(),
                modifier     = Modifier
                    .fillMaxWidth()
                    .then(
                        if (post.isDeleted)
                            Modifier  // strikethrough applied via textDecoration inside
                        else Modifier
                    ),
                onReplyClick = if (post.isDeleted) null else { clickedId ->
                    onReplyClick(clickedId, post)
                },
                textDecoration = if (post.isDeleted) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(6.dp))
        }

        // ── Date ────────────────────────────────────────
        ChanText(
            text    = dateStr,
            variant = TextVariant.Meta,
            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        if (!post.isDeleted) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // reply count — only shown if someone replied to this post
                if (replyCount > 0) {
                    Row(
                        modifier          = Modifier
                            .clickable { onShowRepliesClick(post) }
                            .padding(vertical = 4.dp, horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text       = "$replyCount",
                            color      = ChanGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp
                        )
                        Icon(
                            Icons.Default.Reply,
                            contentDescription = "View replies",
                            tint     = ChanGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                TextButton(onClick = { /* TODO: post composer */ }) {
                    Text(
                        text       = "REPLY",
                        color      = ChanGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 12.sp
                    )
                }
            }
        }
    }
}

// ── Compact post item used inside the reply popup ─────────────
@Composable
fun PopupPostItem(
    post               : PostDto,
    boardTag           : String,
    threadNo           : Long = 0L,
    allPosts           : List<PostDto> = emptyList(),
    onReplyClick       : (Long, PostDto) -> Unit = { _, _ -> },
    onShowRepliesClick : (PostDto) -> Unit = {},
    onImageClick       : (PostDto) -> Unit = {}
) {
    val context = LocalContext.current
    val timeAgo = remember(post.unixTime) {
        post.unixTime?.let { relativeTime(it) } ?: ""
    }
    val replyCount = remember(allPosts, post.id) {
        allPosts.count { it.repliesTo(post.id) }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(
                    text    = post.safeName(),
                    variant = TextVariant.Username,
                    color   = if (post.isDeleted) Color(0xFFFF6B6B) else ChanGreen
                )
                Spacer(Modifier.width(6.dp))
                ChanText(text = post.id.toString(), variant = TextVariant.Meta, color = TextLink)
                // [DELETED] badge
                if (post.isDeleted) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color  = Color(0xFFFF4444).copy(alpha = 0.15f),
                        shape  = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text       = "DELETED",
                            color      = Color(0xFFFF4444),
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier   = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }
            ChanText(
                text    = timeAgo,
                variant = TextVariant.Meta,
                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(6.dp))
        // Image (compact)
        val imageUrl = remember(post.imageId, boardTag, threadNo) {
            if (post.hasImage()) {
                val localThumb = com.chan.mimi.data.repository.SavedThreadsHelper.getLocalThumbFile(context, boardTag, threadNo, post.id)
                if (localThumb.exists()) {
                    localThumb.absolutePath
                } else {
                    "https://t.4cdn.org/$boardTag/${post.imageId}s.jpg"
                }
            } else null
        }
        if (imageUrl != null) {
            val isVideo = remember(post.imageExt) {
                post.imageExt?.endsWith(".webm", ignoreCase = true) == true ||
                        post.imageExt?.endsWith(".mp4", ignoreCase = true) == true
            }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 4.dp)
                    .then(
                        if (post.isDeleted) Modifier
                        else Modifier.clickable { onImageClick(post) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (post.isDeleted) {
                    // Greyed-out placeholder for deleted media
                    Surface(
                        color    = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape    = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector        = Icons.Default.DeleteForever,
                                    contentDescription = "Deleted media",
                                    tint               = Color(0xFFFF4444).copy(alpha = 0.6f),
                                    modifier           = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text       = if (isVideo) "Video deleted" else "Image deleted",
                                    color      = Color(0xFFFF4444).copy(alpha = 0.6f),
                                    fontSize   = 7.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign  = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    AsyncImage(
                        model              = imageUrl,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize()
                    )
                    if (isVideo) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector        = Icons.Default.PlayArrow,
                                    contentDescription = "Video",
                                    tint               = Color.White,
                                    modifier           = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        // Comment
        if (post.safeComment().isNotEmpty()) {
            ChanHtmlText(
                html           = post.safeComment(),
                modifier       = Modifier.fillMaxWidth(),
                onReplyClick   = if (post.isDeleted) null else { clickedId ->
                    onReplyClick(clickedId, post)
                },
                textDecoration = if (post.isDeleted) TextDecoration.LineThrough else null
            )
        }
        // Replied box (shows replies to this post)
        if (!post.isDeleted && replyCount > 0) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier          = Modifier
                    .clickable { onShowRepliesClick(post) }
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = "$replyCount",
                    color      = ChanGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Default.Reply,
                    contentDescription = "View replies",
                    tint               = ChanGreen,
                    modifier           = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Relative time helper ──────────────────────────────────────
fun relativeTime(unixTime: Long): String {
    val diff = System.currentTimeMillis() / 1000L - unixTime
    return when {
        diff < 60    -> "just now"
        diff < 3600  -> "${diff / 60} minutes ago"
        diff < 86400 -> "${diff / 3600} hours ago"
        else         -> "${diff / 86400} days ago"
    }
}