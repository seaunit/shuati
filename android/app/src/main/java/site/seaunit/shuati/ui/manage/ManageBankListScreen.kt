package site.seaunit.shuati.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.SectionBox
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.review.NeedLogin
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat

/** 题库管理入口：列出我能维护的题库（自己的 + 管理员能维护的公共题库） */
@Composable
fun ManageBankListScreen(
    container: AppContainer,
    isLoggedIn: Boolean,
    onOpenBank: (Long, String) -> Unit,
    onLogin: () -> Unit,
) {
    if (!isLoggedIn) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            NeedLogin("登录后可以管理自己的题库")
            Button(
                onClick = onLogin,
                modifier = Modifier.padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("去登录", fontSize = 14.sp) }
        }
        return
    }

    val scope = rememberCoroutineScope()
    val loader = rememberLoader(isLoggedIn) { container.manage.banks() }
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "我维护的题库",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { creating = true; newName = "" }) {
                Text("新建空题库", fontSize = 13.sp, color = Moss)
            }
        }

        StateHost(state = loader.state, onRetry = loader.retry) { banks ->
            if (banks.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("还没有可管理的题库，先新建一个空的", color = Oat, fontSize = 13.sp)
                }
                return@StateHost
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(banks, key = { it.id }) { bank ->
                    ManageBankRow(bank) { onOpenBank(bank.id, bank.name) }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("新建空题库", fontSize = 16.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("题库名称", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error.isNotBlank()) {
                        Column(Modifier.padding(top = 8.dp)) { NoticeBar(error, error = true) }
                    }
                    Text(
                        "建好之后进题库就能新增单元和题目",
                        fontSize = 12.sp,
                        color = Oat,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && newName.isNotBlank(),
                    onClick = {
                        busy = true
                        error = ""
                        scope.launch {
                            try {
                                val id = container.manage.createBank(newName.trim())
                                creating = false
                                if (id != null && id > 0) onOpenBank(id, newName.trim()) else loader.retry()
                            } catch (e: Throwable) {
                                error = e.toUserMessage("新建题库失败")
                            } finally {
                                busy = false
                            }
                        }
                    },
                ) { Text(if (busy) "创建中…" else "创建", color = Moss) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("取消", color = Oat) }
            },
        )
    }
}

@Composable
private fun ManageBankRow(bank: Bank, onClick: () -> Unit) {
    SectionBox(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                bank.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text(if (bank.is_public) "公共题库" else "我的题库", fontSize = 11.sp, color = Oat)
        }
        Text(
            "${bank.unit_count} 个单元 · ${bank.question_count} 道题",
            fontSize = 12.sp,
            color = Oat,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
