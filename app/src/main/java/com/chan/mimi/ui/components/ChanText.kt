package com.chan.mimi.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

sealed class TextVariant {
    object Username : TextVariant()
    object Body     : TextVariant()
    object Meta     : TextVariant()
    object Label    : TextVariant()
}

@Composable
fun ChanText(
    text     : String,
    variant  : TextVariant = TextVariant.Body,
    color    : Color = Color.Unspecified,
    modifier : Modifier = Modifier,
    maxLines : Int = Int.MAX_VALUE,
    overflow : TextOverflow = TextOverflow.Clip
) {
    val style = when (variant) {
        TextVariant.Username -> MaterialTheme.typography.titleSmall
        TextVariant.Body     -> MaterialTheme.typography.bodyMedium
        TextVariant.Meta     -> MaterialTheme.typography.labelSmall
        TextVariant.Label    -> MaterialTheme.typography.labelMedium
    }

    val resolvedColor = if (color != Color.Unspecified) color
    else MaterialTheme.colorScheme.onBackground  // ← was .background

    Text(
        text     = text,
        style    = style,
        color    = resolvedColor,
        modifier = modifier,
        maxLines = maxLines,
        overflow = overflow
    )
}