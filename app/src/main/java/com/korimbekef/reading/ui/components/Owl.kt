package com.korimbekef.reading.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.korimbekef.reading.core.MascotStage
import com.korimbekef.reading.ui.theme.KColors

enum class OwlMood { IDLE, HAPPY, TALKING, CHEER }

/**
 * "Yanshufi", the companion owl, drawn with Canvas so it can blink, talk and cheer.
 * Accessories appear as the child collects stars ([MascotStage]). Animations are short and calm.
 */
@Composable
fun Owl(
    modifier: Modifier = Modifier,
    stage: MascotStage = MascotStage.BABY,
    mood: OwlMood = OwlMood.IDLE,
    size: Dp = 140.dp,
) {
    val transition = rememberInfiniteTransition(label = "owl")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val blink by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                0f at 0
                0f at 3900
                1f at 4000
                0f at 4150
            },
        ),
        label = "blink",
    )
    val flap by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(280, easing = LinearEasing), RepeatMode.Reverse),
        label = "flap",
    )
    val talk by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(170, easing = LinearEasing), RepeatMode.Reverse),
        label = "talk",
    )
    Canvas(modifier.size(size).semantics { contentDescription = stage.title }) {
        val s = this.size.minDimension / 100f
        val jump = when (mood) {
            OwlMood.CHEER -> -5f * flap
            OwlMood.HAPPY -> -3f * bob
            else -> -1.8f * bob
        }
        // soft shadow stays on the ground
        drawOval(
            color = Color.Black.copy(alpha = 0.07f),
            topLeft = Offset(28 * s, 93 * s),
            size = Size(44 * s, 6 * s),
        )
        translate(top = jump * s) {
            drawOwl(s, stage, mood, blink, flap, talk)
        }
    }
}

private fun DrawScope.drawOwl(s: Float, stage: MascotStage, mood: OwlMood, blink: Float, flap: Float, talk: Float) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    fun sz(w: Float, h: Float) = Size(w * s, h * s)

    // ear tufts
    val tufts = Path().apply {
        moveTo(27 * s, 32 * s); lineTo(30 * s, 11 * s); lineTo(43 * s, 25 * s); close()
        moveTo(73 * s, 32 * s); lineTo(70 * s, 11 * s); lineTo(57 * s, 25 * s); close()
    }
    drawPath(tufts, KColors.OwlDark)

    // wings (behind the body when resting, raised when cheering)
    val wingAngle = when (mood) {
        OwlMood.CHEER -> 55f + 25f * flap
        OwlMood.HAPPY -> 25f
        else -> 8f
    }
    rotate(wingAngle, pivot = p(26f, 50f)) {
        drawOval(KColors.OwlDark, topLeft = p(13f, 46f), size = sz(17f, 33f))
    }
    rotate(-wingAngle, pivot = p(74f, 50f)) {
        drawOval(KColors.OwlDark, topLeft = p(70f, 46f), size = sz(17f, 33f))
    }

    // body and belly
    drawOval(KColors.Owl, topLeft = p(22f, 17f), size = sz(56f, 75f))
    drawOval(KColors.OwlBelly, topLeft = p(33f, 50f), size = sz(34f, 37f))
    val feather = Stroke(width = 1.6f * s, cap = StrokeCap.Round)
    listOf(p(42f, 62f), p(50f, 69f), p(58f, 62f), p(46f, 77f), p(54f, 77f)).forEach { c ->
        drawArc(
            color = KColors.OwlDark.copy(alpha = 0.35f),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(c.x - 3.5f * s, c.y - 3.5f * s),
            size = sz(7f, 7f),
            style = feather,
        )
    }

    // cheeks
    drawCircle(KColors.Pink.copy(alpha = 0.45f), radius = 3.6f * s, center = p(30f, 55f))
    drawCircle(KColors.Pink.copy(alpha = 0.45f), radius = 3.6f * s, center = p(70f, 55f))

    // eyes
    val leftEye = p(39f, 42f)
    val rightEye = p(61f, 42f)
    drawCircle(KColors.OwlBelly, radius = 12.8f * s, center = leftEye)
    drawCircle(KColors.OwlBelly, radius = 12.8f * s, center = rightEye)
    if (mood == OwlMood.HAPPY || mood == OwlMood.CHEER) {
        val happy = Stroke(width = 3f * s, cap = StrokeCap.Round)
        listOf(leftEye, rightEye).forEach { c ->
            drawArc(
                color = KColors.Ink,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(c.x - 7f * s, c.y - 4f * s),
                size = sz(14f, 12f),
                style = happy,
            )
        }
    } else {
        listOf(leftEye to p(40f, 43f), rightEye to p(60f, 43f)).forEach { (eye, pupil) ->
            scale(scaleX = 1f, scaleY = (1f - blink * 0.92f), pivot = eye) {
                drawCircle(Color.White, radius = 11f * s, center = eye)
                drawCircle(KColors.Ink, radius = 5.6f * s, center = pupil)
                drawCircle(Color.White, radius = 1.8f * s, center = Offset(pupil.x + 2f * s, pupil.y - 2f * s))
            }
        }
    }

    // beak
    val beakColor = Color(0xFFE8590C)
    if (mood == OwlMood.TALKING) {
        val open = 1.5f + 2.5f * talk
        val upper = Path().apply { moveTo(45.5f * s, 51f * s); lineTo(54.5f * s, 51f * s); lineTo(50f * s, 55.5f * s); close() }
        val lower = Path().apply { moveTo(47f * s, (55.5f + open * 0.4f) * s); lineTo(53f * s, (55.5f + open * 0.4f) * s); lineTo(50f * s, (58f + open) * s); close() }
        drawPath(upper, beakColor)
        drawPath(lower, beakColor)
    } else {
        val beak = Path().apply { moveTo(45.5f * s, 51.5f * s); lineTo(54.5f * s, 51.5f * s); lineTo(50f * s, 58.5f * s); close() }
        drawPath(beak, beakColor)
    }

    // feet
    drawOval(beakColor, topLeft = p(37f, 89f), size = sz(9f, 4.5f))
    drawOval(beakColor, topLeft = p(54f, 89f), size = sz(9f, 4.5f))

    // ---- accessories ----
    if (stage.ordinal >= MascotStage.SCARF.ordinal) {
        drawRoundRect(KColors.Pink, topLeft = p(28f, 60f), size = sz(44f, 8f), cornerRadius = CornerRadius(4f * s))
        rotate(12f, pivot = p(60f, 66f)) {
            drawRoundRect(KColors.Pink, topLeft = p(57f, 64f), size = sz(7f, 17f), cornerRadius = CornerRadius(3f * s))
        }
        drawLine(Color.White.copy(alpha = 0.7f), p(33f, 64f), p(52f, 64f), strokeWidth = 1.4f * s, cap = StrokeCap.Round)
    }
    if (stage.ordinal >= MascotStage.GLASSES.ordinal) {
        val rim = Stroke(width = 2.3f * s)
        drawCircle(KColors.Ink, radius = 12.6f * s, center = leftEye, style = rim)
        drawCircle(KColors.Ink, radius = 12.6f * s, center = rightEye, style = rim)
        drawLine(KColors.Ink, p(48.5f, 41f), p(51.5f, 41f), strokeWidth = 2.3f * s)
    }
    when (stage) {
        MascotStage.CAP -> {
            val board = Path().apply {
                moveTo(28f * s, 17f * s); lineTo(50f * s, 9f * s); lineTo(72f * s, 17f * s); lineTo(50f * s, 25f * s); close()
            }
            drawRoundRect(KColors.Ink, topLeft = p(39f, 17f), size = sz(22f, 9f), cornerRadius = CornerRadius(3f * s))
            drawPath(board, KColors.Ink)
            drawLine(KColors.Yellow, p(50f, 17f), p(67f, 21f), strokeWidth = 1.6f * s)
            drawLine(KColors.Yellow, p(67f, 21f), p(67f, 30f), strokeWidth = 1.6f * s)
            drawCircle(KColors.Yellow, radius = 2.2f * s, center = p(67f, 31f))
        }
        MascotStage.CROWN -> {
            val crown = Path().apply {
                moveTo(35f * s, 23f * s)
                lineTo(37f * s, 8f * s)
                lineTo(43.5f * s, 16f * s)
                lineTo(50f * s, 4f * s)
                lineTo(56.5f * s, 16f * s)
                lineTo(63f * s, 8f * s)
                lineTo(65f * s, 23f * s)
                close()
            }
            drawPath(crown, KColors.Yellow)
            drawPath(crown, KColors.OwlDark, style = Stroke(width = 1.2f * s))
            drawCircle(KColors.Pink, radius = 2f * s, center = p(50f, 16f))
            drawCircle(KColors.Blue, radius = 1.6f * s, center = p(42f, 19f))
            drawCircle(KColors.Teal, radius = 1.6f * s, center = p(58f, 19f))
        }
        else -> Unit
    }
}
