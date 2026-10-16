package site.seaunit.shuati.data.repo

import kotlinx.serialization.builtins.ListSerializer
import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.core.JsonSupport
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.data.dto.BankUnit
import site.seaunit.shuati.data.dto.CursorResponse
import site.seaunit.shuati.data.dto.Progress
import site.seaunit.shuati.data.dto.Question

/** 题库 / 单元 / 题目 / 进度：游客也能读（契约 §2） */
class BankRepository(private val api: ApiClient) {

    suspend fun banks(): List<Bank> =
        api.get("/api/banks", ListSerializer(Bank.serializer())).orEmpty()

    suspend fun units(bankId: Long): List<BankUnit> =
        api.get("/api/banks/$bankId/units", ListSerializer(BankUnit.serializer())).orEmpty()

    suspend fun bankQuestions(bankId: Long): List<Question> =
        api.get("/api/banks/$bankId/questions?mode=seq", ListSerializer(Question.serializer()))
            .orEmpty()

    suspend fun unitQuestions(unitId: Long): List<Question> =
        api.get("/api/units/$unitId/questions?mode=seq", ListSerializer(Question.serializer()))
            .orEmpty()

    suspend fun allQuestions(): List<Question> =
        api.get("/api/questions?mode=seq", ListSerializer(Question.serializer())).orEmpty()

    /** 续做用：范围要么是单元、要么是题库、要么全部 */
    suspend fun progress(bankId: Long? = null, unitId: Long? = null): Progress {
        val path = when {
            unitId != null -> "/api/progress/unit/$unitId"
            bankId != null -> "/api/progress/bank/$bankId"
            else -> "/api/progress/all"
        }
        return api.get(path, Progress.serializer()) ?: Progress()
    }

    /** 读练习光标：这个范围里最后停在哪道题；没记过返回 null */
    suspend fun cursor(scopeType: String, scopeId: Long): Long? =
        api.get(
            "/api/progress/cursor?scopeType=$scopeType&scopeId=$scopeId",
            CursorResponse.serializer(),
        )?.questionId

    /** 写/清练习光标：questionId 传 null 表示清空（一轮做完） */
    suspend fun saveCursor(scopeType: String, scopeId: Long, questionId: Long?) {
        api.putVoid(
            "/api/progress/cursor",
            JsonSupport.obj(
                "scopeType" to scopeType,
                "scopeId" to scopeId,
                "questionId" to questionId,
            ),
        )
    }
}
