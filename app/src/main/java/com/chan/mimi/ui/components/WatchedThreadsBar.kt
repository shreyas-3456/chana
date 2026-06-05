package com.chan.mimi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.chan.mimi.data.model.WatchedThread
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.SurfaceDark

private val NewPostsDot = Color(0xFFFFAA00)

@Composable
fun WatchedThreadsBar(
    threads          : List<WatchedThread>,
    activeThreadNo   : Long,
    boardTag         : String,
    isExpanded       : Boolean,
    onToggleExpand   : () -> Unit,
    onSwitchThread   : (WatchedThread) -> Unit,
    onRemove         : (WatchedThread) -> Unit,
    onTogglePolling  : ((WatchedThread, Boolean) -> Unit)? = null,
    modifier         : Modifier = Modifier
) {
    val boardThreads = threads.filter { it.boardTag == boardTag }
    if (boardThreads.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
    ) {

        // ── Expanded grid ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = isExpanded,
            enter   = expandVertically(expandFrom = Alignment.Bottom),
            exit    = shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            LazyVerticalGrid(
                columns               = GridCells.Fixed(3),
                modifier              = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp)
            ) {
                items(boardThreads, key = { it.threadNo }) { thread ->
                    WatchedThreadGridCard(
                        thread         = thread,
                        isActive       = thread.threadNo == activeThreadNo,
                        onSwitchThread = {
                            onSwitchThread(thread)
                            onToggleExpand()
                        },
                        onRemove          = { onRemove(thread) },
                        onTogglePolling   = onTogglePolling?.let { cb ->
                            { enabled -> cb(thread, enabled) }
                        }
                    )
                }
            }
        }

        // ── Compact strip ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Static Board Tag Label
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = "/$boardTag/",
                    color      = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize   = 14.sp
                )
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .fillMaxHeight(0.6f)
                    .width(1.dp)
                    .background(Color.Gray.copy(alpha = 0.5f))
            )

            // Scrollable Watched Threads Chips
            val listState = rememberLazyListState()

            LaunchedEffect(activeThreadNo, boardThreads) {
                val index = boardThreads.indexOfFirst { it.threadNo == activeThreadNo }
                if (index != -1) {
                    listState.scrollToItem(index)
                    val layoutInfo = snapshotFlow { listState.layoutInfo }
                        .first { info -> info.visibleItemsInfo.any { it.index == index } }
                    val itemInfo    = layoutInfo.visibleItemsInfo.first { it.index == index }
                    val viewportWidth = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
                    val offset        = -(viewportWidth / 2 - itemInfo.size / 2)
                    listState.animateScrollToItem(index, offset)
                }
            }

            LazyRow(
                state    = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                itemsIndexed(boardThreads, key = { _, thread -> thread.threadNo }) { index, thread ->
                    WatchedThreadChip(
                        thread          = thread,
                        isActive        = thread.threadNo == activeThreadNo,
                        onSwitchThread  = { onSwitchThread(thread) },
                        onRemove        = { onRemove(thread) }
                    )
                    if (index < boardThreads.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.6f)
                                .width(1.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}

// ── Compact chip in the strip ──────────────────────────────────────────────────

@Composable
private fun WatchedThreadChip(
    thread         : WatchedThread,
    isActive       : Boolean,
    onSwitchThread : () -> Unit,
    onRemove       : () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxHeight()
            .clickable { onSwitchThread() }
            .padding(horizontal = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (thread.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model              = thread.thumbnailUrl,
                    contentDescription = thread.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            if (thread.hasNewPosts) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(NewPostsDot, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text     = thread.title,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = if (isActive) ChanGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.widthIn(max = 80.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint               = Color(0xFFFF5555),
                    modifier           = Modifier.size(16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (isActive) ChanGreen else Color.Transparent, RoundedCornerShape(1.dp))
        )
    }
}

// ── Expanded grid card ─────────────────────────────────────────────────────────

@Composable
private fun WatchedThreadGridCard(
    thread           : WatchedThread,
    isActive         : Boolean,
    onSwitchThread   : () -> Unit,
    onRemove         : () -> Unit,
    onTogglePolling  : ((Boolean) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (isActive) Modifier.border(2.dp, ChanGreen, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable { onSwitchThread() }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (thread.thumbnailUrl.isNotEmpty()) {
                    AsyncImage(
                        model              = thread.thumbnailUrl,
                        contentDescription = thread.title,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("NO IMG", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Polling enabled badge — top-right corner of thumbnail
                if (thread.pollingEnabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .background(ChanGreen.copy(alpha = 0.9f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Polling on",
                            tint     = Color.White,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            // Title
            Text(
                text      = thread.title,
                fontSize  = 10.sp,
                maxLines  = 2,
                overflow  = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )

            // Action row: new-posts dot | bell toggle | X remove
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // New-posts dot (or empty space to maintain layout)
                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    if (thread.hasNewPosts) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(NewPostsDot, CircleShape)
                        )
                    }
                }

                // Bell toggle — only shown when a toggle callback is provided
                if (onTogglePolling != null) {
                    IconButton(
                        onClick  = { onTogglePolling(!thread.pollingEnabled) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector        = if (thread.pollingEnabled)
                                Icons.Default.Notifications
                            else
                                Icons.Default.NotificationsNone,
                            contentDescription = if (thread.pollingEnabled)
                                "Disable background notifications"
                            else
                                "Enable background notifications",
                            tint     = if (thread.pollingEnabled) ChanGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(28.dp))
                }

                // Remove button
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint               = Color(0xFFFF5555),
                        modifier           = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
