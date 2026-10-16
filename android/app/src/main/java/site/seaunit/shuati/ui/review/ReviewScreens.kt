package site.seaunit.shuati.ui.review

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.data.dto.StatsMe
import site.seaunit.shuati.ui.components.SectionHeader
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.WeButton
import site.seaunit.shuati.ui.components.WeCell
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat
import site.seaunit.shuati.ui.theme.Rose
import site.seaunit.shuati.ui.theme.TextGray

/** 错题本：字段与网页端一致（题干 / 题库 / 单元 / 判定） */
@Composable
fun WrongBookScreen(container: AppContainer, isLoggedIn: Boolean) {
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionHeader("错题") }
            items(items, key = { it.record_id }) { item ->
                WeCell(
                    title = item.content.orEmpty(),
                    subtitle = listOf(item.bank_name, item.unit_name)
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    rightText = verdictLabel(item.verdict),
                    rightColor = if (item.verdict == "PARTIAL") Oat else Rose,
                    divider = true,
                )
            }
            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
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
