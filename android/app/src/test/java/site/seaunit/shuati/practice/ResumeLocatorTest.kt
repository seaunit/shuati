package site.seaunit.shuati.practice

import org.junit.Assert.assertEquals
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

    @Test
    fun `correct option is green selected wrong option is red`() {
        val result = ChoiceResult(verdict = "WRONG", score = 0, correctAnswer = "A")
        assertEquals(OptionMark.CORRECT, AnswerRules.mark("A", setOf("B"), result))
        assertEquals(OptionMark.WRONG, AnswerRules.mark("B", setOf("B"), result))
        assertEquals(OptionMark.NORMAL, AnswerRules.mark("C", setOf("B"), result))
        // 未提交前不着色
        assertEquals(OptionMark.NORMAL, AnswerRules.mark("A", setOf("A"), null))
    }

    @Test
    fun `multi answer marks every correct option`() {
        val result = ChoiceResult(verdict = "PARTIAL", score = 5, correctAnswer = "AB")
        assertEquals(OptionMark.CORRECT, AnswerRules.mark("A", setOf("A", "C"), result))
        assertEquals(OptionMark.CORRECT, AnswerRules.mark("B", setOf("A", "C"), result))
        assertEquals(OptionMark.WRONG, AnswerRules.mark("C", setOf("A", "C"), result))
    }
}
