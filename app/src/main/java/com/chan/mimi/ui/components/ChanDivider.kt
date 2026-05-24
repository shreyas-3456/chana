// FILE: ui/components/ChanDivider.kt
package com.chan.mimi.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chan.mimi.ui.theme.ElevatedDark

@Composable
fun ChanDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier  = modifier,
        thickness = 0.5.dp,
        color     = ElevatedDark
    )
}