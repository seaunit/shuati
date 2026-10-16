package site.seaunit.shuati.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.data.dto.Catalog
import site.seaunit.shuati.data.dto.LedgerItem
import site.seaunit.shuati.data.dto.PointPack
import site.seaunit.shuati.data.dto.PointsInfo
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.SectionBox
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.review.NeedLogin
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat

/**
 * 「我的」：账号 + 点数 + 点数包。购买不在 App 内完成 ——
 * 下单拿到的 checkoutUrl 交给系统浏览器打开，与网页端是同一个支付页（spec FR-012）。
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    if (!isLoggedIn) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            NeedLogin("登录后可以查看点数与套餐")
            Button(
                onClick = onLogin,
                modifier = Modifier.padding(top = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text("去登录", fontSize = 14.sp) }
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    val loader = rememberLoader(isLoggedIn) {
        Pair(container.billing.points(), container.billing.catalog())
    }

    StateHost(state = loader.state, onRetry = loader.retry) { (points, catalog) ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SectionBox {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(
                            container.auth.cachedUser()?.email.orEmpty(),
                            fontSize = 14.sp,
                            color = Ink,
                            modifier = Modifier.weight(1f),
                        )
                        // 支付在浏览器里完成，回来后点一下刷新就能看到新余额
                        TextButton(onClick = loader.retry) {
                            Text("刷新", color = Oat, fontSize = 13.sp)
                        }
                    }
                    Text(
                        "当前套餐：${points.entitlements.planName}",
                        fontSize = 12.sp,
                        color = Oat,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "${points.entitlements.available} 点可用",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Moss,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            item { NoticeBar(notice) }
            item { NoticeBar(error, error = true) }

            if (catalog.packs.isNotEmpty()) {
                item { Text("点数包", fontSize = 13.sp, color = Oat, modifier = Modifier.padding(top = 4.dp)) }
                items(catalog.packs, key = { it.code }) { pack ->
                    PackRow(
                        pack = pack,
                        enabled = !busy,
                        onClick = {
                            busy = true
                            error = ""
                            scope.launch {
                                try {
                                    val order = container.billing.createPackOrder(pack.code)
                                    val url = order?.checkoutUrl
                                    if (url.isNullOrBlank()) {
                                        error = order?.checkoutError ?: "暂时拿不到支付地址，请稍后再试"
                                    } else {
                                        notice = "已打开支付页面，完成后回到 App 重新进入本页刷新"
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                } catch (e: Throwable) {
                                    error = e.toUserMessage("下单失败，请稍后重试")
                                } finally {
                                    busy = false
                                }
                            }
                        },
                    )
                }
            }

            if (points.ledger.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 4.dp)) {
                        Text(
                            "近期点数流水（近 ${points.ledger.size} 条）",
                            fontSize = 13.sp,
                            color = Oat,
                        )
                        // 固定高度的滚动区，和网页端「点数明细」的 max-h-80 overflow-y-auto 一个意思：
                        // 流水再多也不把页面撑长
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                                .padding(top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(points.ledger, key = { it.id }) { row -> LedgerRow(row) }
                        }
                    }
                }
            }

            item {
                TextButton(
                    onClick = { scope.launch { container.auth.logout(); onLoggedOut() } },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("退出登录", color = Oat, fontSize = 13.sp) }
            }
        }
    }
}

@Composable
private fun LedgerRow(row: LedgerItem) {
    SectionBox {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                row.note ?: reasonLabel(row.reason),
                fontSize = 13.sp,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (row.delta >= 0) "+${row.delta}" else "${row.delta}",
                fontSize = 13.sp,
                color = if (row.delta >= 0) Moss else Ink,
            )
        }
        Text(
            "余额 ${row.balance_after} · ${row.created_at.orEmpty().take(10)}",
            fontSize = 11.sp,
            color = Oat,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PackRow(pack: PointPack, enabled: Boolean, onClick: () -> Unit) {
    SectionBox {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(pack.name, fontSize = 15.sp, color = Ink, fontWeight = FontWeight.Medium)
                Text(
                    "${pack.points + pack.bonus_points} 点",
                    fontSize = 12.sp,
                    color = Oat,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
            ) { Text(priceLabel(pack), fontSize = 13.sp) }
        }
    }
}

/** 价格是分，展示成元；币种以接口返回为准（海外收款是 USD） */
private fun priceLabel(pack: PointPack): String {
    val yuan = pack.price_cents / 100.0
    val currency = pack.currency?.uppercase() ?: "CNY"
    val text = if (yuan % 1.0 == 0.0) yuan.toInt().toString() else String.format("%.2f", yuan)
    return if (currency == "CNY") "¥$text 购买" else "$currency $text"
}

private fun reasonLabel(reason: String?): String = when (reason) {
    "JUDGE" -> "AI 判分"
    "EXPLAIN" -> "AI 解析"
    "IMPORT" -> "文档解析"
    "PURCHASE" -> "购买点数"
    "SIGNUP" -> "注册赠送"
    "REFUND" -> "退款退回"
    else -> reason.orEmpty().ifBlank { "点数变动" }
}
