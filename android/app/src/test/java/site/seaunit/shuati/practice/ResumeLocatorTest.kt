package site.seaunit.shuati.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import site.seaunit.shuati.data.dto.ChoiceResult
import site.seaunit.shuati.data.dto.Question
import site.seaunit.shuati.ui.practice.AnswerRules
import site.seaunit.shuati.ui.practice.OptionMark
import site.seaunit.shuati.ui.practice.ResumeLocator

/** 续做定位与选项着色：与网页端同一套规则，用纯函数固定下来 */
class ResumeLocatorTest {

    private fun question(id: Long) = Question(id = id, type = "SINGLE", content = "题干 $id")

    @Test
    fun `empty list stays at zero`() {
        assertEquals(0, ResumeLocator.nextIndex(emptyList(), emptyList()))
    }

    @Test
    fun `starts from the first unanswered question`() {
        val questions = listOf(question(1), question(2), question(3))
        assertEquals(0, ResumeLocator.nextIndex(questions, emptyList()))
        assertEquals(1, ResumeLocator.nextIndex(questions, listOf(1)))
        assertEquals(2, ResumeLocator.nextIndex(questions, listOf(1, 2)))
    }

    @Test
    fun `answered ids outside the scope do not move the cursor`() {
        val questions = listOf(question(1), question(2))
        assertEquals(0, ResumeLocator.nextIndex(questions, listOf(99)))
    }

    /** 与网页端一致：全部答完 = 从头再来，而不是停在最后一题 */
    @Test
    fun `all answered starts over from the first question`() {
        val questions = listOf(question(1), question(2))
        assertEquals(0, ResumeLocator.nextIndex(questions, listOf(1, 2)))
        assertEquals(0, ResumeLocator.nextIndex(listOf(question(1)), listOf(1)))
    }

    /**
     * 落位规则（specs/004-practice-cursor）：光标优先，其次才是"第一道没答过的题"。
     *
     * 用户场景：答一题 → 从头开始 → 练到第 2 题 → 退出 → 重进要停在第 2 题，
     * 哪怕第 1、2 题以前都答过。
     */
    @Test
    fun `cursor wins over the old first-unanswered rule`() {
        val questions = listOf(question(1), question(2), question(3))
        // 三道题全答过：老规则会回到第 1 题，光标规则要落在第 2 题
        assertEquals(1, ResumeLocator.startIndex(questions, cursorQuestionId = 2, answeredIds = listOf(1, 2, 3)))
        // 光标就在第一题：也按光标走
        assertEquals(0, ResumeLocator.startIndex(questions, cursorQuestionId = 1, answeredIds = listOf(1, 2, 3)))
        // 光标落在最后一道：不再往回退
        assertEquals(2, ResumeLocator.startIndex(questions, cursorQuestionId = 3, answeredIds = emptyList()))
    }

    @Test
    fun `stale cursor falls back to the first unanswered question`() {
        val questions = listOf(question(1), question(2), question(3))
        // 光标题已经不在范围里（被下架/删除）→ 回退到老规则
        assertEquals(2, ResumeLocator.startIndex(questions, cursorQuestionId = 99, answeredIds = listOf(1, 2)))
        // 没有光标 → 老规则
        assertEquals(1, ResumeLocator.startIndex(questions, cursorQuestionId = null, answeredIds = listOf(1)))
        // 没有光标且全答过 → 第 1 题
        assertEquals(0, ResumeLocator.startIndex(questions, cursorQuestionId = null, answeredIds = listOf(1, 2, 3)))
    }

    @Test
    fun `correct option is green selected wrong option is red`() {
        val result = ChoiceResult(verdict = "WRONG", score = 0, correctAnswer = "A")
        assertEquals(OptionMark.CORRECT, AnswerRules.mark("A", setOf("B"), result))
        assertEquals(OptionMark.WRONG, AnswerRules.mark("B", setOf("B"), result))
        assertEquals(OptionMark.NORMAL, AnswerRules.mark("C", setOf("B"), result))
        // 未提交前不着色
        assertEquals(OptionMark.NORMAL, AnswerRules.mark("A", setOf("A"), null))
    }

    /** 解析不能在提交前露出来：解析库里写明了正确选项，提前显示就是泄题 */
    @Test
    fun `explanation stays hidden until the question is submitted`() {
        assertFalse(AnswerRules.canShowExplanation(submitted = false, explanation = "正确选项是 A"))
        assertFalse(AnswerRules.canShowExplanation(submitted = false, explanation = null))
        assertFalse(AnswerRules.canShowExplanation(submitted = true, explanation = ""))
        assertFalse(AnswerRules.canShowExplanation(submitted = true, explanation = null))
        assertTrue(AnswerRules.canShowExplanation(submitted = true, explanation = "正确选项是 A"))
    }

    @Test
    fun `multi answer marks every correct option`() {
        val result = ChoiceResult(verdict = "PARTIAL", score = 5, correctAnswer = "AB")
        assertEquals(OptionMark.CORRECT, AnswerRules.mark("A", setOf("A", "C"), result))
        assertEquals(OptionMark.CORRECT, AnswerRules.mark("B", setOf("A", "C"), result))
        assertEquals(OptionMark.WRONG, AnswerRules.mark("C", setOf("A", "C"), result))
    }
}
