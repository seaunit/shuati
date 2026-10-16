package site.seaunit.shuati.practice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import site.seaunit.shuati.ui.practice.RoundCounts

/** 一轮练习的计数与「从头开始」语义（与网页端同一套口径） */
class PracticeRoundTest {

    @Test
    fun `verdicts accumulate into the round counts`() {
        val counts = RoundCounts.Empty
            .plus("CORRECT")
            .plus("PARTIAL")
            .plus("WRONG")
            .plus("PENDING")

        assertEquals(4, counts.answered)
        assertEquals(1, counts.correct)
        assertEquals(1, counts.partial)
        assertEquals(2, counts.wrong)
    }

    @Test
    fun `score is the correct rate of this round`() {
        assertNull(RoundCounts.Empty.score)
        assertEquals(50, RoundCounts.Empty.plus("CORRECT").plus("WRONG").score)
    }

    @Test
    fun `restart resets position and counters but keeps records`() {
        // 从头开始：位置回到第 1 题、本轮计数清零（已提交的作答记录在服务端，不受影响）
        val restarted = RoundCounts.Empty
        assertEquals(0, restarted.answered)
        assertEquals(0, restarted.correct)
        assertNull(restarted.score)
    }
}
