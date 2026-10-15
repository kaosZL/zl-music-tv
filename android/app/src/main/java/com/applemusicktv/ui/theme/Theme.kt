package com.applemusicktv.ui.theme

import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

val AppleRed    = Color(0xFFFA233B)
val DeepBlack   = Color(0xFF000000)
val SurfaceDark = Color(0xFF111111)
val OnSurface   = Color(0xFFE5E5E7)
val SubtleGray  = Color(0xFF8E8E93)

/**
 * ZL 修复：中文回退字体的基线度量和默认拉丁字体不同，行高分布会把字形顶到行框上部
 * （汉化后全部文字「靠上」的根因）。全局注入 LineHeightStyle(Center)，
 * 让带 lineHeight 的样式按行框垂直居中；同时经 ProvideTextStyle 给无样式裸 Text 兜底。
 */
private val CenteredLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun TextStyle.centered(): TextStyle = copy(lineHeightStyle = CenteredLineHeight)

private fun centeredTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.centered(),
        displayMedium = base.displayMedium.centered(),
        displaySmall = base.displaySmall.centered(),
        headlineLarge = base.headlineLarge.centered(),
        headlineMedium = base.headlineMedium.centered(),
        headlineSmall = base.headlineSmall.centered(),
        titleLarge = base.titleLarge.centered(),
        titleMedium = base.titleMedium.centered(),
        titleSmall = base.titleSmall.centered(),
        bodyLarge = base.bodyLarge.centered(),
        bodyMedium = base.bodyMedium.centered(),
        bodySmall = base.bodySmall.centered(),
        labelLarge = base.labelLarge.centered(),
        labelMedium = base.labelMedium.centered(),
        labelSmall = base.labelSmall.centered(),
    )
}

@Composable
fun AppleMusicTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary          = AppleRed,
            onPrimary        = Color.White,
            primaryContainer = AppleRed.copy(alpha = 0.15f),
            secondary        = Color(0xFF1D1D1F),
            onSecondary      = OnSurface,
            background       = DeepBlack,
            surface          = SurfaceDark,
            onSurface        = OnSurface,
            onSurfaceVariant = SubtleGray,
            error            = Color(0xFFFF453A),
        ),
        typography = centeredTypography(),
    ) {
        // 裸 Text（只给 fontSize 不给 style 的调用，本 app 大量存在）走这里兜底：
        // 默认行高 1.3 倍字号 + 行框居中，中文不再顶到行框上部
        ProvideTextStyle(
            value = TextStyle(
                lineHeightStyle = CenteredLineHeight,
                platformStyle = null,
            ),
        ) {
            content()
        }
    }
}
