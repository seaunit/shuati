package site.seaunit.shuati.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 品牌色沿用网页端（frontend/src/styles/main.css）：moss 绿主色、rose 报错。
 *
 * 版式走微信那套：浅灰页面底 + 白色分组块 + 发丝分隔线，
 * 不再用"圆角白卡片 + 灰边框"那一层（那套在手机上看又碎又吵）。
 */
val Ink = Color(0xFF17181A)
val Paper = Color(0xFFFFFFFF)
val Mist = Color(0xFFE9EBEE)
val Moss = Color(0xFF2F6B45)
val Rose = Color(0xFFB42318)
val Oat = Color(0xFF6B7280)

val PageGray = Color(0xFFEDEDED)
val CellWhite = Color(0xFFFFFFFF)
val HairlineColor = Color(0xFFE6E6E6)
val TextHint = Color(0xFFB2B2B2)
val TextGray = Color(0xFF7A7E83)

private val ShuatiColors = lightColorScheme(
    primary = Moss,
    onPrimary = Paper,
    secondary = Ink,
    onSecondary = Paper,
    background = PageGray,
    onBackground = Ink,
    surface = CellWhite,
    onSurface = Ink,
    surfaceVariant = CellWhite,
    onSurfaceVariant = TextGray,
    // 这几个角色决定 NavigationBar / Dialog / Card 的默认底色。
    // 不显式给值就会落到 Material3 基线色（淡紫），底栏发紫就是这么来的。
    surfaceContainerLowest = CellWhite,
    surfaceContainerLow = CellWhite,
    surfaceContainer = CellWhite,
    surfaceContainerHigh = CellWhite,
    surfaceContainerHighest = PageGray,
    outline = HairlineColor,
    outlineVariant = HairlineColor,
    error = Rose,
    onError = Paper,
)

@Composable
fun ShuatiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ShuatiColors, content = content)
}
