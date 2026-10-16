package site.seaunit.shuati.ui.practice

/**
 * 一轮练习的统计（与网页端 `PracticeView` 的 counts 同一套口径）。
 *
 * 抽成纯数据是为了让「从头开始 = 位置与计数都回到初始」这条语义能被单测钉住 ——
 * 本机没有模拟器镜像，UI 测试跑不了。
 */
data class RoundCounts(
    val answered: Int = 0,
    val correct: Int = 0,
    val partial: Int = 0,
    val wrong: Int = 0,
) {
    /** 提交一道题后累加；判定口径与网页端一致（PARTIAL 单独记，其余非正确都算错） */
    fun plus(verdict: String?): RoundCounts = when (verdict) {
        "CORRECT" -> copy(answered = answered + 1, correct = correct + 1)
        "PARTIAL" -> copy(answered = answered + 1, partial = partial + 1)
        else -> copy(answered = answered + 1, wrong = wrong + 1)
    }

    /** 本轮正确率，一题没答时给 null（与网页端一致，不显示 0%） */
    val score: Int? get() = if (answered > 0) Math.round(correct * 100f / answered) else null

    companion object {
        val Empty = RoundCounts()
    }
}
