package com.chan.mimi.ui.screens.saved

import android.text.Html
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

// ─── Preset interval options (label → seconds) ───────────────────────────────
private val PRESET_INTERVALS = listOf(
    0    to "Off",
    30   to "30s",
    60   to "1m",
    120  to "2m",
    300  to "5m",
    900  to "15m",
    1800 to "30m"
)

/** Formats seconds into a compact label like "2m 30s", "45s", "10m". */
private fun formatSeconds(seconds: Int): String {
    if (seconds <= 0) return "Off"
    val m = seconds / 60
    val s = seconds % 60
    return when {
        m == 0  -> "${s}s"
        s == 0  -> "${m}m"
        else    -> "${m}m ${s}s"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    innerPadding : PaddingValues,
    onThreadClick : (SavedThreadDetail) -> Unit,
    viewModel    : SavedViewModel = viewModel()
) {
    val savedThreads    by viewModel.savedThreads.collectAsStateWithLifecycle()
    val currentInterval by viewModel.pollingInterval.collectAsStateWithLifecycle()
    val secondsToNextPoll by viewModel.secondsToNextPoll.collectAsStateWithLifecycle()

    // Custom interval picker state
    var showCustomSheet by remember { mutableStateOf(false) }

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
                    Spacer(modifier = Modifier.height(8.dp))

                    // ── Sync interval controls ────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Sync Interval: ",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )

                        // Preset chips
                        PRESET_INTERVALS.forEach { (seconds, label) ->
                            val isSelected = currentInterval == seconds
                            SuggestionChip(
                                onClick = { viewModel.setPollingInterval(seconds) },
                                label   = { Text(label, fontSize = 11.sp) },
                                colors  = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (isSelected) ChanGreen.copy(alpha = 0.25f) else Color.Transparent,
                                    labelColor     = if (isSelected) ChanGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                ),
                                border  = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled     = true,
                                    borderColor = if (isSelected) ChanGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }

                        // Custom chip — highlights if current value is not a preset
                        val isCustomActive = PRESET_INTERVALS.none { it.first == currentInterval } && currentInterval > 0
                        SuggestionChip(
                            onClick = { showCustomSheet = true },
                            label   = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Timer,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = if (isCustomActive) formatSeconds(currentInterval) else "Custom…",
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            colors  = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isCustomActive) ChanGreen.copy(alpha = 0.25f) else Color.Transparent,
                                labelColor     = if (isCustomActive) ChanGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            ),
                            border  = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled     = true,
                                    borderColor = if (isCustomActive) ChanGreen else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(savedThreads, key = { "${it.boardTag}_${it.thread.id}" }) { detail ->
                    SavedThreadCard(
                        detail                 = detail,
                        onClick                = { onThreadClick(detail) },
                        onUnsaveClick          = {
                            viewModel.unsaveThread(detail.boardTag, detail.thread.id)
                        },
                        secondsToNextPoll      = secondsToNextPoll,
                        currentIntervalSeconds = currentInterval,
                        onTogglePolling        = { enabled ->
                            viewModel.togglePolling(detail.boardTag, detail.thread.id, enabled)
                        },
                        onSetPollingInterval   = { secs ->
                            viewModel.setPollingInterval(secs)
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // ── Custom interval bottom sheet ──────────────────────────────────────────
    if (showCustomSheet) {
        CustomIntervalSheet(
            currentSeconds = currentInterval,
            onConfirm      = { seconds ->
                viewModel.setPollingInterval(seconds)
                showCustomSheet = false
            },
            onDismiss = { showCustomSheet = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Interval Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomIntervalSheet(
    currentSeconds : Int,
    onConfirm      : (Int) -> Unit,
    onDismiss      : () -> Unit
) {
    val initMinutes = if (currentSeconds > 0) (currentSeconds / 60).toString() else ""
    val initSecs    = if (currentSeconds > 0) (currentSeconds % 60).toString() else ""

    var minutesText by remember { mutableStateOf(initMinutes) }
    var secondsText by remember { mutableStateOf(initSecs) }

    val totalSeconds = remember(minutesText, secondsText) {
        val m = minutesText.toIntOrNull() ?: 0
        val s = secondsText.toIntOrNull() ?: 0
        m * 60 + s
    }

    val isValid = totalSeconds > 0

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        containerColor    = MaterialTheme.colorScheme.surface,
        sheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text       = "Custom Polling Interval",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text     = "Set a custom background sync interval.\nMinimum: 10 seconds.",
                fontSize = 12.sp,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minutes field
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value         = minutesText,
                        onValueChange = { new ->
                            if (new.length <= 3 && new.all { it.isDigit() }) minutesText = new
                        },
                        label         = { Text("Minutes") },
                        placeholder   = { Text("0") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.width(110.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = ChanGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor    = ChanGreen
                        )
                    )
                }

                Text(":", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                // Seconds field
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OutlinedTextField(
                        value         = secondsText,
                        onValueChange = { new ->
                            val v = new.toIntOrNull()
                            if (new.isEmpty() || (v != null && v < 60)) secondsText = new
                        },
                        label         = { Text("Seconds") },
                        placeholder   = { Text("0") },
                        singleLine    = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier      = Modifier.width(110.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = ChanGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor    = ChanGreen
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Live preview
            val previewText = when {
                !isValid        -> "Enter a value greater than 0"
                totalSeconds < 10 -> "Too short — minimum is 10 seconds"
                else            -> "Polls every ${formatSeconds(totalSeconds)}"
            }
            val previewColor = when {
                !isValid || totalSeconds < 10 -> MaterialTheme.colorScheme.error
                else -> ChanGreen
            }
            Text(previewText, fontSize = 12.sp, color = previewColor)

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick  = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) { Text("Cancel") }

                Button(
                    onClick  = { if (isValid && totalSeconds >= 10) onConfirm(totalSeconds) },
                    enabled  = isValid && totalSeconds >= 10,
                    modifier = Modifier.weight(1f),
                    colors   = ButtonDefaults.buttonColors(containerColor = ChanGreen)
                ) { Text("Apply", color = Color.White) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Saved Thread Card
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedThreadCard(
    detail                 : SavedThreadDetail,
    onClick                : () -> Unit,
    onUnsaveClick          : () -> Unit,
    secondsToNextPoll      : Int,
    currentIntervalSeconds : Int,
    onTogglePolling        : (Boolean) -> Unit,
    onSetPollingInterval   : (Int) -> Unit
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

    var showMenu by remember { mutableStateOf(false) }

    Box {
        ChanCard(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick     = onClick,
                    onLongClick = { showMenu = true }
                ),
            onClick = null
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
                    if (detail.pollingEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Polling active",
                            tint = ChanGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        if (secondsToNextPoll > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${secondsToNextPoll}s",
                                color = ChanGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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

        SavedPollingDropdownMenu(
            expanded               = showMenu,
            onDismissRequest       = { showMenu = false },
            detail                 = detail,
            currentIntervalSeconds = currentIntervalSeconds,
            onTogglePolling        = onTogglePolling,
            onSetPollingInterval   = onSetPollingInterval,
            onUnsaveClick          = onUnsaveClick
        )
    }
}

// ── Dropdown Menu Helpers ──────────────────────────────────────────────────────

@Composable
private fun SavedPollingDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    detail: SavedThreadDetail,
    currentIntervalSeconds: Int,
    onTogglePolling: (Boolean) -> Unit,
    onSetPollingInterval: (Int) -> Unit,
    onUnsaveClick: () -> Unit
) {
    DropdownMenu(
        expanded         = expanded,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = { Text(if (detail.pollingEnabled) "Disable background sync" else "Enable background sync") },
            onClick = {
                onDismissRequest()
                onTogglePolling(!detail.pollingEnabled)
            }
        )
        if (detail.pollingEnabled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            DropdownMenuItem(
                text = { Text("Active Interval: ${formatSeconds(currentIntervalSeconds)}") },
                enabled = false,
                onClick = {}
            )
            listOf(30, 60, 120, 300, 900).forEach { secs ->
                val label = formatSeconds(secs)
                DropdownMenuItem(
                    text = { Text("  • Every $label") },
                    onClick = {
                        onDismissRequest()
                        onSetPollingInterval(secs)
                    }
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        DropdownMenuItem(
            text = { Text("Remove from saved", color = Color(0xFFFF5555)) },
            onClick = {
                onDismissRequest()
                onUnsaveClick()
            }
        )
    }
}
