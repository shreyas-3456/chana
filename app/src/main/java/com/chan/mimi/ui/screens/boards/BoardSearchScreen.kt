// FILE: ui/screens/boards/BoardSearchScreen.kt
package com.chan.mimi.ui.screens.boards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chan.mimi.ui.components.*
import com.chan.mimi.ui.screens.home.HomeViewModel
import com.chan.mimi.ui.theme.ChanGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardSearchScreen(
    onBackClick : () -> Unit,
    onDone      : () -> Unit,
    viewModel   : HomeViewModel
) {
    val savedTags  by viewModel.savedTags.collectAsStateWithLifecycle()
    val allBoards  by viewModel.allBoards.collectAsStateWithLifecycle()
    var query      by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Auto focus the search field when screen opens
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Filter boards by query
    val filteredBoards = remember(query, allBoards) {
        if (query.isEmpty()) allBoards
        else allBoards.filter { board ->
            board.title.contains(query, ignoreCase = true) ||
                    board.tag.contains(query, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value         = query,
                        onValueChange = { query = it },
                        placeholder   = {
                            ChanText(
                                text    = "Enter board ...",
                                variant = TextVariant.Meta,
                                color   = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        },
                        singleLine    = true,
                        modifier      = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = ChanGreen,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor             = ChanGreen,
                            focusedTextColor        = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor      = MaterialTheme.colorScheme.onBackground
                        ),
                        trailingIcon  = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
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
                    onClick  = { },
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
                    onClick  = onDone,
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            items(filteredBoards) { board ->
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