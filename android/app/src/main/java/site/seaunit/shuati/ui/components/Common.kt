package site.seaunit.shuati.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.core.UiState
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.HairlineColor
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Rose
import site.seaunit.shuati.ui.theme.TextGray
import site.seaunit.shuati.ui.theme.TextHint

/**
 * 极简异步三态。
 *
 * 这里不引 ViewModel：应用是单 Activity + Compose 导航，旋转/尺寸变化已在
 * AndroidManifest 里用 configChanges 兜住，屏幕状态不会因此重建，省掉一层工厂样板。
 */
class Loader<T> {
    var state by mutableStateOf<UiState<T>>(UiState.Loading)
        private set

    /** 交给界面上的「重试」按钮：自增一次就让 rememberLoader 重新执行 */
    internal var retry: () -> Unit = {}

    suspend fun run(block: suspend () -> T) {
        state = UiState.Loading
        state = try {
            UiState.Ready(block())
        } catch (e: Throwable) {
            UiState.Failed(e.toUserMessage())
        }
    }
}

@Composable
fun <T> rememberLoader(vararg keys: Any?, block: suspend () -> T): Loader<T> {
    val loader = remember(*keys) { Loader<T>() }
    var attempt by remember(*keys) { mutableIntStateOf(0) }
    loader.retry = { attempt += 1 }
    val current by rememberUpdatedState(block)
    LaunchedEffect(*keys, attempt) { loader.run { current() } }
    return loader
}

@Composable
fun <T> StateHost(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        is UiState.Loading -> Box(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = Moss) }

        is UiState.Failed -> Column(
            modifier = modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.message, color = Rose, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onRetry) { Text("重试") }
        }

        is UiState.Ready -> content(state.data)
    }
}

/** 提示条：错误用 rose，普通提示用 moss；方角薄条，和微信的提示条一样不抢视线 */
@Composable
fun NoticeBar(message: String, modifier: Modifier = Modifier, error: Boolean = false) {
    if (message.isBlank()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        color = if (error) Rose.copy(alpha = 0.08f) else Moss.copy(alpha = 0.08f),
    ) {
        Text(
            text = message,
            color = if (error) Rose else Moss,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** 分组小标题：浅灰小字，块的上面留白由调用方控制 */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = TextGray,
        fontSize = 13.sp,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** 主按钮：通栏、方角、牌子绿底白字（微信那种"绿条"按钮） */
@Composable
fun WeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (danger) Rose else Moss,
            contentColor = CellWhite,
            disabledContainerColor = if (danger) Rose.copy(alpha = 0.4f) else Moss.copy(alpha = 0.4f),
            disabledContentColor = CellWhite,
        ),
        modifier = modifier.fillMaxWidth().height(44.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Text(text, fontSize = 16.sp)
    }
}

/** 行内文字动作（下架 / 删除 / 重命名这类）：微信里就是一段彩色小字 */
@Composable
fun WeTextAction(
    text: String,
    onClick: () -> Unit,
    color: Color = Moss,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = if (enabled) color else TextHint,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** 发丝分隔线；`startIndent` 用来做微信那种"左边留出图标宽度"的缩进线 */
@Composable
fun Hairline(startIndent: Dp = 0.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startIndent)
            .height(0.5.dp)
            .background(HairlineColor),
    )
}
