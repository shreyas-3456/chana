// FILE: ui/components/ChanButton.kt
package com.chan.mimi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

// Why not use Material3 Button?
// Because the chan "LAST REPLIES" style is just styled text, not a full button
// A clickable ChanText is all we need
@Composable
fun ChanButton(
    text     : String,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier
) {
    ChanText(
        text     = text,
        variant  = TextVariant.Label,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.clickable { onClick() }
    )
}