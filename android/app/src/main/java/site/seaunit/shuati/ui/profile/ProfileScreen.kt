package site.seaunit.shuati.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.data.dto.LedgerItem
import site.seaunit.shuati.data.dto.PointPack
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.SectionHeader
import site.seaunit.shuati.ui.components.StateHost
import site.seaunit.shuati.ui.components.WeCell
import site.seaunit.shuati.ui.components.WeTextAction
import site.seaunit.shuati.ui.components.rememberLoader
import site.seaunit.shuati.ui.review.NeedLogin
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Rose
import site.seaunit.shuati.ui.theme.TextGray

/**
 * 「我的」：账号 + 点数 + 点数包 + 流水。
 *
 * 购买不在 App 内完成 —— 下单拿到的 checkoutUrl 交给系统浏览器打开，
 * 与网页端是同一个支付页（spec FR-012）。
 */
@Composable
fun ProfileScreen(
    container: AppContainer,
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    if (!isLoggedIn) {
        NeedLogin("登录后可以查看点数与套餐", onLogin)
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
        val entitlements = points.entitlements
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { SectionHeader("账号") }
            item {
                WeCell(
                    title = container.auth.cachedUser()?.email.orEmpty(),
                    subtitle = "当前套餐：${entitlements.planName}",
                    divider = true,
                )
            }
            item {
                WeCell(
                    title = "可用点数",
                    rightText = "${entitlements.available}",
                    rightColor = Moss,
                    rightBold = true,
                    divider = true,
                    trailing = { WeTextAction("刷新", onClick = loader.retry, color = TextGray) },
                )
            }
            item { WeCell(title = "累计消耗", rightText = "${entitlements.lifetimeUsed}", divider = true) }

            if (notice.isNotBlank() || error.isNotBlank()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        NoticeBar(notice)
                        if (error.isNotBlank()) {
                            Spacer(Modifier.padding(top = 6.dp))
                            NoticeBar(error, error = true)
                        }
                    }
                }
            }

            if (catalog.packs.isNotEmpty()) {
                item { SectionHeader("点数包") }
                items(catalog.packs, key = { it.code }) { pack ->
                    WeCell(
                        title = pack.name,
                        subtitle = "${pack.points + pack.bonus_points} 点",
                        divider = true,
                        trailing = {
                            WeTextAction(
                                text = priceLabel(pack),
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
                                                notice = "已打开支付页面，完成后回来点「刷新」"
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                                                )
                                            }
                                        } catch (e: Throwable) {
                                            error = e.toUserMessage("下单失败，请稍后重试")
                                        } finally {
                                            busy = false
                                        }
                                    }
                                },
                            )
                        },
                    )
                }
            }

            if (points.ledger.isNotEmpty()) {
                item { SectionHeader("近期点数流水（近 ${points.ledger.size} 条）") }
                item {
                    // 固定高度 + 内部滚动：流水再多也不把页面撑长（与网页端 max-h-80 同一口径）
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .background(CellWhite),
                    ) {
                        items(points.ledger, key = { it.id }) { row ->
                            LedgerCell(row)
                        }
                    }
                }
            }

            item { Spacer(Modifier.padding(bottom = 16.dp)) }
            item {
                WeCell(
                    title = "退出登录",
                    rightText = "",
                    onClick = { scope.launch { container.auth.logout(); onLoggedOut() } },
                )
            }
            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun LedgerCell(row: LedgerItem) {
    WeCell(
        title = row.note ?: reasonLabel(row.reason),
        subtitle = "余额 ${row.balance_after} · ${row.created_at.orEmpty().take(10)}",
        rightText = if (row.delta >= 0) "+${row.delta}" else "${row.delta}",
        rightColor = if (row.delta >= 0) Moss else Ink,
        divider = true,
    )
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
