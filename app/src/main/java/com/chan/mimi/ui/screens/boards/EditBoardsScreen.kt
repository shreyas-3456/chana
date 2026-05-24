// FILE: ui/screens/boards/EditBoardsScreen.kt
package com.chan.mimi.ui.screens.boards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.screens.home.HomeViewModel
import com.chan.mimi.ui.theme.ChanGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBoardsScreen(
    onBackClick : () -> Unit,
    onSearch    : () -> Unit,
    viewModel   : HomeViewModel
) {
    val savedTags by viewModel.savedTags.collectAsStateWithLifecycle()
    val allBoards by viewModel.allBoards.collectAsStateWithLifecycle()

    // If allBoards is empty it means the API hasn't loaded yet
    // Trigger a reload when the screen opens
    LaunchedEffect(Unit) {
        if (allBoards.isEmpty()) {
            viewModel.loadAllBoardsIfNeeded()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    ChanText(
                        text    = "Edit Boards ...",
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
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ChanText(
                        text    = savedTags.size.toString(),
                        variant = TextVariant.Username
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick  = onSearch,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3A3A3A)
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Search", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick  = onBackClick,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = ChanGreen
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->

        // ── Loading state ─────────────────────────────────────
        if (allBoards.isEmpty()) {
            Box(
                modifier         = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ChanGreen)
            }
        } else {

            // ── Board list ────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(allBoards) { board ->
                    EditBoardCard(
                        board    = board,
                        isSaved  = board.tag in savedTags,
                        onToggle = { viewModel.toggleBoard(board.tag) }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
fun EditBoardCard(
    board    : BoardDto,
    isSaved  : Boolean,
    onToggle : () -> Unit
) {
    val cardColor = if (board.isSfw == 1) {
        Color(0xFF2D4A2D)
    } else {
        Color(0xFF4A1A1A)
    }

    Card(
        onClick  = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        colors   = CardDefaults.cardColors(
            containerColor = if (isSaved) cardColor
            else cardColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            ChanText(
                text    = board.title,
                variant = TextVariant.Body,
                color   = MaterialTheme.colorScheme.onBackground
            )
            ChanText(
                text    = "/${board.tag}/",
                variant = TextVariant.Meta,
                color   = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}