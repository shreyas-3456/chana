package com.chan.mimi.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chan.mimi.data.model.BoardDto
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.theme.ChanGreen

@Composable
fun HomeScreen(
    onBoardClick : (BoardDto) -> Unit,
    onEditBoards : () -> Unit,
    innerPadding : PaddingValues,        // ← received from NavGraph Scaffold
    viewModel    : HomeViewModel
) {
    val savedBoards by viewModel.savedBoards.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)       // ← this pushes content above bottom nav
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .padding(bottom = 80.dp), // room for Edit Boards button
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape    = MaterialTheme.shapes.small,
                        color    = ChanGreen
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ChanText(
                                text    = "C",
                                variant = TextVariant.Username,
                                color   = MaterialTheme.colorScheme.background
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text       = "chana",
                        fontSize   = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (savedBoards.isEmpty()) {
                item {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ChanText(
                            text    = "No boards added yet.\nTap Edit Boards to add some.",
                            variant = TextVariant.Meta,
                            color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(savedBoards) { board ->
                    HomeBoardCard(
                        board        = board,
                        onBoardClick = onBoardClick
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        // Edit Boards button — now guaranteed above bottom nav via innerPadding
        Button(
            onClick  = onEditBoards,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .height(56.dp)
                .align(Alignment.BottomCenter),
            colors = ButtonDefaults.buttonColors(containerColor = ChanGreen),
            shape  = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector        = Icons.Default.Edit,
                contentDescription = null,
                tint               = Color.Black
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text       = "Edit Boards",
                fontWeight = FontWeight.Bold,
                fontSize   = 16.sp,
                color      = Color.Black
            )
        }
    }
}

@Composable
fun HomeBoardCard(
    board        : BoardDto,
    onBoardClick : (BoardDto) -> Unit
) {
    val cardColor = if (board.isSfw == 1) {
        Color(0xFF2D4A2D)
    } else {
        Color(0xFF4A1A1A)
    }

    Card(
        onClick  = { onBoardClick(board) },
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        colors   = CardDefaults.cardColors(containerColor = cardColor)
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