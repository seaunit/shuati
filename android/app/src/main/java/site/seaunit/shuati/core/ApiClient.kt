package site.seaunit.shuati.core

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 与后端 API（`/api/` 前缀）的唯一出口。
 *
 * 两条硬约束（契约见 specs/002-android-client/contracts/api-reuse.md）：
 * 1. 写请求必须带 `X-Requested-With: ShuatiApp`，否则 `CustomHeaderFilter` 直接 403；
 * 2. 认证靠 Cookie（后端不认 Authorization 头），所以挂持久化 CookieJar。
 */
class ApiClient(
    private val baseUrl: String,
    cookieJar: okhttp3.CookieJar,
    connectTimeoutSeconds: Long = 15,
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
        // AI 判分 / 解析走大模型，读超时给足
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            val builder = request.newBuilder()
                .header("Accept", "application/json")
            if (request.method != "GET" && request.method != "HEAD") {
                builder.header("X-Requested-With", "ShuatiApp")
            }
            chain.proceed(builder.build())
        }
        .build()

    /** 401 回调：由 UI 层注册，负责清会话并跳登录 */
    var onUnauthorized: (() -> Unit)? = null

    suspend fun <T> get(path: String, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            execute(Request.Builder().url(resolve(path)).get().build(), serializer)
        }

    suspend fun <T> post(path: String, body: JsonElement, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            execute(
                Request.Builder()
                    .url(resolve(path))
                    .post(body.toString().toRequestBody(JSON_MEDIA))
                    .build(),
                serializer,
            )
        }

    suspend fun <T> patch(path: String, body: JsonElement, serializer: KSerializer<T>): T? =
        withContext(Dispatchers.IO) {
            execute(
                Request.Builder()
                    .url(resolve(path))
                    .patch(body.toString().toRequestBody(JSON_MEDIA))
                    .build(),
                serializer,
            )
        }

    /** 不关心返回体（data 为 null）的写请求 */
    suspend fun postVoid(path: String, body: JsonElement = JsonNull) {
        post(path, body, JsonElement.serializer())
    }

    private fun <T> execute(request: Request, serializer: KSerializer<T>): T? {
        http.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val envelope = runCatching {
                JsonSupport.json.decodeFromString(ApiEnvelope.serializer(serializer), raw)
            }.getOrNull()

            val failed = !response.isSuccessful || envelope == null || envelope.code != 200
            if (failed) {
                if (response.code == 401) {
                    onUnauthorized?.invoke()
                }
                throw ApiException(failureMessage(response.code, envelope?.message, raw), response.code)
            }
            return envelope.data
        }
    }

    /** 服务端有文案就用服务端原文（spec FR-009），没有才用本地兜底 */
    private fun failureMessage(status: Int, message: String?, raw: String): String {
        if (!message.isNullOrBlank()) return message
        return when {
            status == 401 -> "请先登录"
            status == 429 -> "请求过于频繁，请稍后再试"
            status >= 500 -> "服务器内部错误"
            raw.isBlank() -> "网络异常，请检查网络后重试（HTTP $status）"
            else -> "请求失败（HTTP $status）"
        }
    }

    private fun resolve(path: String): String =
        if (path.startsWith("http")) path else baseUrl.trimEnd('/') + path

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
