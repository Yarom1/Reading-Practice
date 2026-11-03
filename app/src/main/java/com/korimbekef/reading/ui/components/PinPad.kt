package com.korimbekef.reading.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korimbekef.reading.ui.theme.KColors
import kotlin.math.roundToInt

/**
 * 4-digit PIN entry with an on-screen keypad. [resetKey] clears the entry (e.g. after a wrong
 * PIN); [errorKey] shakes the dots.
 */
@Composable
fun PinPad(
    title: String,
    onComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    error: String? = null,
    resetKey: Any = Unit,
    errorKey: Int = 0,
) {
    var pin by remember(resetKey) { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(errorKey) {
        if (errorKey > 0) {
            pin = ""
            repeat(3) {
                shake.animateTo(14f, tween(45))
                shake.animateTo(-14f, tween(90))
            }
            shake.animateTo(0f, tween(45))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = KColors.Ink, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KColors.InkSoft, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                Modifier.offset { IntOffset(shake.value.roundToInt(), 0) },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(if (i < pin.length) KColors.Violet else Color.White, CircleShape)
                            .border(2.dp, KColors.Violet, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(error ?: " ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("", "0", "<"))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "" -> Spacer(Modifier.size(72.dp))
                                "<" -> Box(
                                    Modifier
                                        .size(72.dp)
                                        .background(KColors.VioletLight, CircleShape)
                                        .bouncyClick { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "מחיקה", tint = KColors.Ink)
                                }
                                else -> Box(
                                    Modifier
                                        .size(72.dp)
                                        .background(Color.White, CircleShape)
                                        .border(2.dp, KColors.Line, CircleShape)
                                        .bouncyClick {
                                            if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) onComplete(pin)
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(key, fontSize = 28.sp, color = KColors.Ink, style = MaterialTheme.typography.headlineMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
