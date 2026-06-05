package com.chan.mimi.ui.screens.saved

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chan.mimi.data.model.PostDto
import com.chan.mimi.data.repository.SavedThreadsHelper
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.screens.threads.PostCard
import com.chan.mimi.ui.screens.threads.PopupPostItem
import com.chan.mimi.ui.screens.threads.relativeTime
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedThreadDetailScreen(
    boardTag: String,
    threadNo: Long,
    threadTitle: String,
    onBackClick: () -> Unit,
    viewModel: SavedThreadDetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var replyPopup by remember { mutableStateOf<com.chan.mimi.ui.screens.threads.ReplyPopup?>(null) }
    var viewerStartIndex by remember { mutableStateOf<Int?>(null) }
    var activeIndex by remember { mutableStateOf(0) }

    // Load local thread data
    LaunchedEffect(boardTag, threadNo) {
        viewModel.loadSavedThread(boardTag, threadNo)
    }

    // Hide keyboard when scrolling
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) focusManager.clearFocus()
    }

    val detail = (uiState as? SavedThreadDetailUiState.Success)?.detail
    val posts = detail?.posts ?: emptyList()

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

    // Scroll sync
    LaunchedEffect(viewerStartIndex, activeIndex) {
        if (viewerStartIndex != null && activeIndex >= 0 && activeIndex < imagePosts.size) {
            val targetPost = imagePosts[activeIndex]
            val originalIndex = displayedPosts.indexOf(targetPost)
            if (originalIndex != -1) {
                val lazyListIndex = originalIndex + 2 // search bar + spacer
                val layoutInfo = listState.layoutInfo
                val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                val itemSize = layoutInfo.visibleItemsInfo.firstOrNull { it.index == lazyListIndex }?.size ?: 0
                val offset = if (viewportHeight > 0) (viewportHeight - itemSize) / 2 else 0
                listState.animateScrollToItem(lazyListIndex, -offset)
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        ChanText(
                            text = "/$boardTag/ - $threadNo",
                            variant = TextVariant.Username
                        )
                        ChanText(
                            text = "OFFLINE CACHE",
                            variant = TextVariant.Meta,
                            color = ChanGreen
                        )
                    }
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
                    // Bookmark (Filled indicates saved, clicking it deletes saved)
                    IconButton(onClick = {
                        viewModel.unsaveThread(boardTag, threadNo)
                        Toast.makeText(context, "Thread deleted from saved", Toast.LENGTH_SHORT).show()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Delete saved thread",
                            tint = ChanGreen
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
                            if (totalDragX > 120f) onBackClick()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            totalDragX += dragAmount
                            change.consume()
                        }
                    )
                }
        ) {
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "search_bar") {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        "Search in saved thread",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
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
                                onReplyClick = { postNo, source ->
                                    val quoted = posts.find { it.id == postNo }
                                    if (quoted != null) {
                                        replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                            quotedPost = quoted,
                                            sourcePost = source
                                        )
                                    }
                                },
                                onShowRepliesClick = { targetPost ->
                                    val replies = posts.filter { p ->
                                        p.repliesTo(targetPost.id)
                                    }
                                    replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                        repliesToPost = targetPost,
                                        replies = replies
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

            // Scroll FABs identical to main screen
            val showScrollToTop by remember {
                derivedStateOf { listState.firstVisibleItemIndex > 3 }
            }
            val showScrollToBottom by remember {
                derivedStateOf {
                    val info = listState.layoutInfo
                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible < info.totalItemsCount - 3
                }
            }

            AnimatedVisibility(
                visible = showScrollToTop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 56.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp))
                }
            }

            AnimatedVisibility(
                visible = showScrollToBottom,
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
                    contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp))
                }
            }

            // Offline Fullscreen Image Viewer Overlay
            if (viewerStartIndex != null && detail != null) {
                val viewerItems = remember(imagePosts, posts, boardTag, threadNo) {
                    val sdf = java.text.SimpleDateFormat("EEEE yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    imagePosts.map { post ->
                        val localFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, post.id, post.imageExt ?: "")
                        val fileUrl = if (localFile.exists()) {
                            Uri.fromFile(localFile).toString()
                        } else {
                            post.imageUrl(boardTag) ?: ""
                        }
                        val ext = post.imageExt?.removePrefix(".")?.uppercase() ?: ""
                        val replyPosts = posts.filter { p ->
                            p.repliesTo(post.id)
                        }
                        val itemReplies = replyPosts.map { r ->
                            val localReplyFile = SavedThreadsHelper.getLocalFullFile(context, boardTag, threadNo, r.id, r.imageExt ?: "")
                            val rFileUrl = if (localReplyFile.exists()) {
                                Uri.fromFile(localReplyFile).toString()
                            } else {
                                r.imageUrl(boardTag)
                            }
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
                                timeStr = r.unixTime?.let { sdf.format(java.util.Date(it * 1000L)) } ?: "",
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
                            timeStr = post.unixTime?.let { sdf.format(java.util.Date(it * 1000L)) } ?: "",
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
                    onSwipeLeftToRight = {
                        viewerStartIndex = null
                        onBackClick()
                    }
                )
            }
        }
    }

    // Reply popup dialog inside Saved details
    replyPopup?.let { popup ->
        AlertDialog(
            onDismissRequest = { replyPopup = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val titlePost = popup.quotedPost ?: popup.repliesToPost
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (titlePost != null) {
                            ChanText(
                                text = ">>${titlePost.id}",
                                variant = TextVariant.Meta,
                                color = TextLink
                            )
                            if (popup.replies.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                ChanText(
                                    text = "${popup.replies.size} ${if (popup.replies.size == 1) "reply" else "replies"}",
                                    variant = TextVariant.Meta,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                    IconButton(onClick = { replyPopup = null }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (popup.quotedPost != null && popup.sourcePost != null) {
                        PopupPostItem(
                            post = popup.quotedPost,
                            boardTag = boardTag,
                            threadNo = threadNo,
                            allPosts = posts,
                            onReplyClick = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) {
                                    replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                        quotedPost = found,
                                        sourcePost = source
                                    )
                                }
                            },
                            onShowRepliesClick = { targetPost ->
                                val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                    repliesToPost = targetPost,
                                    replies = replies
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
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            ChanText(
                                text = "  replied by  ",
                                variant = TextVariant.Meta,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        }
                        PopupPostItem(
                            post = popup.sourcePost,
                            boardTag = boardTag,
                            threadNo = threadNo,
                            allPosts = posts,
                            onReplyClick = { postNo, source ->
                                val found = posts.find { it.id == postNo }
                                if (found != null) {
                                    replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                        quotedPost = found,
                                        sourcePost = source
                                    )
                                }
                            },
                            onShowRepliesClick = { targetPost ->
                                val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                    repliesToPost = targetPost,
                                    replies = replies
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
                        val topPost = popup.quotedPost ?: popup.repliesToPost
                        topPost?.let { post ->
                            PopupPostItem(
                                post = post,
                                boardTag = boardTag,
                                threadNo = threadNo,
                                allPosts = posts,
                                onReplyClick = { postNo, source ->
                                    val found = posts.find { it.id == postNo }
                                    if (found != null) {
                                        replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                            quotedPost = found,
                                            sourcePost = source
                                        )
                                    }
                                },
                                onShowRepliesClick = { targetPost ->
                                    val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                    replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                        repliesToPost = targetPost,
                                        replies = replies
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                ChanText(
                                    text = "  replies  ",
                                    variant = TextVariant.Meta,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            }
                            popup.replies.forEachIndexed { i, post ->
                                PopupPostItem(
                                    post = post,
                                    boardTag = boardTag,
                                    threadNo = threadNo,
                                    allPosts = posts,
                                    onReplyClick = { postNo, source ->
                                        val found = posts.find { it.id == postNo }
                                        if (found != null) {
                                            replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                                quotedPost = found,
                                                sourcePost = source
                                            )
                                        }
                                    },
                                    onShowRepliesClick = { targetPost ->
                                        val replies = posts.filter { p -> p.repliesTo(targetPost.id) }
                                        replyPopup = com.chan.mimi.ui.screens.threads.ReplyPopup(
                                            repliesToPost = targetPost,
                                            replies = replies
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
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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
