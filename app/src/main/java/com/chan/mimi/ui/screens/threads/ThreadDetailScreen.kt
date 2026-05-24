package com.chan.mimi.ui.screens.threads

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

// ── Reply popup state ─────────────────────────────────────────
data class ReplyPopup(
    val quotedPost    : com.chan.mimi.data.model.PostDto? = null,
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
    val hasNewPosts by viewModel.hasNewPosts.collectAsStateWithLifecycle()
    val pollCountdown by viewModel.pollCountdown.collectAsStateWithLifecycle()
    val context     = LocalContext.current
    val listState   = rememberLazyListState()
    val scope       = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Search query lives here so it survives recomposition across uiState changes
    var searchQuery by remember { mutableStateOf("") }

    // Reply popup — null means hidden
    var replyPopup by remember { mutableStateOf<ReplyPopup?>(null) }

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

                    // Bookmark
                    IconButton(onClick = { viewModel.toggleSave() }) {
                        Icon(
                            imageVector        = if (isSaved) Icons.Default.Bookmark
                            else Icons.Default.BookmarkBorder,
                            contentDescription = "Save thread",
                            tint               = if (isSaved) ChanGreen
                            else MaterialTheme.colorScheme.onBackground
                        )
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
        ) {
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
                    val posts = (uiState as ThreadDetailUiState.Success).posts

                    val displayedPosts = remember(searchQuery, posts) {
                        if (searchQuery.isEmpty()) posts
                        else posts.filter {
                            it.safeComment().contains(searchQuery, ignoreCase = true) ||
                                    it.safeName().contains(searchQuery, ignoreCase = true)
                        }
                    }

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
                                allPosts          = posts,
                                onReplyClick      = { postNo ->
                                    // >>postNo tapped — show that post as quoted
                                    val quoted = posts.find { it.id == postNo }
                                    if (quoted != null) replyPopup = ReplyPopup(quotedPost = quoted)
                                },
                                onShowRepliesClick = { targetPost ->
                                    // ←← icon tapped — show target post + all posts replying to it
                                    val replies = posts.filter { p ->
                                        p.safeComment().contains(">>${targetPost.id}")
                                    }
                                    replyPopup = ReplyPopup(
                                        repliesToPost = targetPost,
                                        replies       = replies
                                    )
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

                    // ── Always show the target post at the top ─────────
                    val topPost = popup.quotedPost ?: popup.repliesToPost
                    topPost?.let { post ->
                        PopupPostItem(
                            post         = post,
                            boardTag     = boardTag,
                            onReplyClick = { postNo ->
                                val allPosts = (uiState as? ThreadDetailUiState.Success)?.posts
                                val found = allPosts?.find { it.id == postNo }
                                if (found != null) {
                                    val replies = allPosts.filter { p ->
                                        p.safeComment().contains(">>${found.id}")
                                    }
                                    replyPopup = ReplyPopup(quotedPost = found, replies = replies)
                                }
                            }
                        )
                    }

                    // ── Replies section (all posts quoting the target) ──
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
                                onReplyClick = { postNo ->
                                    val allPosts = (uiState as? ThreadDetailUiState.Success)?.posts
                                    val found = allPosts?.find { it.id == postNo }
                                    if (found != null) {
                                        val replies = allPosts.filter { p ->
                                            p.safeComment().contains(">>${found.id}")
                                        }
                                        replyPopup = ReplyPopup(quotedPost = found, replies = replies)
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
    allPosts           : List<PostDto>  = emptyList(),
    onReplyClick       : (Long) -> Unit = {},
    onShowRepliesClick : (PostDto) -> Unit = {}
) {
    val imageUrl = post.imageUrl(boardTag)
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
        allPosts.count { it.safeComment().contains(">>${post.id}") }
    }

    ChanCard(modifier = Modifier.fillMaxWidth()) {

        // ── Header ────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(
                    text    = post.safeName(),
                    variant = TextVariant.Username,
                    color   = ChanGreen
                )
                Spacer(Modifier.width(8.dp))
                ChanText(
                    text    = post.id.toString(),
                    variant = TextVariant.Meta,
                    color   = TextLink
                )
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

        // ── Image ─────────────────────────────────────────────
        if (imageUrl != null) {
            AsyncImage(
                model              = imageUrl,
                contentDescription = "Post image",
                modifier           = Modifier
                    .size(120.dp)
                    .padding(bottom = 4.dp)
            )
            ChanText(
                text    = "${post.imageExt?.removePrefix(".")?.uppercase()} | ${post.fileSizeKb()}",
                variant = TextVariant.Meta,
                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Comment ───────────────────────────────────────────
        if (post.safeComment().isNotEmpty()) {
            ChanHtmlText(
                html         = post.safeComment(),
                modifier     = Modifier.fillMaxWidth(),
                onReplyClick = onReplyClick
            )
            Spacer(Modifier.height(6.dp))
        }

        // ── Date ──────────────────────────────────────────────
        ChanText(
            text    = dateStr,
            variant = TextVariant.Meta,
            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )

        // ── Bottom row: reply count icon + REPLY button ───────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            // ←← reply count — only shown if someone replied to this post
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
            } else {
                Spacer(Modifier.width(1.dp))
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

// ── Compact post item used inside the reply popup ─────────────
@Composable
fun PopupPostItem(
    post         : PostDto,
    boardTag     : String,
    onReplyClick : (Long) -> Unit = {}
) {
    val timeAgo = remember(post.unixTime) {
        post.unixTime?.let { relativeTime(it) } ?: ""
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(text = post.safeName(), variant = TextVariant.Username, color = ChanGreen)
                Spacer(Modifier.width(6.dp))
                ChanText(text = post.id.toString(), variant = TextVariant.Meta, color = TextLink)
            }
            ChanText(
                text    = timeAgo,
                variant = TextVariant.Meta,
                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        Spacer(Modifier.height(6.dp))
        // Image (compact)
        post.imageUrl(boardTag)?.let { url ->
            AsyncImage(
                model              = url,
                contentDescription = null,
                modifier           = Modifier
                    .size(80.dp)
                    .padding(bottom = 4.dp)
            )
        }
        // Comment
        if (post.safeComment().isNotEmpty()) {
            ChanHtmlText(
                html         = post.safeComment(),
                modifier     = Modifier.fillMaxWidth(),
                onReplyClick = onReplyClick
            )
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