package com.chan.mimi.ui.components

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import java.io.File
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

// ── Data model ──────────────────────────────────────────────────
data class ImageViewerItem(
    val imageUrl     : String,
    val fileUrl      : String = imageUrl,
    val filename     : String = "",
    val fileInfo     : String = "",
    val postUrl      : String = "",
    val username     : String = "Anonymous",
    val postId       : String = "",
    val subject      : String = "",
    val commentHtml  : String = "",
    val timeStr      : String = "",
    val timeAgo      : String = "",
    val replyCount   : Int    = 0,
    val imageCount   : Int    = 0,
    val replies      : List<ImageViewerItem> = emptyList(),
    val thumbnailUrl : String = ""
)

// ── Main composable ──────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullscreenImageViewer(
    items              : List<ImageViewerItem>,
    initialIndex       : Int,
    onIndexChanged     : (Int) -> Unit,
    onDismiss          : () -> Unit,
    onOpenThread       : ((ImageViewerItem) -> Unit)? = null,
    onSwipeLeftToRight : (() -> Unit)? = null,
    onSwipeRightToLeft : (() -> Unit)? = null
) {
    if (items.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows  = false
        )
    ) {
        val pagerState  = rememberPagerState(initialPage = initialIndex) { items.size }
        val context     = LocalContext.current
        val scope       = rememberCoroutineScope()
        var showUi      by remember { mutableStateOf(true) }
        var showGallery by remember { mutableStateOf(false) }
        var showSheet   by remember { mutableStateOf(false) }
        var sessionMuted by remember { mutableStateOf(true) } // Session-level audio muted preference
        var showRepliesModal by remember { mutableStateOf(false) }

        LaunchedEffect(pagerState.currentPage) {
            onIndexChanged(pagerState.currentPage)
        }


        val currentItem = items.getOrElse(pagerState.currentPage) { items.first() }

        // Coil with GIF support
        val imageLoader = remember(context) {
            ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory())
                    else add(GifDecoder.Factory())
                }
                .build()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ── Pager (vertical — swipe down = next, up = prev) ──────
            VerticalPager(
                state    = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item    = items[page]
                val isVideo = item.imageUrl.endsWith(".webm", ignoreCase = true) ||
                              item.imageUrl.endsWith(".mp4",  ignoreCase = true)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Keep viewer swipes aligned with the list/detail screens:
                        // right swipe backs out, left swipe opens the next view.
                        .pointerInput(page) {
                            var totalDragX = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { totalDragX = 0f },
                                onDragEnd = {
                                    // Swipe right (LTR) on page 0 -> close viewer / go back
                                    if (page == 0 && totalDragX > 100f) {
                                        onSwipeLeftToRight?.invoke()
                                    }
                                    // Swipe left (RTL) -> open thread or gallery
                                    if (totalDragX < -100f) {
                                        if (onSwipeRightToLeft != null) onSwipeRightToLeft.invoke()
                                        else showGallery = true
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    totalDragX += dragAmount
                                    change.consume()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isVideo) {
                        VideoPlayer(
                            videoUrl      = item.imageUrl,
                            modifier      = Modifier.fillMaxSize(),
                            onTap         = { showUi = !showUi },
                            isMuted       = sessionMuted,
                            onMuteChanged = { sessionMuted = it },
                            showUi        = showUi,
                            hasComment    = item.subject.isNotEmpty() || item.commentHtml.isNotEmpty()
                        )
                    } else {
                        AsyncImage(
                            model              = item.imageUrl,
                            imageLoader        = imageLoader,
                            contentDescription = item.filename,
                            contentScale       = ContentScale.Fit,
                            modifier           = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null
                                ) { showUi = !showUi }
                        )
                    }
                }
            }

            // ── Top bar ───────────────────────────────────────────
            AnimatedVisibility(
                visible  = showUi && !showGallery,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White)
                    }
                    Text(
                        text       = currentItem.filename.ifEmpty { "Image" },
                        color      = Color.White,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f)
                    )
                    // Gallery grid button
                    IconButton(onClick = { showGallery = true }) {
                        Icon(Icons.Default.GridView, "All images", tint = Color.White)
                    }
                    // Download current file via DownloadManager
                    IconButton(onClick = {
                        downloadFile(context, currentItem.fileUrl, currentItem.filename)
                    }) {
                        Icon(Icons.Default.Download, "Download", tint = Color.White)
                    }
                    // Three-dot
                    IconButton(onClick = { showSheet = true }) {
                        Icon(Icons.Default.MoreVert, "More options", tint = Color.White)
                    }
                }
            }

            // ── Bottom overlay ────────────────────────────────────
            AnimatedVisibility(
                visible  = showUi && !showGallery,
                enter    = fadeIn(),
                exit     = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .navigationBarsPadding()
                ) {
                    // Subject + comment
                    if (currentItem.subject.isNotEmpty() || currentItem.commentHtml.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
                        ) {
                            if (currentItem.subject.isNotEmpty()) {
                                Text(
                                    text       = currentItem.subject,
                                    color      = Color(0xFF4EC94E),
                                    fontSize   = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines   = 2,
                                    overflow   = TextOverflow.Ellipsis
                                )
                            }
                            if (currentItem.commentHtml.isNotEmpty()) {
                                ChanHtmlText(
                                    html      = currentItem.commentHtml,
                                    maxLines  = 3,
                                    textColor = Color.White,
                                    modifier  = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    // File info
                    if (currentItem.fileInfo.isNotEmpty()) {
                        Text(
                            text     = currentItem.fileInfo,
                            color    = Color.White.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp)
                        )
                    }
                    // Bottom action bar
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text       = "${pagerState.currentPage + 1} / ${items.size}",
                            color      = Color.White.copy(alpha = 0.85f),
                            fontSize   = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (currentItem.replies.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { showRepliesModal = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text       = currentItem.replies.size.toString(),
                                        color      = Color.White,
                                        fontSize   = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector        = Icons.AutoMirrored.Filled.Reply,
                                        contentDescription = "View replies",
                                        tint               = Color.White,
                                        modifier           = Modifier.size(22.dp)
                                    )
                                }
                            }
                            if (onOpenThread != null) {
                                Row(
                                    modifier = Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication        = null
                                    ) { onOpenThread(currentItem) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector        = Icons.Default.ChatBubbleOutline,
                                        contentDescription = "Open thread",
                                        tint               = Color.White,
                                        modifier           = Modifier.size(22.dp)
                                    )
                                    if (currentItem.replyCount > 0) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text       = currentItem.replyCount.toString(),
                                            color      = Color.White,
                                            fontSize   = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Gallery overlay (slides in from left) ────────────
            AnimatedVisibility(
                visible  = showGallery,
                enter    = slideInHorizontally { -it },
                exit     = slideOutHorizontally { -it },
                modifier = Modifier.fillMaxSize()
            ) {
                GalleryGrid(
                    items        = items,
                    currentIndex = pagerState.currentPage,
                    context      = context,
                    onBack       = { showGallery = false },
                    onItemClick  = { index ->
                        showGallery = false
                        scope.launch { pagerState.scrollToPage(index) }
                    }
                )
            }
        }

        // ── Bottom sheet ──────────────────────────────────────────
        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSheet = false },
                containerColor   = Color(0xFF1A1A1A),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 8.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .background(
                                Color.White.copy(alpha = 0.3f),
                                shape = MaterialTheme.shapes.extraLarge
                            )
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                ) {
                    SheetItem(Icons.Default.Link, "Copy Post Link") {
                        copyToClipboard(context, "Post link", currentItem.postUrl)
                        showSheet = false
                    }
                    SheetItem(Icons.Default.OpenInBrowser, "Open Post Link") {
                        if (currentItem.postUrl.isNotEmpty())
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentItem.postUrl)))
                        showSheet = false
                    }
                    SheetItem(Icons.Default.Share, "Share Post") {
                        shareText(context, currentItem.postUrl)
                        showSheet = false
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 16.dp),
                        color    = Color.White.copy(alpha = 0.08f)
                    )
                    SheetItem(Icons.Default.Link, "Copy File Link") {
                        copyToClipboard(context, "File link", currentItem.fileUrl)
                        showSheet = false
                    }
                    SheetItem(Icons.Default.Download, "Download File") {
                        downloadFile(context, currentItem.fileUrl, currentItem.filename)
                        showSheet = false
                    }
                    SheetItem(Icons.Default.Share, "Share File") {
                        shareText(context, currentItem.fileUrl)
                        showSheet = false
                    }
                }
            }
        }

        // Replies Modal dialog
        if (showRepliesModal) {
            AlertDialog(
                onDismissRequest = { showRepliesModal = false },
                containerColor   = Color(0xFF1E1E1E),
                title = {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ChanText(
                                text    = ">>${currentItem.postId}",
                                variant = TextVariant.Meta,
                                color   = Color(0xFF5B9BD5)
                            )
                            if (currentItem.replies.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                ChanText(
                                    text    = "${currentItem.replies.size} ${if (currentItem.replies.size == 1) "reply" else "replies"}",
                                    variant = TextVariant.Meta,
                                    color   = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                        IconButton(onClick = { showRepliesModal = false }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint     = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        currentItem.replies.forEachIndexed { i, replyItem ->
                            ViewerPopupPostItem(
                                item = replyItem,
                                onImageClick = { clickedReply ->
                                    // Search for this image/post in the main items list
                                    val targetIndex = items.indexOfFirst { it.postId == clickedReply.postId }
                                    if (targetIndex != -1) {
                                        scope.launch {
                                            pagerState.scrollToPage(targetIndex)
                                        }
                                        showRepliesModal = false
                                    } else {
                                        Toast.makeText(context, "Image not found in viewer list", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            if (i < currentItem.replies.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color    = Color.White.copy(alpha = 0.08f)
                                )
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

// ── Gallery grid overlay ─────────────────────────────────────────
@Composable
private fun GalleryGrid(
    items        : List<ImageViewerItem>,
    currentIndex : Int,
    context      : Context,
    onBack       : () -> Unit,
    onItemClick  : (Int) -> Unit
) {
    val gridState = rememberLazyGridState()

    // Scroll to current item instantly (no animation) when gallery opens
    LaunchedEffect(Unit) {
        if (currentIndex > 0) {
            gridState.scrollToItem(currentIndex)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D0D))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        var count = 0
                        items.forEach { item ->
                            if (item.fileUrl.isNotEmpty()) {
                                downloadFile(context, item.fileUrl, item.filename)
                                count++
                            }
                        }
                        Toast.makeText(context, "Queued $count files for download", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        imageVector        = Icons.Default.Download,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text       = "Download all files",
                        color      = Color.White,
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 2-column grid
            LazyVerticalGrid(
                state                 = gridState,
                columns               = GridCells.Fixed(2),
                contentPadding        = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement   = Arrangement.spacedBy(2.dp),
                modifier              = Modifier.fillMaxSize()
            ) {
                itemsIndexed(items) { index, item ->
                    val isVideo = item.imageUrl.endsWith(".webm", ignoreCase = true) ||
                                  item.imageUrl.endsWith(".mp4",  ignoreCase = true)
                    val thumbUrl = if (item.thumbnailUrl.isNotEmpty()) {
                        item.thumbnailUrl
                    } else {
                        item.imageUrl
                            .replace("i.4cdn.org", "t.4cdn.org")
                            .let { url ->
                                val lastDot = url.lastIndexOf('.')
                                if (lastDot != -1) {
                                    url.substring(0, lastDot) + "s.jpg"
                                } else {
                                    url
                                }
                            }
                    }

                    Box(
                        modifier         = Modifier
                            .aspectRatio(1f)
                            .clickable { onItemClick(index) },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model              = thumbUrl,
                            contentDescription = item.filename,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize()
                        )
                        if (isVideo) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector        = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint               = Color.White,
                                    modifier           = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── VideoPlayer with custom controls, muted by default ───────────
@Composable
private fun VideoPlayer(
    videoUrl      : String,
    modifier      : Modifier = Modifier,
    onTap         : () -> Unit = {},
    isMuted       : Boolean,
    onMuteChanged : (Boolean) -> Unit,
    showUi        : Boolean = false,
    hasComment    : Boolean = false
) {
    val context      = LocalContext.current
    var showControls by remember { mutableStateOf(false) }
    var isPlaying    by remember { mutableStateOf(true)  }
    var currentPos   by remember { mutableStateOf(0L)    }
    var duration     by remember { mutableStateOf(1L)    }
    var isSeeking    by remember { mutableStateOf(false) }
    var controlActionTrigger by remember { mutableStateOf(0) }

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
            repeatMode    = Player.REPEAT_MODE_ONE
            volume        = if (isMuted) 0f else 1f
            prepare()
            playWhenReady = true
        }
    }

    // Sync isMuted state to ExoPlayer volume
    LaunchedEffect(isMuted, exoPlayer) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    // Poll position every 500 ms
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) {
                currentPos = exoPlayer.currentPosition
                duration   = exoPlayer.duration.coerceAtLeast(1L)
                isPlaying  = exoPlayer.isPlaying
            }
            delay(500)
        }
    }

    // Auto-hide controls after 4 seconds (resets when controlActionTrigger or isSeeking changes)
    LaunchedEffect(showControls, controlActionTrigger, isSeeking) {
        if (showControls && !isSeeking) {
            delay(4000)
            showControls = false
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player        = exoPlayer
                    useController = false
                    resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null
                ) {
                    showControls = !showControls
                    onTap()
                }
        )

        // ── Controls overlay ──────────────────────────────────────
        AnimatedVisibility(
            visible = showControls,
            enter   = fadeIn(),
            exit    = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) {
                        showControls = false
                        onTap()
                    }
            ) {
                // Center play/pause
                Surface(
                    shape    = CircleShape,
                    color    = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .size(68.dp)
                        .align(Alignment.Center)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) {
                            controlActionTrigger++
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                isPlaying = exoPlayer.isPlaying
                                controlActionTrigger++
                            }
                        ) {
                            Icon(
                                imageVector        = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint               = Color.White,
                                modifier           = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Bottom seek + mute bar
                val bottomPadding = if (showUi) {
                    if (hasComment) 140.dp else 70.dp
                } else {
                    0.dp
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) {
                            controlActionTrigger++
                        }
                        .navigationBarsPadding()
                        .padding(bottom = bottomPadding)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    // Seek slider
                    Slider(
                        value             = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                        onValueChange     = { fraction ->
                            isSeeking  = true
                            currentPos = (fraction * duration).toLong()
                            controlActionTrigger++
                        },
                        onValueChangeFinished = {
                            exoPlayer.seekTo(currentPos)
                            isSeeking = false
                            controlActionTrigger++
                        },
                        colors = SliderDefaults.colors(
                            thumbColor         = Color.White,
                            activeTrackColor   = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Time + mute
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = "${formatMs(currentPos)} / ${formatMs(duration)}",
                            color    = Color.White,
                            fontSize = 12.sp
                        )
                        IconButton(
                            onClick  = {
                                onMuteChanged(!isMuted)
                                controlActionTrigger++
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector        = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Mute toggle",
                                tint               = Color.White,
                                modifier           = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────
private fun formatMs(ms: Long): String {
    val secs = ms / 1000L
    return "%02d:%02d".format(secs / 60, secs % 60)
}

private fun copyLocalFileToDownloads(context: Context, filePath: String, filename: String) {
    try {
        val sourceFile = File(filePath)
        if (!sourceFile.exists()) {
            Toast.makeText(context, "Source file not found", Toast.LENGTH_SHORT).show()
            return
        }

        val rawFilename = filename.ifEmpty { sourceFile.name }
        val cleanFilename = rawFilename.replace(Regex("[\\\\/:*?\"<>|]"), "_")

        val mimeType = when {
            cleanFilename.endsWith(".jpg", ignoreCase = true) || cleanFilename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            cleanFilename.endsWith(".png", ignoreCase = true) -> "image/png"
            cleanFilename.endsWith(".gif", ignoreCase = true) -> "image/gif"
            cleanFilename.endsWith(".webm", ignoreCase = true) -> "video/webm"
            cleanFilename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "application/octet-stream"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, cleanFilename)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    sourceFile.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(context, "Saved to Downloads: $cleanFilename", Toast.LENGTH_SHORT).show()
            } else {
                throw Exception("Failed to insert media store entry")
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val destFile = File(downloadsDir, cleanFilename)
            sourceFile.inputStream().use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                data = Uri.fromFile(destFile)
            }
            context.sendBroadcast(mediaScanIntent)
            Toast.makeText(context, "Saved to Downloads: $cleanFilename", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun downloadFile(context: Context, url: String, filename: String) {
    if (url.isEmpty()) return
    try {
        if (url.startsWith("file://")) {
            val filePath = Uri.parse(url).path?.let { Uri.decode(it) } ?: url.removePrefix("file://")
            copyLocalFileToDownloads(context, filePath, filename)
            return
        }
        if (url.startsWith("content://")) {
            Toast.makeText(context, "Content URIs not supported for copy", Toast.LENGTH_SHORT).show()
            return
        }

        val rawFilename = filename.ifEmpty { "4chan_${System.currentTimeMillis()}" }
        // Replace illegal filesystem characters with underscores to prevent DownloadManager crash
        val cleanFilename = rawFilename.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { "4chan_${System.currentTimeMillis()}" }
        
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(cleanFilename)
            setDescription("Downloading from 4chan…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, cleanFilename)
            addRequestHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            addRequestHeader("Referer", "https://boards.4chan.org/")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)

            // Set correct MIME type so files are recognized in the system MediaStore/Gallery
            val mimeType = when {
                cleanFilename.endsWith(".jpg", ignoreCase = true) || cleanFilename.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                cleanFilename.endsWith(".png", ignoreCase = true) -> "image/png"
                cleanFilename.endsWith(".gif", ignoreCase = true) -> "image/gif"
                cleanFilename.endsWith(".webm", ignoreCase = true) -> "video/webm"
                cleanFilename.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                else -> null
            }
            if (mimeType != null) {
                setMimeType(mimeType)
            }
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "Downloading $cleanFilename…", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cb.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareText(context: Context, text: String) {
    if (text.isEmpty()) return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share"))
}

// ── Bottom sheet row ──────────────────────────────────────────────
@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}

// ── Compact post item used inside the viewer replies popup ───────
@Composable
private fun ViewerPopupPostItem(
    item: ImageViewerItem,
    onImageClick: (ImageViewerItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Header
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChanText(
                    text    = item.username.ifEmpty { "Anonymous" },
                    variant = TextVariant.Username,
                    color   = Color(0xFF4EC94E)
                )
                Spacer(Modifier.width(6.dp))
                ChanText(
                    text    = item.postId,
                    variant = TextVariant.Meta,
                    color   = Color(0xFF5B9BD5)
                )
            }
            ChanText(
                text    = item.timeAgo,
                variant = TextVariant.Meta,
                color   = Color.White.copy(alpha = 0.5f)
            )
        }
        Spacer(Modifier.height(6.dp))
        // Image
        if (item.imageUrl.isNotEmpty()) {
            val isVideo = item.imageUrl.endsWith(".webm", ignoreCase = true) ||
                          item.imageUrl.endsWith(".mp4",  ignoreCase = true)
            val thumbUrl = item.imageUrl
                .replace("i.4cdn.org", "t.4cdn.org")
                .let { url ->
                    val lastDot = url.lastIndexOf('.')
                    if (lastDot != -1) {
                        url.substring(0, lastDot) + "s.jpg"
                    } else {
                        url
                    }
                }
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 4.dp)
                    .clickable { onImageClick(item) },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model              = thumbUrl,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize()
                )
                if (isVideo) {
                    Surface(
                        shape = CircleShape,
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
        // Comment
        if (item.commentHtml.isNotEmpty()) {
            ChanHtmlText(
                html      = item.commentHtml,
                textColor = Color.White,
                modifier  = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}
