package site.seaunit.shuati.core

import kotlinx.serialization.Serializable

/**
 * 后端统一信封（与网页端 `frontend/src/api/client.ts` 同一套语义）：
 * `code != 200` 或 HTTP 非 2xx 一律按错误处理，提示文案取服务端 `message` 原文。
 */
@Serializable
data class ApiEnvelope<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null,
)

/** 接口错误：`message` 是给用户看的原文，`status` 是 HTTP 状态码，便于定位 4xx / 5xx */
class ApiException(message: String, val status: Int) : Exception(message)
