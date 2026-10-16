package site.seaunit.shuati.data.repo

import kotlinx.serialization.builtins.ListSerializer
import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.data.dto.BankUnit
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
}
