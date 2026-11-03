package com.korimbekef.reading.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.korimbekef.reading.core.LineTokens
import com.korimbekef.reading.ui.theme.KColors
import com.korimbekef.reading.ui.theme.KFonts

/**
 * Text style for reading lines. The generous line height plus `Trim.None` leaves room above and
 * below every line, so nikud marks (dots above letters, vowels below them) are never clipped.
 */
fun readingTextStyle(fontSize: TextUnit): TextStyle = TextStyle(
    fontFamily = KFonts.Reading,
    fontWeight = FontWeight.Medium,
    fontSize = fontSize,
    lineHeight = 1.8.em,
    color = KColors.Ink,
    textDirection = TextDirection.Rtl,
    textAlign = TextAlign.Start,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    ),
)

/**
 * One reading line with word highlighting:
 *  - the word being spoken gets a bright yellow "pill" that glides from word to word,
 *  - words already spoken in this line get a soft green background,
 *  - other words stay plain.
 * Tapping reports the tapped word index; a long press opens the word card.
 */
@Composable
fun ReadingLineText(
    text: String,
    tokens: LineTokens,
    currentWord: Int,
    spokenBefore: Int,
    style: TextStyle,
    modifier: Modifier = Modifier,
    onTap: (wordIndex: Int) -> Unit,
    onLongPress: (wordIndex: Int) -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val padH = with(density) { 5.dp.toPx() }
    val edge = with(density) { 2.dp.toPx() }
    val fontPx = with(density) { style.fontSize.toPx() }
    val pill = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    var pillVisible by remember { mutableStateOf(false) }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)

    LaunchedEffect(currentWord, layout, tokens) {
        val l = layout
        if (l == null || currentWord < 0 || currentWord >= tokens.wordCount) {
            pillVisible = false
            return@LaunchedEffect
        }
        val target = wordRect(l, tokens, currentWord, padH, fontPx)
        if (!pillVisible) {
            pill.snapTo(target)
            pillVisible = true
        } else {
            pill.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 700f))
        }
    }

    Text(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier = modifier
            .drawBehind {
                val l = layout ?: return@drawBehind
                val upTo = minOf(spokenBefore, tokens.wordCount)
                for (i in 0 until upTo) {
                    if (i == currentWord) continue
                    val r = wordRect(l, tokens, i, padH, fontPx)
                    drawRoundRect(KColors.WordDone, r.topLeft, r.size, CornerRadius(r.height * 0.28f))
                }
                if (pillVisible && currentWord >= 0) {
                    val r = pill.value
                    val corner = CornerRadius(r.height * 0.28f)
                    drawRoundRect(KColors.WordNow, r.topLeft, r.size, corner)
                    drawRoundRect(KColors.WordNowEdge, r.topLeft, r.size, corner, style = Stroke(width = edge))
                }
            }
            .pointerInput(tokens) {
                detectTapGestures(
                    onTap = { pos ->
                        val l = layout
                        tap(if (l != null) tokens.wordIndexAt(l.getOffsetForPosition(pos)) else -1)
                    },
                    onLongPress = { pos ->
                        val l = layout
                        if (l != null) longPress(tokens.wordIndexAt(l.getOffsetForPosition(pos)))
                    },
                )
            },
    )
}

/** Rectangle around word [index]: horizontal bounds from the layout, vertical from the font size. */
private fun wordRect(l: TextLayoutResult, tokens: LineTokens, index: Int, padH: Float, fontPx: Float): Rect {
    val span = tokens.words[index]
    val end = minOf(span.end, l.layoutInput.text.length)
    val start = minOf(span.start, end)
    val box = l.getPathForRange(start, end).getBounds()
    val line = l.getLineForOffset(start)
    val baseline = l.getLineBaseline(line)
    return Rect(box.left - padH, baseline - fontPx * 1.08f, box.right + padH, baseline + fontPx * 0.52f)
}
