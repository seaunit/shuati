package site.seaunit.shuati.data.repo

import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.core.JsonSupport
import site.seaunit.shuati.data.dto.Catalog
import site.seaunit.shuati.data.dto.OrderCreated
import site.seaunit.shuati.data.dto.PointsInfo

/**
 * 点数与套餐：只读 + 下单拿支付地址（契约 §5）。
 *
 * 购买动作本身在浏览器里完成：这里拿到的 `checkoutUrl` 交给系统浏览器打开，
 * 与网页端点套餐走的是同一个下单接口、同一个支付页（spec FR-012）。
 */
class BillingRepository(private val api: ApiClient) {

    suspend fun points(): PointsInfo = api.get("/api/points", PointsInfo.serializer()) ?: PointsInfo()

    suspend fun catalog(): Catalog = api.get("/api/plans", Catalog.serializer()) ?: Catalog()

    suspend fun createPackOrder(packCode: String): OrderCreated? = api.post(
        "/api/orders",
        JsonSupport.obj("kind" to "PACK", "itemCode" to packCode),
        OrderCreated.serializer(),
    )
}
