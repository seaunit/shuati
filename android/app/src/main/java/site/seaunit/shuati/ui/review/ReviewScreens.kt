package site.seaunit.shuati.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.data.dto.StatsMe
import site.seaunit.shuati.ui.components.SectionHeader
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.WeButton
import site.seaunit.shuati.ui.components.WeButton
import site.seaunit.shuati.ui.components.WeCell
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat
import site.seaunit.shuati.ui.theme.Rose
import site.seaunit.shuati.ui.theme.TextGray

/** 错题本：字段与网页端一致（题干 / 题库 / 单元 / 判定） */
@Composable
fun WrongBookScreen(
    container: AppContainer,
    isLoggedIn: Boolean,
    onPractice: (List<Long>) -> Unit = {},
) {
    if (!isLoggedIn) {
        NeedLogin("登录后可以查看错题本")
        return
    }
    val loader = rememberLoader(Unit) { container.review.wrongBook() }

    StateHost(state = loader.state, onRetry = loader.retry) { items ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("错题本是空的，说明还没有答错的题", color = TextGray, fontSize = 14.sp)
            }
            return@StateHost
        }
        WrongBookBody(items, onPractice)
    }
}

/** 错题列表 + 筛选：题库与判定两个维度，条件与网页端一致 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WrongBookBody(
    items: List<site.seaunit.shuati.data.dto.WrongBookItem>,
    onPractice: (List<Long>) -> Unit,
) {
    var bank by remember { mutableStateOf(WrongBookFilter.ALL) }
    var verdict by remember { mutableStateOf(WrongBookFilter.ALL) }
    val filtered = WrongBookFilter.apply(items, bank, verdict)

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                text = "按最近一次作答结果统计，同一道题只保留最新一次；答对后自动移出",
                color = TextGray,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CellWhite)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item { SectionHeader("筛选") }
        item {
            Column(Modifier.fillMaxWidth().background(CellWhite).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text("题库", color = TextGray, fontSize = 12.sp)
                // 题库可能有很多个，用 FlowRow 自动换行（网页端就是 flex-wrap）
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    FilterChip("全部", bank == WrongBookFilter.ALL) { bank = WrongBookFilter.ALL }
                    WrongBookFilter.banks(items).forEach { name ->
                        FilterChip(name, bank == name) { bank = name }
                    }
                }
                Text("判定", color = TextGray, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    listOf(
                        WrongBookFilter.ALL to "全部",
                        WrongBookFilter.VERDICT_WRONG to "错误",
                        WrongBookFilter.VERDICT_PARTIAL to "部分正确",
                    ).forEach { (key, label) ->
                        FilterChip(label, verdict == key) { verdict = key }
                    }
                }
                Text(
                    text = "共 ${items.size} 道错题，当前筛选出 ${filtered.size} 道",
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
                // 筛出来就是一批"复习小题库"，直接开练：做对的题会自动从错题本移除
                WeButton(
                    text = "练这批错题（${filtered.size} 道）",
                    enabled = filtered.isNotEmpty(),
                    modifier = Modifier.padding(top = 10.dp),
                    onClick = { onPractice(filtered.map { it.question_id }) },
                )
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text(
                    "没有符合筛选条件的错题",
                    color = TextGray,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().background(CellWhite).padding(16.dp),
                )
            }
        }

        items(filtered, key = { it.record_id }) { item ->
            WeCell(
                title = item.content.orEmpty(),
                subtitle = listOf(item.bank_name, item.unit_name)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                rightText = verdictLabel(item.verdict),
                rightColor = if (item.verdict == "PARTIAL") Oat else Rose,
                showChevron = true,
                divider = true,
                onClick = { onPractice(listOf(item.question_id)) },
            )
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

/** 与网页端同一套 chip：选中是浅绿底绿字，未选是白底黑字 */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Moss else Oat,
        fontSize = 13.sp,
        modifier = Modifier
            .padding(end = 8.dp, bottom = 2.dp)
            .background(
                color = if (selected) Moss.copy(alpha = 0.10f) else Color(0xFFF3F3F3),
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 学习统计：先是累计几个数，再按单元列 */
@Composable
fun StatsScreen(container: AppContainer, isLoggedIn: Boolean) {
    if (!isLoggedIn) {
        NeedLogin("登录后可以查看学习统计")
        return
    }
    val loader = rememberLoader(Unit) { container.review.stats() }

    StateHost(state = loader.state, onRetry = loader.retry) { stats -> StatsBody(stats) }
}

@Composable
private fun StatsBody(stats: StatsMe) {
    val overall = stats.overall
    val rate = if (overall.total > 0) "${Math.round(overall.correct * 100f / overall.total)}%" else "—"

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionHeader("累计") }
        item {
            WeCell(title = "做题数", rightText = "${overall.total}", rightColor = Moss, rightBold = true, divider = true)
        }
        item {
            WeCell(title = "正确率", rightText = rate, rightColor = Moss, rightBold = true, divider = true)
        }
        item { WeCell(title = "回答正确", rightText = "${overall.correct}", divider = true) }
        item { WeCell(title = "部分正确", rightText = "${overall.partial}", divider = true) }
        item { WeCell(title = "回答错误", rightText = "${overall.wrong}", divider = true) }
        if (overall.pending > 0) {
            item { WeCell(title = "待自评", rightText = "${overall.pending}", divider = true) }
        }

        if (stats.byUnit.isNotEmpty()) {
            item { SectionHeader("按单元") }
            items(stats.byUnit.size) { index ->
                val unit = stats.byUnit[index]
                WeCell(
                    title = unit.unit_name,
                    subtitle = unit.bank_name,
                    rightText = "${unit.total} 题 / 正确 ${unit.correct}",
                    divider = true,
                )
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

/** 未登录占位：错题本、统计、我的都要先登录（spec FR-008） */
@Composable
fun NeedLogin(message: String, onLogin: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        SectionHeader("提示")
        Text(
            text = message,
            color = TextGray,
            fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        )
        if (onLogin != null) {
            WeButton("去登录", onClick = onLogin, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

fun verdictLabel(verdict: String?): String = when (verdict) {
    "CORRECT" -> "正确"
    "PARTIAL" -> "部分正确"
    "WRONG" -> "错误"
    "PENDING" -> "待自评"
    else -> "已作答"
}
