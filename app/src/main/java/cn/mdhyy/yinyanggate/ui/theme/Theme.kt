package cn.mdhyy.yinyanggate.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private val YinErrorLight = Color(0xFFB00020)
private val YinErrorDark = Color(0xFFCF6679)

@Composable
fun YinYangGateTheme(
    flipProgress: Float,
    content: @Composable () -> Unit
) {
    val t = (flipProgress / 180f).coerceIn(0f, 1f)
    // 翻转进度未变时复用同一 ColorScheme 实例，避免每次重组都新建实例导致整棵子树失效重绘。
    val colorScheme = remember(flipProgress) { lerpYinScheme(t) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** 依据翻转进度 t(0=阳面, 1=阴面) 线性插值所有颜色槽位，界面颜色随翻转连续渐变。 */
private fun lerpYinScheme(t: Float): ColorScheme {
    fun c(start: Color, end: Color): Color = lerp(start, end, t)
    return ColorScheme(
        primary = c(YinBlack, YinWhite),
        onPrimary = c(YinWhite, YinBlack),
        primaryContainer = c(YinLightGray, YinDarkGray),
        onPrimaryContainer = c(YinBlack, YinWhite),
        inversePrimary = c(YinWhite, YinBlack),
        secondary = c(YinDarkGray, YinLightGray),
        onSecondary = c(YinWhite, YinBlack),
        secondaryContainer = c(YinLightGray, YinDarkGray),
        onSecondaryContainer = c(YinBlack, YinWhite),
        tertiary = c(YinMidGray, YinMidGray),
        onTertiary = c(YinBlack, YinWhite),
        tertiaryContainer = c(YinLightGray, YinDarkGray),
        onTertiaryContainer = c(YinBlack, YinWhite),
        background = c(YinWhite, YinBlack),
        onBackground = c(YinBlack, YinWhite),
        surface = c(YinWhite, YinBlack),
        onSurface = c(YinBlack, YinWhite),
        surfaceVariant = c(YinLightGray, YinDarkGray),
        onSurfaceVariant = c(YinBlack, YinWhite),
        surfaceTint = c(YinBlack, YinWhite),
        inverseSurface = c(YinDarkGray, YinLightGray),
        inverseOnSurface = c(YinWhite, YinBlack),
        error = c(YinErrorLight, YinErrorDark),
        onError = c(YinWhite, YinBlack),
        errorContainer = c(Color(0xFFF9DEDC), Color(0xFF93000A)),
        onErrorContainer = c(Color(0xFF690005), Color(0xFFF9DEDC)),
        outline = c(YinMidGray, YinMidGray),
        outlineVariant = c(YinLightGray, YinDarkGray),
        scrim = Color.Black,
        surfaceBright = c(YinWhite, Color(0xFF383838)),
        surfaceDim = c(Color(0xFFE6E6E6), YinBlack),
        // 阴面卡片用较亮的浅灰，与纯黑背景区分，避免刺眼、看不清。
        surfaceContainer = c(YinLightGray, Color(0xFF3C3C3C)),
        surfaceContainerHigh = c(YinLightGray, Color(0xFF262626)),
        surfaceContainerHighest = c(Color(0xFFE6E6E6), Color(0xFF2E2E2E)),
        surfaceContainerLow = c(YinWhite, YinBlack),
        surfaceContainerLowest = c(YinWhite, YinBlack),
        primaryFixed = c(YinBlack, YinWhite),
        primaryFixedDim = c(YinMidGray, YinMidGray),
        onPrimaryFixed = c(YinWhite, YinBlack),
        onPrimaryFixedVariant = c(YinWhite, YinBlack),
        secondaryFixed = c(YinDarkGray, YinLightGray),
        secondaryFixedDim = c(YinMidGray, YinMidGray),
        onSecondaryFixed = c(YinWhite, YinBlack),
        onSecondaryFixedVariant = c(YinWhite, YinBlack),
        tertiaryFixed = c(YinMidGray, YinMidGray),
        tertiaryFixedDim = c(YinMidGray, YinMidGray),
        onTertiaryFixed = c(YinBlack, YinWhite),
        onTertiaryFixedVariant = c(YinBlack, YinWhite),
    )
}
