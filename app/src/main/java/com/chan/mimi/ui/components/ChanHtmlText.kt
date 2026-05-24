// FILE: ui/components/ChanHtmlText.kt
package com.chan.mimi.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

val QuoteGreen  = Color(0xFF789922)
val LinkBlue    = Color(0xFF5B9BD5)
val ReplyOrange = Color(0xFFCC4400)   // >>postNo reply links

private const val REPLY_TAG = "REPLY_LINK"

@Composable
fun ChanHtmlText(
    html       : String,
    modifier   : Modifier    = Modifier,
    maxLines   : Int         = Int.MAX_VALUE,
    onReplyClick : ((Long) -> Unit)? = null   // called with postNo when >>postNo tapped
) {
    val annotated = remember(html) {
        buildAnnotatedString {
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

            val lines = remaining
                .replace(Regex("<span[^>]*class=\"quote\"[^>]*>(.*?)</span>")) { match ->
                    "\u0000QUOTE\u0000${match.groupValues[1]}\u0000ENDQUOTE\u0000"
                }
                .replace(Regex("<a[^>]*>(.*?)</a>")) { match ->
                    "\u0000LINK\u0000${match.groupValues[1]}\u0000ENDLINK\u0000"
                }
                .replace(Regex("<[^>]+>"), "")
                .split("\n")

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                when {
                    // Greentext span
                    trimmed.contains("\u0000QUOTE\u0000") -> {
                        val content = trimmed
                            .replace("\u0000QUOTE\u0000", ">")
                            .replace("\u0000ENDQUOTE\u0000", "")
                        val start = length
                        append(content)
                        addStyle(SpanStyle(color = QuoteGreen), start, length)
                    }

                    // Reply / board links
                    trimmed.contains("\u0000LINK\u0000") -> {
                        // A single line can have multiple links — split on the sentinel
                        val parts = trimmed.split("\u0000LINK\u0000")
                        parts.forEachIndexed { pi, part ->
                            if (pi == 0) {
                                // text before first link
                                if (part.isNotEmpty()) append(part)
                            } else {
                                val linkEnd = part.indexOf("\u0000ENDLINK\u0000")
                                val linkText = if (linkEnd >= 0) part.substring(0, linkEnd) else part
                                val after    = if (linkEnd >= 0) part.substring(linkEnd + "\u0000ENDLINK\u0000".length) else ""

                                // Is this a >>postNo reply link?
                                val replyNo = Regex("^>>([0-9]+)").find(linkText.trim())?.groupValues?.get(1)?.toLongOrNull()

                                val start = length
                                append(linkText)
                                if (replyNo != null) {
                                    // Orange for reply-to-post links
                                    addStyle(SpanStyle(color = ReplyOrange, fontWeight = FontWeight.Medium), start, length)
                                    addStringAnnotation(REPLY_TAG, replyNo.toString(), start, length)
                                } else {
                                    addStyle(SpanStyle(color = LinkBlue), start, length)
                                }
                                if (after.isNotEmpty()) append(after)
                            }
                        }
                    }

                    // Plain greentext
                    trimmed.startsWith(">") && !trimmed.startsWith(">>") -> {
                        val start = length
                        append(trimmed)
                        addStyle(SpanStyle(color = QuoteGreen), start, length)
                    }

                    else -> append(trimmed)
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor)

    if (onReplyClick != null) {
        ClickableText(
            text     = annotated,
            style    = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
            onClick  = { offset ->
                annotated.getStringAnnotations(REPLY_TAG, offset, offset)
                    .firstOrNull()
                    ?.item
                    ?.toLongOrNull()
                    ?.let { onReplyClick(it) }
            }
        )
    } else {
        androidx.compose.material3.Text(
            text     = annotated,
            style    = textStyle,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}