package site.seaunit.shuati.core

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import site.seaunit.shuati.data.dto.User

/**
 * API 客户端的关键行为回归。
 *
 * 本机没有模拟器镜像，instrumented 测试跑不了，所以把"和后端约定相关"的逻辑
 * 全部收在 ApiClient 一层，用 MockWebServer 直接验证。
 */
class ApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var storage: MemoryCookieStorage
    private lateinit var api: ApiClient
    private var unauthorizedCount = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        storage = MemoryCookieStorage()
        api = ApiClient(
            baseUrl = server.url("/").toString(),
            cookieJar = PersistentCookieJar(storage),
        )
        api.onUnauthorized = { unauthorizedCount += 1 }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `envelope data is decoded and session cookie is reused`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Set-Cookie", "shuati_token=abc123; Path=/")
                .setBody("""{"code":200,"message":"ok","data":{"id":"u1","email":"a@b.com"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"code":200,"message":"ok","data":[]}"""),
        )

        runBlocking {
            val user = api.get("/api/me", User.serializer())
            assertEquals("u1", user?.id)
            assertEquals("a@b.com", user?.email)
            // 第二次请求必须带上第一次下发的 Cookie，否则说明会话没持久化
            api.get("/api/banks", kotlinx.serialization.builtins.ListSerializer(JsonPrimitive.serializer()))
        }

        val first = server.takeRequest()
        assertEquals("/api/me", first.path)
        val second = server.takeRequest()
        assertTrue(second.getHeader("Cookie").orEmpty().contains("shuati_token=abc123"))
    }

    @Test
    fun `write requests carry the security header`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"code":200,"message":"ok","data":null}"""))

        runBlocking {
            api.postVoid("/api/auth/logout", JsonSupport.obj())
        }

        val request = server.takeRequest()
        assertEquals("ShuatiApp", request.getHeader("X-Requested-With"))
    }

    @Test
    fun `server message is surfaced verbatim with its status`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"code":400,"message":"该邮箱已注册，请直接登录","data":null}"""),
        )

        val error = runCatching {
            runBlocking { api.postVoid("/api/auth/register/email-code", JsonSupport.obj("email" to "a@b.com")) }
        }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals("该邮箱已注册，请直接登录", error?.message)
        assertEquals(400, (error as ApiException).status)
    }

    @Test
    fun `401 notifies the session expiry hook`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"code":401,"message":"请先登录","data":null}"""),
        )

        runCatching { runBlocking { api.get("/api/wrong-book", User.serializer()) } }

        assertEquals(1, unauthorizedCount)
    }

    @Test
    fun `non envelope failure falls back to a readable message`() {
        server.enqueue(MockResponse().setResponseCode(502).setBody("<html>bad gateway</html>"))

        val error = runCatching {
            runBlocking { api.get("/api/banks", User.serializer()) }
        }.exceptionOrNull()

        assertEquals("服务器内部错误", error?.message)
    }

    private class MemoryCookieStorage : CookieStorage {
        private var cookies: List<Cookie> = emptyList()

        override fun load(): List<Cookie> = cookies

        override fun save(cookies: List<Cookie>) {
            this.cookies = cookies
        }

        override fun clear() {
            cookies = emptyList()
        }
    }
}
