package com.chan.mimi.ui.screens.saved

import android.text.Html
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.chan.mimi.data.repository.SavedThreadDetail
import com.chan.mimi.data.repository.SavedThreadsHelper
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark
import com.chan.mimi.ui.theme.TextLink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    innerPadding : PaddingValues,
    onThreadClick : (SavedThreadDetail) -> Unit,
    viewModel    : SavedViewModel = viewModel()
) {
    val savedThreads by viewModel.savedThreads.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        if (savedThreads.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    tint = ChanGreen.copy(alpha = 0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                ChanText(
                    text = "No saved threads yet",
                    variant = TextVariant.Username,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bookmark a thread to save its text, replies, and images for offline viewing.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Saved Threads",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = ChanGreen
                        )
                        Surface(
                            color = ElevatedDark,
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Text(
                                text = "${savedThreads.size} saved",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(savedThreads, key = { "${it.boardTag}_${it.thread.id}" }) { detail ->
                    SavedThreadCard(
                        detail = detail,
                        onClick = { onThreadClick(detail) },
                        onUnsaveClick = {
                            viewModel.unsaveThread(detail.boardTag, detail.thread.id)
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun SavedThreadCard(
    detail: SavedThreadDetail,
    onClick: () -> Unit,
    onUnsaveClick: () -> Unit
) {
    val context = LocalContext.current
    val thread = detail.thread
    val boardTag = detail.boardTag

    val cleanComment = remember(thread.comment) {
        val raw = thread.safeComment()
        if (raw.isEmpty()) ""
        else Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT).toString()
    }

    val localThumb = remember(boardTag, thread.id) {
        SavedThreadsHelper.getLocalThumbFile(context, boardTag, thread.id, thread.id)
    }

    val imageModel = remember(localThumb) {
        if (localThumb.exists()) localThumb.absolutePath else null
    }

    val saveDateStr = remember(detail.saveTime) {
        val sdf = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(detail.saveTime))
    }

    ChanCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        // Row 1 — Board tag, thread no, and Delete action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = ChanGreen.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "/$boardTag/",
                        color = ChanGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ChanText(
                    text = "No. ${thread.id}",
                    variant = TextVariant.Meta,
                    color = TextLink
                )
            }

            IconButton(
                onClick = onUnsaveClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from saved",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Row 2 — Thumbnail & text contents
        Row {
            if (imageModel != null) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .padding(end = 10.dp)
                ) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Thread image",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Surface(
                    color = ElevatedDark,
                    shape = MaterialTheme.shapes.small,
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
                        text = thread.safeSubject(),
                        variant = TextVariant.Username,
                        color = Color(0xFF789922),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                ChanHtmlText(
                    html = thread.safeComment(),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        ChanDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Row 3 — Counts and saved time
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row {
                ChanText(
                    text = "${thread.safeReplyCount()} Replies",
                    variant = TextVariant.Meta
                )
                Spacer(modifier = Modifier.width(12.dp))
                ChanText(
                    text = "${thread.safeImageCount()} Images",
                    variant = TextVariant.Meta
                )
            }

            ChanText(
                text = "Saved $saveDateStr",
                variant = TextVariant.Meta,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
