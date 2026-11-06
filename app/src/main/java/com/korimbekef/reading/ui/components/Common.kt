package com.korimbekef.reading.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korimbekef.reading.ui.T
import com.korimbekef.reading.ui.theme.KColors
import kotlin.math.sin
import kotlin.random.Random

fun Color.darker(factor: Float = 0.8f): Color = Color(red * factor, green * factor, blue * factor, alpha)

/** Modifier for a press-to-shrink effect; keeps buttons feeling tactile for small hands. */
@Composable
fun Modifier.bouncyClick(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(stiffness = 900f), label = "press")
    return this
        .scale(scale)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/**
 * Text that shrinks to fit its space instead of breaking a word letter by letter or being cut off
 * (long words, narrow phones, a large font set on the device).
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
    minFontSize: TextUnit = 9.sp,
    textAlign: TextAlign = TextAlign.Center,
) {
    val max = if (style.fontSize.isSp) style.fontSize.value else 16f
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = if (color != Color.Unspecified) color else style.color, textAlign = textAlign),
        maxLines = maxLines,
        softWrap = maxLines > 1,
        autoSize = WholeWordsAutoSize(minSp = if (minFontSize.isSp) minFontSize.value else 9f, maxSp = max),
    )
}

/**
 * The biggest font size (in whole sp) at which the text fits its space with every word kept whole:
 * a long word in a narrow button gets smaller instead of being split between two lines.
 */
private data class WholeWordsAutoSize(val minSp: Float, val maxSp: Float) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        var low = minSp.toInt()
        var high = maxSp.toInt()
        if (high <= low) return minSp.sp
        if (fitsWholeWords(performLayout(constraints, text, high.sp), text.text)) return high.sp
        // low is the fallback (used even if it does not fit); high is known not to fit
        while (high - low > 1) {
            val mid = (low + high) / 2
            if (fitsWholeWords(performLayout(constraints, text, mid.sp), text.text)) low = mid else high = mid
        }
        return low.sp
    }
}

private fun fitsWholeWords(layout: TextLayoutResult, text: String): Boolean {
    if (layout.didOverflowWidth || layout.didOverflowHeight) return false
    for (line in 0 until layout.lineCount - 1) {
        val end = layout.getLineEnd(line)
        if (end <= 0 || end >= text.length) continue
        val before = text[end - 1]
        val after = text[end]
        // a line may only end at a space (or after a dash / slash / maqaf), never inside a word
        if (!before.isWhitespace() && !after.isWhitespace() && before !in "-/\u05BE") return false
    }
    return true
}

/** Large rounded "3D" button used across the child screens. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    icon: ImageVector? = null,
    color: Color = KColors.Violet,
    contentColor: Color = Color.White,
    height: Dp = 72.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    enabled: Boolean = true,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, spring(stiffness = 1200f), label = "press")
    val base = if (enabled) color else Color(0xFFD9D3E6)
    val shape = RoundedCornerShape(height / 3)
    Box(
        modifier
            .height(height + 6.dp)
            .clip(shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = 6.dp)
                .background(base.darker(0.78f), shape),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = (6 * press).dp)
                .background(base, shape)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (emoji != null) {
                Text(emoji, fontSize = (height.value * 0.42f).sp)
                Spacer(Modifier.width(10.dp))
            }
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(height * 0.42f))
                Spacer(Modifier.width(8.dp))
            }
            FitText(
                text,
                style = textStyle,
                color = if (enabled) contentColor else KColors.InkSoft,
                maxLines = 2,
                minFontSize = 12.sp,
            )
        }
    }
}

/** A colorful square tile with a big emoji and a label (home menu, games hub). */
@Composable
fun EmojiTile(
    emoji: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    Box(
        modifier
            .shadow(6.dp, RoundedCornerShape(28.dp), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .bouncyClick(onClick = onClick)
            .padding(14.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, fontSize = 40.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = KColors.Ink,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        if (badge != null) {
            Text(
                badge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = KColors.Ink,
            )
        }
    }
}

/** Top bar for child screens: big round back button + title. */
@Composable
fun KidTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(Icons.AutoMirrored.Rounded.ArrowBack, T.BACK, onBack)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = KColors.Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    color: Color = Color.White,
    tint: Color = KColors.Ink,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            .background(if (enabled) color else Color(0xFFEDE9F5))
            .bouncyClick(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) tint else KColors.InkSoft.copy(alpha = 0.5f), modifier = Modifier.size(size * 0.5f))
    }
}

/** Round icon button with a small label underneath (for readers and pre-readers alike). */
@Composable
fun LabeledIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    tint: Color = KColors.Ink,
    enabled: Boolean = true,
) {
    Column(modifier.widthIn(max = 84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RoundIconButton(icon, label, onClick, color = color, tint = tint, enabled = enabled, size = 48.dp)
        Spacer(Modifier.height(4.dp))
        FitText(label, MaterialTheme.typography.labelSmall, color = KColors.InkSoft, maxLines = 2, minFontSize = 8.sp)
    }
}

@Composable
fun SpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Box(
        modifier
            .shadow(4.dp, RoundedCornerShape(22.dp))
            .background(color, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text, style = textStyle, color = KColors.Ink)
    }
}

/** A small card with an emoji, a value and (optionally) a label under it, e.g. ⭐ 42 / כּוֹכָבִים. */
@Composable
fun StatPill(emoji: String, value: String, modifier: Modifier = Modifier, color: Color = Color.White, label: String? = null) {
    Column(
        modifier
            .shadow(3.dp, RoundedCornerShape(18.dp))
            .background(color, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 18.sp)
            Spacer(Modifier.width(4.dp))
            FitText(value, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = KColors.Ink)
        }
        if (label != null) FitText(label, MaterialTheme.typography.labelSmall, color = KColors.InkSoft, minFontSize = 8.sp)
    }
}

/** Progress ring for the daily goal. */
@Composable
fun GoalRing(progress: Float, modifier: Modifier = Modifier, size: Dp = 64.dp, color: Color = KColors.Teal, content: @Composable BoxScope.() -> Unit = {}) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "goal")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.13f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(color.copy(alpha = 0.18f), 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * animated, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}

@Composable
fun StarsRow(filled: Int, total: Int = 3, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(total) { i ->
            Text(if (i < filled) "⭐" else "☆", fontSize = (size.value).sp, color = if (i < filled) Color.Unspecified else KColors.InkSoft.copy(alpha = 0.45f))
        }
    }
}

/** A white rounded card used for content sections. */
@Composable
fun SoftCard(modifier: Modifier = Modifier, color: Color = Color.White, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Box(
        modifier
            .shadow(4.dp, RoundedCornerShape(24.dp))
            .background(color, RoundedCornerShape(24.dp))
            .padding(padding),
    ) { content() }
}

@Composable
fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = KColors.Violet) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) color else Color.White)
            .border(2.dp, if (selected) color else KColors.Line, RoundedCornerShape(50))
            .bouncyClick(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (selected) Color.White else KColors.Ink)
    }
}

private class Particle(
    val x0: Float,
    val y0: Float,
    val vx: Float,
    val vy: Float,
    val w: Float,
    val h: Float,
    val rot0: Float,
    val spin: Float,
    val phase: Float,
    val color: Color,
)

/** Short confetti celebration; restarts whenever [key] changes to a new non-null value. */
@Composable
fun ConfettiBurst(key: Any?, modifier: Modifier = Modifier, durationMs: Int = 2600, count: Int = 80) {
    if (key == null) return
    val progress = remember(key) { Animatable(0f) }
    val particles = remember(key) {
        val r = Random(key.hashCode())
        val colors = listOf(KColors.Pink, KColors.Yellow, KColors.Teal, KColors.Violet, KColors.Orange, KColors.Blue, KColors.Green)
        List(count) {
            Particle(
                x0 = r.nextFloat(),
                y0 = -0.1f - r.nextFloat() * 0.3f,
                vx = (r.nextFloat() - 0.5f) * 0.3f,
                vy = 0.45f + r.nextFloat() * 0.5f,
                w = 8f + r.nextFloat() * 10f,
                h = 5f + r.nextFloat() * 6f,
                rot0 = r.nextFloat() * 360f,
                spin = (r.nextFloat() - 0.5f) * 4f,
                phase = r.nextFloat() * 6.28f,
                color = colors[r.nextInt(colors.size)],
            )
        }
    }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
    }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val alpha = if (t > 0.75f) (1f - t) / 0.25f else 1f
        particles.forEach { p ->
            val x = (p.x0 + p.vx * t + 0.03f * sin(t * 12f + p.phase)) * size.width
            val y = (p.y0 + p.vy * t + 0.35f * t * t) * size.height
            rotate(p.rot0 + p.spin * 360f * t, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    topLeft = Offset(x - p.w / 2, y - p.h / 2),
                    size = Size(p.w, p.h),
                )
            }
        }
    }
}
