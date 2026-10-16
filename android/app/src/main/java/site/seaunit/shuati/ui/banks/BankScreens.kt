package site.seaunit.shuati.ui.banks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.data.dto.BankUnit
import site.seaunit.shuati.ui.components.SectionBox
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat

/** 首页：题库列表。游客也能看公共题库，只有开始刷题时才要求登录 */
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isLoggedIn) {
                    container.auth.cachedUser()?.email.orEmpty()
                } else {
                    "未登录，仅可浏览公共题库"
                },
                fontSize = 12.sp,
                color = Oat,
                modifier = Modifier.weight(1f),
            )
            // 游客只能看公共题库，给一个明确的登录入口，别让人找了半天
            if (!isLoggedIn) {
                OutlinedButton(onClick = onNeedLogin) {
                    Text("登录", fontSize = 13.sp, color = Moss)
                }
            }
        }

        StateHost(state = loader.state, onRetry = loader.retry) { banks ->
            if (banks.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("还没有可刷的题库", color = Oat, fontSize = 14.sp)
                }
                return@StateHost
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(banks, key = { it.id }) { bank ->
                    BankRow(
                        bank = bank,
                        onClick = { if (isLoggedIn) onOpenBank(bank.id, bank.name) else onNeedLogin() },
                    )
                }
            }
        }
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    SectionBox(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(bank.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
            Text(if (bank.is_public) "公共题库" else "我的题库", fontSize = 11.sp, color = Oat)
        }
        if (!bank.description.isNullOrBlank()) {
            Text(bank.description, fontSize = 12.sp, color = Oat, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "${bank.question_count} 题 · 已做 ${bank.answered_count}",
            fontSize = 12.sp,
            color = Oat,
        )
        if (bank.question_count > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { bank.answered_count.toFloat() / bank.question_count },
                modifier = Modifier.fillMaxWidth(),
                color = Moss,
            )
        }
    }
}

/** 进入题库：看单元与进度，可以整库刷，也可以选单元刷 */
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(bankName, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
            Button(
                onClick = onStartBank,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("整库刷题", fontSize = 13.sp) }
        }

        StateHost(state = loader.state, onRetry = loader.retry) { units ->
            if (units.isEmpty()) {
                Column(Modifier.padding(16.dp)) { Text("这个题库还没有单元", color = Oat, fontSize = 13.sp) }
                return@StateHost
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(units, key = { it.id }) { unit -> UnitRow(unit, onStartUnit) }
            }
        }
    }
}

@Composable
private fun UnitRow(unit: BankUnit, onStartUnit: (Long, String) -> Unit) {
    SectionBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(unit.name, fontSize = 14.sp, color = Ink)
                Text(
                    "${unit.question_count} 题 · 已做 ${unit.answered_count}",
                    fontSize = 12.sp,
                    color = Oat,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            OutlinedButton(onClick = { onStartUnit(unit.id, unit.name) }) {
                Text("开始", fontSize = 13.sp, color = Moss)
            }
        }
    }
}
