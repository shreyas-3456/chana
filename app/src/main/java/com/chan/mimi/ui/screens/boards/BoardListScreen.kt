// FILE: ui/screens/boards/BoardListScreen.kt
package com.chan.mimi.ui.screens.boards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.theme.ChanGreen
import com.chan.mimi.ui.theme.ElevatedDark

@Composable
fun BoardListScreen(
    onBoardClick  : (BoardDto) -> Unit,
    viewModel     : BoardViewModel = viewModel()  // injected automatically
) {
    // collectAsStateWithLifecycle — observes the StateFlow
    // redraws the screen whenever uiState changes
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {

        // ── Loading ───────────────────────────────────────────
        is BoardUiState.Loading -> {
            Box(
                modifier          = Modifier.fillMaxSize(),
                contentAlignment  = Alignment.Center
            ) {
                CircularProgressIndicator(color = ChanGreen)
            }
        }

        // ── Error ─────────────────────────────────────────────
        is BoardUiState.Error -> {
            val message = (uiState as BoardUiState.Error).message
            Box(
                modifier         = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ChanText(
                        text    = "Failed to load boards",
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
                        onClick = { viewModel.loadBoards() }
                    )
                }
            }
        }

        // ── Success ───────────────────────────────────────────
        is BoardUiState.Success -> {
            val boards = (uiState as BoardUiState.Success).boards
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(boards) { board ->
                    BoardCard(
                        board        = board,
                        onBoardClick = onBoardClick
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

// ============================================================
// BOARD CARD
// ============================================================

@Composable
fun BoardCard(
    board        : BoardDto,
    onBoardClick : (BoardDto) -> Unit
) {
    ChanCard(
        modifier = Modifier.fillMaxWidth(),
        onClick  = { onBoardClick(board) }
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Board tag box e.g. "/tv/"
            Surface(
                color    = ElevatedDark,
                shape    = MaterialTheme.shapes.small,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ChanText(
                        text    = "/${board.tag}/",
                        variant = TextVariant.Label,
                        color   = ChanGreen
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                ChanText(
                    text    = board.title,
                    variant = TextVariant.Username,
                    color   = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                ChanText(
                    text     = board.description,
                    variant  = TextVariant.Meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // NSFW badge
            if (board.isSfw == 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    ChanText(
                        text     = "NSFW",
                        variant  = TextVariant.Meta,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        ChanDivider()
    }
}