// FILE: ui/components/ChanHtmlText.kt
package com.chan.mimi.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 4chan green text color
val QuoteGreen = Color(0xFF789922)  // the classic 4chan greentext color
val LinkBlue   = Color(0xFF5B9BD5)

@Composable
fun ChanHtmlText(
    html     : String,
    modifier : Modifier = Modifier,
    maxLines : Int      = Int.MAX_VALUE
) {
    val annotated = remember(html) {
        buildAnnotatedString {
            // Split by HTML tags and process each part
            var remaining = html
                .replace("&#039;", "'")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&gt;", ">")
                .replace("&lt;", "<")
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<wbr>", "")

            // Process line by line for greentext
            val lines = remaining
                .replace(Regex("<span[^>]*class=\"quote\"[^>]*>(.*?)</span>")) { match ->
                    "\u0000QUOTE\u0000${match.groupValues[1]}\u0000ENDQUOTE\u0000"
                }
                .replace(Regex("<a[^>]*>(.*?)</a>")) { match ->
                    "\u0000LINK\u0000${match.groupValues[1]}\u0000ENDLINK\u0000"
                }
                .replace(Regex("<[^>]+>"), "") // strip remaining tags
                .split("\n")

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                when {
                    // Greentext quote spans
                    trimmed.contains("\u0000QUOTE\u0000") -> {
                        val content = trimmed
                            .replace("\u0000QUOTE\u0000", ">")
                            .replace("\u0000ENDQUOTE\u0000", "")
                        val start = length
                        append(content)
                        addStyle(
                            SpanStyle(color = QuoteGreen),
                            start, length
                        )
                    }
                    // Reply links like >>220680173
                    trimmed.contains("\u0000LINK\u0000") -> {
                        val content = trimmed
                            .replace("\u0000LINK\u0000", "")
                            .replace("\u0000ENDLINK\u0000", "")
                        val start = length
                        append(content)
                        addStyle(
                            SpanStyle(color = LinkBlue),
                            start, length
                        )
                    }
                    // Plain greentext starting with >
                    trimmed.startsWith(">") && !trimmed.startsWith(">>") -> {
                        val start = length
                        append(trimmed)
                        addStyle(
                            SpanStyle(color = QuoteGreen),
                            start, length
                        )
                    }
                    else -> append(trimmed)
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    androidx.compose.material3.Text(
        text     = annotated,
        style    = MaterialTheme.typography.bodyMedium,
        color    = MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier
    )
}