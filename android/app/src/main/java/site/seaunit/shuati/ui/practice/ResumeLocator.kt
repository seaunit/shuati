package site.seaunit.shuati.ui.practice

import site.seaunit.shuati.data.dto.ChoiceResult
import site.seaunit.shuati.data.dto.Question

/**
 * 续做定位与选项着色。
 *
 * 刻意做成纯函数：与网页端 `PracticeView.resumeProgress` 同一套规则，
 * 但能被 JVM 单测直接覆盖（本机没有模拟器，UI 测试跑不了）。
 */
object ResumeLocator {

    /** 已经答过的题目 id 集合里，找到第一道没答过的题的下标；找不到返回列表长度 - 1 */
    fun nextIndex(questions: List<Question>, answeredIds: Collection<Long>): Int {
        if (questions.isEmpty()) return 0
        val answered = answeredIds.toSet()
        val index = questions.indexOfFirst { it.id !in answered }
        return if (index >= 0) index else questions.lastIndex
    }
}

/** 选项在作答后的呈现状态 */
enum class OptionMark { CORRECT, WRONG, NORMAL }

object AnswerRules {

    /** 正确选项标绿；选错的标红；其余保持原样（与网页端一致） */
    fun mark(optionKey: String, selected: Collection<String>, result: ChoiceResult?): OptionMark {
        if (result == null) return OptionMark.NORMAL
        val correct = result.correctAnswer.replace(",", "").trim().uppercase()
        if (correct.contains(optionKey)) return OptionMark.CORRECT
        if (selected.contains(optionKey)) return OptionMark.WRONG
        return OptionMark.NORMAL
    }

    fun verdictLabel(verdict: String?): String = when (verdict) {
        "CORRECT" -> "回答正确"
        "PARTIAL" -> "部分正确"
        "WRONG" -> "回答错误"
        "PENDING" -> "待自评"
        else -> "已提交"
    }
}
