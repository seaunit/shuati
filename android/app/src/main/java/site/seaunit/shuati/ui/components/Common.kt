package site.seaunit.shuati.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.core.UiState
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.ui.theme.Mist
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Rose

/**
 * 极简异步三态。
 *
 * 这里不引 ViewModel：应用是单 Activity + Compose 导航，旋转/尺寸变化已在
 * AndroidManifest 里用 configChanges 兜住，屏幕状态不会因此重建，省掉一层工厂样板。
 */
class Loader<T> {
    var state by mutableStateOf<UiState<T>>(UiState.Loading)
        private set

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
    val current by rememberUpdatedState(block)
    LaunchedEffect(*keys) { loader.run { current() } }
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

/** 提示条：错误用 rose，普通提示用 moss（与网页端的 notice 条同一套语义） */
@Composable
fun NoticeBar(message: String, modifier: Modifier = Modifier, error: Boolean = false) {
    if (message.isBlank()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = if (error) Rose.copy(alpha = 0.08f) else Moss.copy(alpha = 0.08f),
    ) {
        Text(
            text = message,
            color = if (error) Rose else Moss,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/** 分组容器：与网页端的「卡片」一致，但不再套第二层卡片 */
@Composable
fun SectionBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Mist),
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}
