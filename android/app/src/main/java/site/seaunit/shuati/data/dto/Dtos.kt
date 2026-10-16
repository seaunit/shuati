package site.seaunit.shuati.data.dto

import kotlinx.serialization.Serializable

/**
 * 与后端 JSON 一一对应的数据模型。
 *
 * 字段名刻意保持 snake_case 与后端逐字一致（见 specs/002-android-client/data-model.md）：
 * 任何"顺手改成驼峰"的改动都会让两边字段口径分叉，而网页端才是功能定义来源。
 */

@Serializable
data class User(
    val id: String,
    val email: String,
    val nickname: String? = null,
    val role: String = "USER",
    val status: String = "ENABLED",
)

@Serializable
data class Bank(
    val id: Long,
    val name: String,
    val description: String? = null,
    val is_default: Boolean = false,
    val is_public: Boolean = false,
    val unit_count: Int = 0,
    val question_count: Int = 0,
    val answered_count: Int = 0,
)

@Serializable
data class BankUnit(
    val id: Long,
    val bank_id: Long = 0,
    val name: String,
    val sort: Int = 0,
    val question_count: Int = 0,
    val answered_count: Int = 0,
)

@Serializable
data class QuestionOption(val key: String, val text: String)

@Serializable
data class Question(
    val id: Long,
    val unit_id: Long = 0,
    val type: String,
    val content: String,
    val options: List<QuestionOption>? = null,
    val difficulty: String? = null,
    val tags: List<String> = emptyList(),
    val images: List<String> = emptyList(),
    val status: String? = null,
    val explanation: String? = null,
) {
    val isShort: Boolean get() = type == "SHORT"
    val isMulti: Boolean get() = type == "MULTI"
}

@Serializable
data class Progress(
    val total: Int = 0,
    val answered: Int = 0,
    val correct: Int = 0,
    val answeredIds: List<Long> = emptyList(),
)

@Serializable
data class ChoiceResult(
    val recordId: Long? = null,
    val verdict: String,
    val score: Int = 0,
    val correctAnswer: String = "",
)

@Serializable
data class EssayFeedback(
    val hit_points: List<String> = emptyList(),
    val missed_points: List<String> = emptyList(),
    val comment: String? = null,
)

@Serializable
data class EssayResult(
    val recordId: Long? = null,
    val verdict: String,
    val score: Int? = null,
    val feedback: EssayFeedback? = null,
    val referenceAnswer: String? = null,
    val degraded: Boolean = false,
)

@Serializable
data class Explanation(val explanation: String = "")

@Serializable
data class SessionCreated(val id: Long)

@Serializable
data class WrongBookItem(
    val record_id: Long = 0,
    val question_id: Long = 0,
    val verdict: String? = null,
    val score: Int? = null,
    val last_at: String? = null,
    val type: String? = null,
    val content: String? = null,
    val difficulty: String? = null,
    val unit_id: Long? = null,
    val unit_name: String = "",
    val bank_name: String = "",
)

@Serializable
data class StatsOverall(
    val total: Int = 0,
    val correct: Int = 0,
    val partial: Int = 0,
    val wrong: Int = 0,
    val pending: Int = 0,
)

@Serializable
data class StatsUnit(
    val bank_name: String = "",
    val unit_name: String = "",
    val total: Int = 0,
    val correct: Int = 0,
)

@Serializable
data class StatsMe(
    val overall: StatsOverall = StatsOverall(),
    val byUnit: List<StatsUnit> = emptyList(),
)

@Serializable
data class Entitlements(
    val planCode: String = "free",
    val planName: String = "免费版",
    val planExpiresAt: String? = null,
    val monthlyQuota: Int = 0,
    val monthlyUsed: Int = 0,
    val monthlyLeft: Int = 0,
    val bonusBalance: Int = 0,
    val available: Int = 0,
    val lifetimeUsed: Int = 0,
)

@Serializable
data class LedgerItem(
    val id: Long = 0,
    val delta: Int = 0,
    val bucket: String? = null,
    val balance_after: Int = 0,
    val reason: String? = null,
    val note: String? = null,
    val created_at: String? = null,
)

@Serializable
data class PointsInfo(
    val entitlements: Entitlements = Entitlements(),
    val ledger: List<LedgerItem> = emptyList(),
)

@Serializable
data class PointPack(
    val code: String,
    val name: String,
    val price_cents: Int = 0,
    val points: Int = 0,
    val bonus_points: Int = 0,
    val currency: String? = null,
)

@Serializable
data class Catalog(
    val packs: List<PointPack> = emptyList(),
    val signupBonus: Int = 0,
)

@Serializable
data class OrderCreated(
    val orderId: Long? = null,
    val checkoutUrl: String? = null,
    val checkoutError: String? = null,
)

@Serializable
data class CaptchaData(val ticket: String = "", val imageBase64: String = "")

@Serializable
data class EmailCodeSent(
    val cooldownSeconds: Int = 60,
    val expiresInSeconds: Int = 300,
    val codeAllowed: Boolean = true,
)
