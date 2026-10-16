package site.seaunit.shuati.core

/** 每屏统一三态，`Failed.message` 一律放服务端原文或断网兜底文案 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Ready<T>(val data: T) : UiState<T>
    data class Failed(val message: String) : UiState<Nothing>
}

fun Throwable.toUserMessage(fallback: String = "网络异常，请稍后重试"): String =
    message?.takeIf { it.isNotBlank() } ?: fallback
