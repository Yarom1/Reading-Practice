package com.korimbekef.reading.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.korimbekef.reading.R

object KColors {
    val Violet = Color(0xFF6C4BF4)
    val VioletDark = Color(0xFF4A2FC9)
    val VioletLight = Color(0xFFEDE7FF)
    val Orange = Color(0xFFFF8A3D)
    val OrangeLight = Color(0xFFFFE9D9)
    val Teal = Color(0xFF19B6A3)
    val TealLight = Color(0xFFD7F6F1)
    val Pink = Color(0xFFFF6B9A)
    val PinkLight = Color(0xFFFFE3EC)
    val Yellow = Color(0xFFFFD23F)
    val YellowLight = Color(0xFFFFF4C7)
    val Green = Color(0xFF3FBF7F)
    val GreenLight = Color(0xFFD9F5E5)
    val Blue = Color(0xFF3DA9FC)
    val BlueLight = Color(0xFFDDF0FF)
    val Cream = Color(0xFFFFF8EC)
    val Ink = Color(0xFF2D1B69)
    val InkSoft = Color(0xFF6B5E8F)
    val Line = Color(0xFFE9E1F7)

    /** Reading highlight: the word being read right now. */
    val WordNow = Color(0xFFFFD54A)
    val WordNowEdge = Color(0xFFFFA000)

    /** Reading highlight: words already read in this line (soft green). */
    val WordDone = Color(0xFFD3F2DC)

    val Owl = Color(0xFFFFA53D)
    val OwlDark = Color(0xFFF08A24)
    val OwlBelly = Color(0xFFFFE3BD)

    val levelColors = listOf(Green, Blue, Orange, Pink, Violet)
    val levelLight = listOf(GreenLight, BlueLight, OrangeLight, PinkLight, VioletLight)

    fun level(level: Int): Color = levelColors[(level - 1).coerceIn(0, 4)]
    fun levelSoft(level: Int): Color = levelLight[(level - 1).coerceIn(0, 4)]

    private val cardGradients = listOf(
        listOf(Color(0xFFFFE29A), Color(0xFFFFB99A)),
        listOf(Color(0xFFB8F1E6), Color(0xFF8FD3FF)),
        listOf(Color(0xFFFFC9DE), Color(0xFFD7C1FF)),
        listOf(Color(0xFFC9F2B8), Color(0xFFFFE89A)),
        listOf(Color(0xFFD6CCFF), Color(0xFFA8E0FF)),
        listOf(Color(0xFFFFD7B5), Color(0xFFFFA9B8)),
    )

    fun cardBrush(index: Int): Brush = Brush.linearGradient(cardGradients[Math.floorMod(index, cardGradients.size)])
    fun cardBase(index: Int): Color = cardGradients[Math.floorMod(index, cardGradients.size)][0]
}

object KFonts {
    val Fredoka = FontFamily(
        Font(R.font.fredoka_semibold, FontWeight.Medium),
        Font(R.font.fredoka_semibold, FontWeight.SemiBold),
        Font(R.font.fredoka_semibold, FontWeight.Normal),
        Font(R.font.fredoka_bold, FontWeight.Bold),
        Font(R.font.fredoka_bold, FontWeight.ExtraBold),
    )

    /** Reading text: Noto Sans Hebrew has complete nikud support and clear letter shapes. */
    val Reading = FontFamily(
        Font(R.font.noto_hebrew_medium, FontWeight.Normal),
        Font(R.font.noto_hebrew_medium, FontWeight.Medium),
        Font(R.font.noto_hebrew_bold, FontWeight.Bold),
    )
}

private val AppColors = lightColorScheme(
    primary = KColors.Violet,
    onPrimary = Color.White,
    primaryContainer = KColors.VioletLight,
    onPrimaryContainer = KColors.Ink,
    secondary = KColors.Orange,
    onSecondary = Color.White,
    secondaryContainer = KColors.OrangeLight,
    onSecondaryContainer = KColors.Ink,
    tertiary = KColors.Teal,
    onTertiary = Color.White,
    tertiaryContainer = KColors.TealLight,
    onTertiaryContainer = KColors.Ink,
    background = KColors.Cream,
    onBackground = KColors.Ink,
    surface = Color.White,
    onSurface = KColors.Ink,
    surfaceVariant = Color(0xFFF4EFFC),
    onSurfaceVariant = KColors.InkSoft,
    outline = Color(0xFFCFC4E6),
    outlineVariant = KColors.Line,
    error = Color(0xFFD9466B),
)

private fun style(size: Int, weight: FontWeight = FontWeight.SemiBold, line: Float = 1.3f) = TextStyle(
    fontFamily = KFonts.Fredoka,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * line).sp,
)

private val AppTypography = Typography(
    displayLarge = style(48, FontWeight.Bold),
    displayMedium = style(40, FontWeight.Bold),
    displaySmall = style(34, FontWeight.Bold),
    headlineLarge = style(30, FontWeight.Bold),
    headlineMedium = style(26, FontWeight.Bold),
    headlineSmall = style(22, FontWeight.Bold),
    titleLarge = style(21, FontWeight.Bold),
    titleMedium = style(18),
    titleSmall = style(16),
    bodyLarge = style(18, FontWeight.Medium, 1.45f),
    bodyMedium = style(16, FontWeight.Medium, 1.45f),
    bodySmall = style(14, FontWeight.Medium, 1.4f),
    labelLarge = style(17),
    labelMedium = style(15),
    labelSmall = style(13),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** The whole app is Hebrew, so the layout is always right-to-left regardless of device language. */
@Composable
fun KorimTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = AppColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
