// FILE: ui/screens/threads/ThreadListScreen.kt
package com.chan.mimi.ui.screens.threads

import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
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

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    items(threads) { thread ->
                        ThreadCard(
                            thread        = thread,
                            boardTag      = board.tag,
                            onThreadClick = onThreadClick
                        )
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
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
    onThreadClick : (ThreadDto) -> Unit
) {
    // Strip HTML tags from comment — 4chan returns HTML in comments
    val cleanComment = remember(thread.comment) {
        val raw = thread.safeComment()
        if (raw.isEmpty()) ""
        else Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    // Build image URL if thread has an image
    val imageUrl = remember(thread.imageId) {
        if (thread.imageId != null && thread.imageExt != null) {
            "https://i.4cdn.org/$boardTag/${thread.imageId}${thread.imageExt}"
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
                AsyncImage(
                    model              = imageUrl,
                    contentDescription = "Thread image",
                    modifier           = Modifier
                        .size(80.dp)
                        .padding(end = 10.dp)
                )
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