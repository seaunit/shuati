package site.seaunit.shuati.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * 与网页端同一套色板（frontend/src/styles/main.css）：
 * 白纸黑字 + moss 绿主色，错误用 rose。安卓端不做第二套视觉。
 */
val Ink = Color(0xFF17181A)
val Paper = Color(0xFFFFFFFF)
val Mist = Color(0xFFE9EBEE)
val Moss = Color(0xFF2F6B45)
val Rose = Color(0xFFB42318)
val Oat = Color(0xFF6B7280)

private val ShuatiColors = lightColorScheme(
    primary = Moss,
    onPrimary = Paper,
    secondary = Ink,
    onSecondary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Oat,
    outline = Mist,
    error = Rose,
    onError = Paper,
)

@Composable
fun ShuatiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ShuatiColors, content = content)
}
