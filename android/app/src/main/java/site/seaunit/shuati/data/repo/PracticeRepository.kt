package site.seaunit.shuati.data.repo

import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.core.JsonSupport
import site.seaunit.shuati.data.dto.ChoiceResult
import site.seaunit.shuati.data.dto.EssayResult
import site.seaunit.shuati.data.dto.Explanation
import site.seaunit.shuati.data.dto.SessionCreated

/** 刷题链路：会话、提交、AI 解析、自评（契约 §3） */
class PracticeRepository(private val api: ApiClient) {

    suspend fun startSession(
        scopeType: String,
        scopeId: Long?,
        scopeName: String,
        totalQuestions: Int,
    ): Long? = api.post(
        "/api/practice/sessions",
        JsonSupport.obj(
            "scopeType" to scopeType,
            "scopeId" to scopeId,
            "scopeName" to scopeName,
            "totalQuestions" to totalQuestions,
        ),
        SessionCreated.serializer(),
    )?.id

    /** 会话收尾失败不影响做题，网页端也是忽略错误 */
    suspend fun finishSession(
        id: Long,
        answered: Int,
        correct: Int,
        partial: Int,
        wrong: Int,
        score: Int?,
    ) {
        runCatching {
            api.patch(
                "/api/practice/sessions/$id",
                JsonSupport.obj(
                    "status" to "COMPLETED",
                    "answered" to answered,
                    "correct" to correct,
                    "partial" to partial,
                    "wrong" to wrong,
                    "score" to score,
                ),
                Explanation.serializer(),
            )
        }
    }

    suspend fun submitChoice(
        questionId: Long,
        selected: List<String>,
        durationMs: Long,
    ): ChoiceResult? = api.post(
        "/api/practice/submit-choice",
        JsonSupport.obj(
            "questionId" to questionId,
            "selected" to selected,
            "durationMs" to durationMs,
        ),
        ChoiceResult.serializer(),
    )

    suspend fun submitEssay(
        questionId: Long,
        answer: String,
        durationMs: Long,
    ): EssayResult? = api.post(
        "/api/practice/submit-essay",
        JsonSupport.obj(
            "questionId" to questionId,
            "answer" to answer,
            "durationMs" to durationMs,
        ),
        EssayResult.serializer(),
    )

    /** 解析按题目共享缓存：第一次生成要扣点，之后直接取库里的 */
    suspend fun explanation(questionId: Long): String =
        api.get("/api/practice/questions/$questionId/explanation", Explanation.serializer())
            ?.explanation
            .orEmpty()

    suspend fun selfEval(recordId: Long, verdict: String) {
        api.postVoid(
            "/api/practice/records/$recordId/self-eval",
            JsonSupport.obj("verdict" to verdict),
        )
    }
}
