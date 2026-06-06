// FILE: ui/components/ChanHtmlText.kt
package com.chan.mimi.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import kotlin.text.Regex

val QuoteGreen  = Color(0xFF789922)
val LinkBlue    = Color(0xFF5B9BD5)
val ReplyOrange = Color(0xFFCC4400)   // >>postNo reply links

private const val REPLY_TAG = "REPLY_LINK"
private const val URL_TAG = "URL_LINK"
private const val LINK_SENTINEL = "\u0000LINK\u0000"
private const val END_LINK_SENTINEL = "\u0000ENDLINK\u0000"
private const val LINK_META_SEPARATOR = "\u0001"
private val HtmlLinkRegex = Regex(
    "<a[^>]*href=(['\"])(.*?)\\1[^>]*>(.*?)</a>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val QuoteSpanRegex = Regex(
    "<span[^>]*class=\"quote\"[^>]*>(.*?)</span>",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)
private val UrlRegex = Regex("""https?://[^\s<>()]+[^\s<>().,!?:;]""")

@Composable
fun ChanHtmlText(
    html           : String,
    modifier       : Modifier = Modifier,
    maxLines       : Int = Int.MAX_VALUE,
    textColor      : Color = MaterialTheme.colorScheme.onSurface,
    textDecoration : TextDecoration? = null,
    onReplyClick   : ((Long) -> Unit)? = null,   // called with postNo when >>postNo tapped
    onPlainTextClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
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
                .replace(QuoteSpanRegex) { match ->
                    "\u0000QUOTE\u0000${match.groupValues[1]}\u0000ENDQUOTE\u0000"
                }
                .replace(HtmlLinkRegex) { match ->
                    val href = match.groupValues[2]
                    val text = match.groupValues[3]
                    "$LINK_SENTINEL$href$LINK_META_SEPARATOR$text$END_LINK_SENTINEL"
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
                    trimmed.contains(LINK_SENTINEL) -> {
                        // A single line can have multiple links — split on the sentinel
                        val parts = trimmed.split(LINK_SENTINEL)
                        parts.forEachIndexed { pi, part ->
                            if (pi == 0) {
                                // text before first link
                                if (part.isNotEmpty()) appendTextWithUrlAnnotations(part)
                            } else {
                                val linkEnd = part.indexOf(END_LINK_SENTINEL)
                                val linkData = if (linkEnd >= 0) part.substring(0, linkEnd) else part
                                val after = if (linkEnd >= 0) {
                                    part.substring(linkEnd + END_LINK_SENTINEL.length)
                                } else {
                                    ""
                                }
                                val separatorIndex = linkData.indexOf(LINK_META_SEPARATOR)
                                val href = if (separatorIndex >= 0) {
                                    linkData.substring(0, separatorIndex)
                                } else {
                                    linkData
                                }
                                val linkText = if (separatorIndex >= 0) {
                                    linkData.substring(separatorIndex + LINK_META_SEPARATOR.length)
                                } else {
                                    linkData
                                }

                                // Is this a >>postNo reply link?
                                val replyNo = Regex("^>>([0-9]+)").find(linkText.trim())?.groupValues?.get(1)?.toLongOrNull()

                                val start = length
                                append(linkText)
                                if (replyNo != null) {
                                    // Orange for reply-to-post links
                                    addStyle(
                                        SpanStyle(
                                            color          = ReplyOrange,
                                            fontWeight     = FontWeight.Medium,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        start,
                                        length
                                    )
                                    addStringAnnotation(REPLY_TAG, replyNo.toString(), start, length)
                                } else {
                                    addStyle(
                                        SpanStyle(
                                            color = LinkBlue,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        start,
                                        length
                                    )
                                    addStringAnnotation(URL_TAG, normalizeUrl(href, linkText), start, length)
                                }
                                if (after.isNotEmpty()) appendTextWithUrlAnnotations(after)
                            }
                        }
                    }

                    // Plain greentext
                    trimmed.startsWith(">") && !trimmed.startsWith(">>") -> {
                        val start = length
                        append(trimmed)
                        addStyle(SpanStyle(color = QuoteGreen), start, length)
                    }

                    else -> appendTextWithUrlAnnotations(trimmed)
                }
                if (index < lines.size - 1) append("\n")
            }
        }
    }

    val textStyle = MaterialTheme.typography.bodyMedium.copy(
        color = textColor,
        textDecoration = textDecoration
    )

    ClickableText(
        text     = annotated,
        style    = textStyle,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
        onClick  = { offset ->
            val replyTarget = annotated.getStringAnnotations(REPLY_TAG, offset, offset)
                .firstOrNull()
                ?.item
                ?.toLongOrNull()
            if (replyTarget != null && onReplyClick != null) {
                onReplyClick(replyTarget)
                return@ClickableText
            }

            val urlTarget = annotated.getStringAnnotations(URL_TAG, offset, offset)
                .firstOrNull()
                ?.item
            if (urlTarget == null) {
                onPlainTextClick?.invoke()
                return@ClickableText
            }
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlTarget)))
            }
        }
    )
}

private fun AnnotatedString.Builder.appendTextWithUrlAnnotations(text: String) {
    var currentIndex = 0
    UrlRegex.findAll(text).forEach { match ->
        if (match.range.first > currentIndex) {
            append(text.substring(currentIndex, match.range.first))
        }
        val start = length
        val url = match.value
        append(url)
        addStyle(
            SpanStyle(
                color = LinkBlue,
                textDecoration = TextDecoration.Underline
            ),
            start,
            length
        )
        addStringAnnotation(URL_TAG, url, start, length)
        currentIndex = match.range.last + 1
    }
    if (currentIndex < text.length) {
        append(text.substring(currentIndex))
    }
}

private fun normalizeUrl(href: String, fallbackText: String): String {
    val candidate = href.ifBlank { fallbackText }.trim()
    return when {
        candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true) -> candidate
        candidate.startsWith("//") -> "https:$candidate"
        candidate.startsWith("/") -> "https://boards.4chan.org$candidate"
        else -> candidate
    }
}
