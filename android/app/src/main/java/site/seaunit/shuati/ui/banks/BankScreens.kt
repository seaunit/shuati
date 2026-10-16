package site.seaunit.shuati.ui.banks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.ui.components.SectionHeader
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.WeCell
import site.seaunit.shuati.ui.components.WeTextAction
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.TextGray

/**
 * 首页题库列表。
 *
 * 版式走微信的"分组列表"：浅灰底上先一行账号/游客提示，再是一组白色 cell。
 * 游客能看公共题库，点进去才要求登录（与网页端一致）。
 */
@Composable
fun BankListScreen(
    container: AppContainer,
    isLoggedIn: Boolean,
    onOpenBank: (Long, String) -> Unit,
    onNeedLogin: () -> Unit,
) {
    val loader = rememberLoader(isLoggedIn) { container.banks.banks() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isLoggedIn) {
                    container.auth.cachedUser()?.email.orEmpty()
                } else {
                    "未登录，仅可浏览公共题库"
                },
                fontSize = 13.sp,
                color = TextGray,
                modifier = Modifier.weight(1f),
            )
            if (!isLoggedIn) {
                WeTextAction("登录", onClick = onNeedLogin)
            }
        }

        StateHost(state = loader.state, onRetry = loader.retry) { banks ->
            if (banks.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("还没有可刷的题库", color = TextGray, fontSize = 14.sp)
                }
                return@StateHost
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionHeader("题库") }
                items(banks, key = { it.id }) { bank ->
                    WeCell(
                        title = bank.name,
                        subtitle = "${bank.question_count} 题 · 已做 ${bank.answered_count}",
                        rightText = if (bank.is_public) "公共" else "我的",
                        showChevron = true,
                        divider = true,
                        onClick = {
                            if (isLoggedIn) onOpenBank(bank.id, bank.name) else onNeedLogin()
                        },
                    )
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

/** 题库详情：顶部题库名 + 「整库刷题」，下面是单元列表 */
@Composable
fun UnitListScreen(
    container: AppContainer,
    bankId: Long,
    bankName: String,
    onStartBank: () -> Unit,
    onStartUnit: (Long, String) -> Unit,
    onBack: () -> Unit,
) {
    val loader = rememberLoader(bankId) { container.banks.units(bankId) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = bankName,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            WeTextAction("整库刷题", onClick = onStartBank)
        }

        StateHost(state = loader.state, onRetry = loader.retry) { units ->
            if (units.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("这个题库还没有单元", color = TextGray, fontSize = 14.sp)
                }
                return@StateHost
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionHeader("单元") }
                items(units, key = { it.id }) { unit ->
                    WeCell(
                        title = unit.name,
                        subtitle = "${unit.question_count} 题 · 已做 ${unit.answered_count}",
                        divider = true,
                        onClick = { onStartUnit(unit.id, unit.name) },
                        trailing = {
                            WeTextAction("开始", onClick = { onStartUnit(unit.id, unit.name) })
                        },
                    )
                }
                item { Spacer(Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}
