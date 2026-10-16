package site.seaunit.shuati.core

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.seaunit.shuati.data.dto.Question

/**
 * 用线上真实返回的形态喂 DTO。
 *
 * 起因：`question.images` 全库是 JSON NULL、`tags` 大多数为 NULL，
 * 接口原样返回 `"images": null`，而 DTO 里是非空 List —— 解码直接抛异常，
 * 界面上只看到"HTTP 200"的报错。这组用例把当时的 JSON 形状固定下来。
 */
class DtoDecodeTest {

    @Test
    fun `question list decodes when json columns are null`() {
        val raw = """
            {"code":200,"message":"ok","data":[
              {"id":342,"unit_id":1,"type":"SINGLE","content":"下面哪个是索引的作用？",
               "options":[{"key":"A","text":"加快查询"},{"key":"B","text":"加快写入"}],
               "difficulty":"MEDIUM","images":null,"tags":null,"status":"ON","explanation":null},
              {"id":343,"unit_id":1,"type":"SHORT","content":"请写出三条硬性要求。",
               "options":null,"difficulty":null,"images":null,"tags":[],
               "status":"ON","explanation":"解析文本"}
            ]}
        """.trimIndent()

        val envelope = JsonSupport.json.decodeFromString(
            ApiEnvelope.serializer(ListSerializer(Question.serializer())),
            raw,
        )

        val questions = envelope.data.orEmpty()
        assertEquals(2, questions.size)
        // null 被收回默认值，而不是抛异常
        assertTrue(questions[0].images.isEmpty())
        assertTrue(questions[0].tags.isEmpty())
        assertEquals("A", questions[0].options?.first()?.key)
        // 简答题选项为 null、解析非空都要保持原样
        assertEquals(null, questions[1].options)
        assertEquals("解析文本", questions[1].explanation)
    }

    /** 反证：不加 coerceInputValues 时同样的 JSON 会炸 —— 这就是当时"HTTP 200 报错"的原因 */
    @Test
    fun `without coercion a null json column throws`() {
        val strict = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val raw = """{"code":200,"message":"ok","data":[
            {"id":1,"unit_id":1,"type":"SINGLE","content":"题干","images":null,"tags":null}]}"""

        val error = runCatching {
            strict.decodeFromString(
                ApiEnvelope.serializer(ListSerializer(Question.serializer())),
                raw,
            )
        }.exceptionOrNull()

        assertTrue(error is kotlinx.serialization.SerializationException)
    }

    @Test
    fun `unknown extra fields do not break decoding`() {
        val raw = """{"code":200,"message":"ok","data":{
            "id":1,"name":"软考真题","description":null,"is_default":false,
            "is_public":true,"owner_id":null,"created_at":"2026-10-15T00:00:00Z",
            "unit_count":3,"question_count":322,"answered_count":0,"brand_new_field":"x"}}"""

        val bank = JsonSupport.json.decodeFromString(
            ApiEnvelope.serializer(site.seaunit.shuati.data.dto.Bank.serializer()),
            raw,
        ).data

        assertEquals("软考真题", bank?.name)
        assertEquals(322, bank?.question_count)
    }
}
