package site.seaunit.shuati.ui.review

import site.seaunit.shuati.data.dto.WrongBookItem

/**
 * 错题本筛选：题库 + 判定。
 *
 * 与网页端 `WrongBookView.vue` 的筛选条件逐字一致：两个维度独立、可叠加，
 * 永远在本地过滤（服务端一次给全量，不再打第二次接口）。
 *
 * 另外说明一条既有行为：后端按题目去重，只保留**最近一次**作答；
 * 最近一次答对了就自动移出错题本，所以同一道题不会在错题本里出现两条。
 */
object WrongBookFilter {

    const val ALL = "ALL"
    const val VERDICT_WRONG = "WRONG"
    const val VERDICT_PARTIAL = "PARTIAL"

    /** 出现过错题的题库名（去重、保持原有顺序） */
    fun banks(items: List<WrongBookItem>): List<String> =
        items.map { it.bank_name }.filter { it.isNotBlank() }.distinct()

    fun apply(
        items: List<WrongBookItem>,
        bank: String = ALL,
        verdict: String = ALL,
    ): List<WrongBookItem> = items.filter { item ->
        (bank == ALL || item.bank_name == bank) &&
            (verdict == ALL || item.verdict == verdict)
    }
}
