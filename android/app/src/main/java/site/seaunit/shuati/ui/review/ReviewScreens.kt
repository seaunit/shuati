package site.seaunit.shuati.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.data.dto.StatsMe
import site.seaunit.shuati.data.dto.WrongBookItem
import site.seaunit.shuati.ui.components.SectionBox
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat
import site.seaunit.shuati.ui.theme.Rose

/** 错题本：字段与网页端一致（题库 / 单元 / 判定 / 题干） */
@Composable
fun WrongBookScreen(container: AppContainer, isLoggedIn: Boolean) {
    if (!isLoggedIn) {
        NeedLogin("登录后可以查看错题本")
        return
    }
    val loader = rememberLoader(Unit) { container.review.wrongBook() }

    StateHost(state = loader.state, onRetry = { }) { items ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                Text("错题本是空的，说明还没有答错的题", color = Oat, fontSize = 13.sp)
            }
            return@StateHost
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(items, key = { it.record_id }) { item -> WrongRow(item) }
        }
    }
}

@Composable
private fun WrongRow(item: WrongBookItem) {
    SectionBox {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (item.bank_name.isNotBlank()) {
                Text(item.bank_name, fontSize = 11.sp, color = Oat)
            }
            if (item.unit_name.isNotBlank()) {
                Text(item.unit_name, fontSize = 11.sp, color = Oat)
            }
            Text(
                verdictLabel(item.verdict),
                fontSize = 11.sp,
                color = if (item.verdict == "PARTIAL") Ink else Rose,
            )
        }
        Text(
            item.content.orEmpty(),
            fontSize = 14.sp,
            color = Ink,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 学习统计：总量 + 按单元 */
@Composable
fun StatsScreen(container: AppContainer, isLoggedIn: Boolean) {
    if (!isLoggedIn) {
        NeedLogin("登录后可以查看学习统计")
        return
    }
    val loader = rememberLoader(Unit) { container.review.stats() }

    StateHost(state = loader.state, onRetry = { }) { stats -> StatsBody(stats) }
}

@Composable
private fun StatsBody(stats: StatsMe) {
    val overall = stats.overall
    val rate = if (overall.total > 0) "${Math.round(overall.correct * 100f / overall.total)}%" else "—"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionBox {
                Text("累计", fontSize = 12.sp, color = Oat)
                Text("做题 ${overall.total} · 正确率 $rate", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("正确 ${overall.correct}", fontSize = 12.sp, color = Moss)
                    Text("部分正确 ${overall.partial}", fontSize = 12.sp, color = Oat)
                    Text("错误 ${overall.wrong}", fontSize = 12.sp, color = Rose)
                    if (overall.pending > 0) {
                        Text("待自评 ${overall.pending}", fontSize = 12.sp, color = Oat)
                    }
                }
            }
        }
        items(stats.byUnit.size) { index ->
            val unit = stats.byUnit[index]
            SectionBox {
                Text("${unit.bank_name} · ${unit.unit_name}", fontSize = 13.sp, color = Ink)
                Text(
                    "做题 ${unit.total} · 正确 ${unit.correct}",
                    fontSize = 12.sp,
                    color = Oat,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** 未登录占位：错题本、统计、我的都要先登录（spec FR-008） */
@Composable
fun NeedLogin(message: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(message, color = Oat, fontSize = 14.sp)
    }
}

fun verdictLabel(verdict: String?): String = when (verdict) {
    "CORRECT" -> "正确"
    "PARTIAL" -> "部分正确"
    "WRONG" -> "错误"
    "PENDING" -> "待自评"
    else -> "已作答"
}
