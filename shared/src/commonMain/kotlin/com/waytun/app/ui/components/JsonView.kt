package com.waytun.app.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.waytun.app.ui.icons.AppIcons
import com.waytun.app.res.Res
import com.waytun.app.res.action_copy
import androidx.compose.ui.unit.IntOffset
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waytun.app.ui.theme.Amber
import com.waytun.app.ui.theme.Cyan
import com.waytun.app.ui.theme.Mint
import com.waytun.app.ui.theme.Rose
import com.waytun.app.core.json.JSONObject

/** Pretty-printed JSON text (2-space indent); returns [text] unchanged if it isn't valid JSON. */
fun prettyJson(text: String): String =
    runCatching { JSONObject(text).toString(2).replace("\\/", "/") }.getOrDefault(text)

/** Colors keys, strings, numbers and literals so a long config stays readable. */
fun highlightJson(text: String, key: Color, string: Color, number: Color, literal: Color, punctuation: Color): AnnotatedString =
    buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    var j = i + 1
                    while (j < text.length && text[j] != '"') { if (text[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, text.length)
                    var k = end
                    while (k < text.length && text[k] == ' ') k++
                    val isKey = k < text.length && text[k] == ':'
                    withStyle(SpanStyle(color = if (isKey) key else string)) { append(text.substring(i, end)) }
                    i = end
                }
                c.isDigit() || (c == '-' && i + 1 < text.length && text[i + 1].isDigit()) -> {
                    var j = i + 1
                    while (j < text.length && (text[j].isDigit() || text[j] in ".eE+-")) j++
                    withStyle(SpanStyle(color = number)) { append(text.substring(i, j)) }
                    i = j
                }
                text.startsWith("true", i) || text.startsWith("false", i) || text.startsWith("null", i) -> {
                    val len = if (text.startsWith("false", i)) 5 else 4
                    withStyle(SpanStyle(color = literal)) { append(text.substring(i, i + len)) }
                    i += len
                }
                c in "{}[],:" -> { withStyle(SpanStyle(color = punctuation)) { append(c) }; i++ }
                else -> { append(c); i++ }
            }
        }
    }

/**
 * A read-only, horizontally scrollable code card for a JSON document, drawn line by line in a lazy list: only the lines
 * on screen are ever composed, so even a full Xray config opens instantly and the screen transition stays smooth. The
 * copy button stays in the card's top-right corner.
 */
@Composable
fun JsonLinesCard(lines: List<String>, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val horizontal = rememberScrollState()
    val widest = remember(lines) { lines.maxOfOrNull { it.length } ?: 0 }
    Box(modifier.fillMaxWidth()) {
        GlassCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Monospace 12sp is about 7.3sp per character; the list is as wide as its longest line.
                val needed = with(density) { (widest * 7.4f).sp.toDp() } + 36.dp
                val viewportWidth = maxWidth
                val viewportHeight = maxHeight
                Box(Modifier.horizontalScroll(horizontal)) {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
                        modifier = Modifier.width(maxOf(viewportWidth, needed)).height(viewportHeight)
                    ) {
                        items(lines.size) { index ->
                            Text(
                                remember(lines[index], scheme) {
                                    highlightJson(lines[index], key = Cyan, string = Mint, number = Amber, literal = Rose, punctuation = scheme.onSurfaceVariant)
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                color = scheme.onSurface,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
        GlassIconButton(
            onClick = onCopy,
            size = 44.dp,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(AppIcons.ContentCopy, contentDescription = stringResource(Res.string.action_copy), modifier = Modifier.size(20.dp))
        }
    }
}
