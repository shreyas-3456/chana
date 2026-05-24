package com.chan.mimi.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ChanCard(
    modifier: Modifier = Modifier,
    onClick  : (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Two versions — clickable and non-clickable
    // Why? A clickable card has a ripple effect, non-clickable doesn't
    if (onClick != null) {
        Card(
            onClick  = onClick,
            modifier = modifier,
            shape    = RoundedCornerShape(8.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    } else {
        Card(
            modifier = modifier,
            shape    = RoundedCornerShape(8.dp),
            colors   = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

