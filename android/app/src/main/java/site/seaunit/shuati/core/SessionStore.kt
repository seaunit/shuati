package site.seaunit.shuati.core

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import site.seaunit.shuati.data.dto.User

/**
 * 会话凭据只放应用私有存储（spec FR-003）。
 *
 * 抽成接口是为了单测能塞内存实现，不必依赖 Android Context。
 */
interface CookieStorage {
    fun load(): List<Cookie>
    fun save(cookies: List<Cookie>)
    fun clear()
}

/**
 * 持久化 CookieJar。
 *
 * 后端 `JwtAuthFilter` 只认 `shuati_token` Cookie，没有 Authorization 头分支，
 * 所以安卓端必须像浏览器一样把 Cookie 存下来、每次请求带回去 —— 这样后端一行都不用改。
 */
class PersistentCookieJar(private val storage: CookieStorage) : CookieJar {

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val merged = LinkedHashMap<String, Cookie>()
        storage.load().forEach { merged[it.name] = it }
        cookies.forEach { merged[it.name] = it }
        storage.save(merged.values.toList())
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val stored = storage.load()
        val alive = stored.filter { it.expiresAt > now }
        if (alive.size != stored.size) {
            storage.save(alive)
        }
        return alive.filter { cookie ->
            val host = url.host
            host == cookie.domain || host.endsWith(cookie.domain.trimStart('.'))
        }
    }
}

/** SharedPreferences 版存储：Cookie 与登录用户信息都放这里，文件名固定、模式私有 */
class SessionStore(context: Context) : CookieStorage {

    private val prefs = context.getSharedPreferences("shuati_session", Context.MODE_PRIVATE)

    override fun load(): List<Cookie> =
        prefs.getStringSet(KEY_COOKIES, emptySet()).orEmpty().mapNotNull(::decodeCookie)

    override fun save(cookies: List<Cookie>) {
        prefs.edit().putStringSet(KEY_COOKIES, cookies.map(::encodeCookie).toSet()).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    /** 缓存登录用户，冷启动就能显示邮箱，不必等网络 */
    var user: User?
        get() = prefs.getString(KEY_USER, null)?.let { raw ->
            runCatching { JsonSupport.json.decodeFromString(User.serializer(), raw) }.getOrNull()
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_USER).apply()
            } else {
                prefs.edit()
                    .putString(KEY_USER, JsonSupport.json.encodeToString(User.serializer(), value))
                    .apply()
            }
        }

    private fun encodeCookie(cookie: Cookie): String = listOf(
        cookie.name,
        cookie.value,
        cookie.expiresAt.toString(),
        cookie.domain,
        cookie.path,
    ).joinToString(SEP)

    private fun decodeCookie(raw: String): Cookie? {
        val parts = raw.split(SEP)
        if (parts.size != 5) return null
        val expires = parts[2].toLongOrNull() ?: return null
        return runCatching {
            Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .expiresAt(expires)
                .domain(parts[3])
                .path(parts[4])
                .build()
        }.getOrNull()
    }

    private companion object {
        const val KEY_COOKIES = "cookies"
        const val KEY_USER = "user"
        // Cookie 值与域名里不会出现这个字符，用它当分隔符足够
        const val SEP = "\u0001"
    }
}
