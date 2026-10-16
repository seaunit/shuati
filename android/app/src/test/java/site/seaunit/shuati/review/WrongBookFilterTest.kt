package site.seaunit.shuati.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.seaunit.shuati.data.dto.WrongBookItem
import site.seaunit.shuati.ui.review.WrongBookFilter

/** 错题本筛选：题库与判定两个维度，与网页端同一套条件 */
class WrongBookFilterTest {

    private fun item(id: Long, bank: String, verdict: String) = WrongBookItem(
        record_id = id,
        question_id = id + 100,
        verdict = verdict,
        bank_name = bank,
        unit_name = "第一章",
        content = "题干 $id",
    )

    private val items = listOf(
        item(1, "软考真题", "WRONG"),
        item(2, "软考真题", "PARTIAL"),
        item(3, "我的题库", "WRONG"),
    )

    @Test
    fun `no filter keeps everything`() {
        assertEquals(3, WrongBookFilter.apply(items).size)
    }

    @Test
    fun `filter by bank`() {
        val filtered = WrongBookFilter.apply(items, bank = "我的题库")
        assertEquals(3 - 2, filtered.size)
        assertEquals(3L, filtered.first().record_id)
    }

    @Test
    fun `filter by verdict`() {
        val filtered = WrongBookFilter.apply(items, verdict = WrongBookFilter.VERDICT_PARTIAL)
        assertEquals(1, filtered.size)
        assertTrue(filtered.all { it.verdict == "PARTIAL" })
    }

    @Test
    fun `bank and verdict stack`() {
        assertTrue(WrongBookFilter.apply(items, bank = "我的题库", verdict = "PARTIAL").isEmpty())
        assertEquals(
            1,
            WrongBookFilter.apply(items, bank = "软考真题", verdict = "WRONG").size,
        )
    }

    @Test
    fun `bank options are distinct and keep order`() {
        assertEquals(listOf("软考真题", "我的题库"), WrongBookFilter.banks(items))
    }
}
