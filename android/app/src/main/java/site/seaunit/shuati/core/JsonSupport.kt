package site.seaunit.shuati.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 全局 JSON 配置：后端返回的字段比客户端用到的多（`select q.*`），
 * 所以必须忽略未知字段，否则任何一次后端加字段都会把 App 打挂。
 *
 * 字段名与后端 JSON 逐字一致（snake_case），不做驼峰转换 —— 少一层映射就少一类错位。
 */
object JsonSupport {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        // 后端有的列是 JSON NULL（例如 question.images 全库为空、tags 大多是空），
        // 序列化出来就是 `"images": null`。声明成非空 List 又带默认值时，
        // 不加这一行会直接抛 "Unexpected null value"，表现就是接口 HTTP 200 但客户端报错。
        coerceInputValues = true
    }

    fun obj(vararg pairs: Pair<String, Any?>): JsonObject =
        JsonObject(pairs.associate { (key, value) -> key to element(value) })

    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
