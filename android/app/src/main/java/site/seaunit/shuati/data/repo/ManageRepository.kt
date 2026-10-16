package site.seaunit.shuati.data.repo

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.core.JsonSupport
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.data.dto.BankUnit
import site.seaunit.shuati.data.dto.EmailCodeSent
import site.seaunit.shuati.data.dto.IdResponse
import site.seaunit.shuati.data.dto.Question

/**
 * 题库管理接口（`/api/manage/` 前缀）。
 *
 * 这些接口在网页端早就存在，安卓端只是复用同一套契约：权限由服务端按"谁有维护权"判定，
 * 客户端不做任何本地权限判断，也不能因为"是 App"就放宽。
 */
class ManageRepository(private val api: ApiClient) {

    // ---- 题库 ----

    suspend fun banks(): List<Bank> =
        api.get("/api/manage/banks", ListSerializer(Bank.serializer())).orEmpty()

    suspend fun createBank(name: String): Long? = api.post(
        "/api/manage/banks",
        JsonSupport.obj("name" to name),
        IdResponse.serializer(),
    )?.id

    suspend fun updateBank(id: Long, name: String, description: String) {
        api.patchVoid(
            "/api/manage/banks/$id",
            JsonSupport.obj("name" to name, "description" to description),
        )
    }

    /** 删题库要先发邮箱验证码（服务端用独立的 DELETE_BANK 用途，别的验证码不能顶替） */
    suspend fun sendDeleteCode(id: Long): EmailCodeSent? = api.post(
        "/api/manage/banks/$id/delete-code",
        JsonSupport.obj(),
        EmailCodeSent.serializer(),
    )

    suspend fun deleteBank(id: Long, code: String) {
        api.postVoid("/api/manage/banks/$id/delete", JsonSupport.obj("code" to code))
    }

    // ---- 单元 ----

    suspend fun units(bankId: Long): List<BankUnit> =
        api.get("/api/manage/banks/$bankId/units", ListSerializer(BankUnit.serializer())).orEmpty()

    suspend fun createUnit(bankId: Long, name: String): Long? = api.post(
        "/api/manage/units",
        JsonSupport.obj("bankId" to bankId, "name" to name),
        IdResponse.serializer(),
    )?.id

    suspend fun renameUnit(id: Long, name: String) {
        api.patchVoid("/api/manage/units/$id", JsonSupport.obj("name" to name))
    }

    suspend fun deleteUnit(id: Long) {
        api.deleteVoid("/api/manage/units/$id")
    }

    // ---- 题目 ----

    suspend fun questions(bankId: Long, status: String, keyword: String): List<Question> {
        val params = StringBuilder("?status=$status")
        if (keyword.isNotBlank()) {
            params.append("&q=").append(java.net.URLEncoder.encode(keyword, "UTF-8"))
        }
        return api.get(
            "/api/manage/banks/$bankId/questions$params",
            ListSerializer(Question.serializer()),
        ).orEmpty()
    }

    suspend fun createQuestion(payload: JsonObject) {
        api.postVoid("/api/manage/questions", payload)
    }

    suspend fun updateQuestion(id: Long, payload: JsonObject) {
        // 后端编辑题目是 PUT（改写整题），不是 PATCH
        api.putVoid("/api/manage/questions/$id", payload)
    }

    suspend fun setQuestionStatus(id: Long, status: String) {
        api.patchVoid("/api/manage/questions/$id/status", JsonSupport.obj("status" to status))
    }

    suspend fun deleteQuestion(id: Long) {
        api.deleteVoid("/api/manage/questions/$id")
    }
}
