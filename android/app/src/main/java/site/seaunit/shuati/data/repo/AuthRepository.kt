package site.seaunit.shuati.data.repo

import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.core.JsonSupport
import site.seaunit.shuati.core.SessionStore
import site.seaunit.shuati.data.dto.CaptchaData
import site.seaunit.shuati.data.dto.EmailCodeSent
import site.seaunit.shuati.data.dto.User

/** 认证相关调用，路径与网页端完全一致（契约 §1） */
class AuthRepository(
    private val api: ApiClient,
    private val store: SessionStore,
) {
    suspend fun captcha(): CaptchaData? =
        api.get("/captcha/image", CaptchaData.serializer())

    suspend fun me(): User? = api.get("/api/me", User.serializer())?.also { store.user = it }

    /**
     * 冷启动校准登录态：未登录时服务端会返回 401，这里按"没登录"处理，
     * 不触发会话过期提示（游客打开 App 不该被弹登录框）。
     */
    suspend fun probeMe(): User? {
        val user = api.get("/api/me", User.serializer(), notifyUnauthorized = false)
        store.user = user
        return user
    }

    suspend fun login(
        email: String,
        password: String,
        captchaTicket: String,
        captchaCode: String,
    ): User? = api.post(
        "/api/auth/login",
        JsonSupport.obj(
            "email" to email.trim(),
            "password" to password,
            "captchaTicket" to captchaTicket,
            "captchaCode" to captchaCode.trim(),
        ),
        User.serializer(),
    )?.also { store.user = it }

    suspend fun sendRegisterCode(email: String): EmailCodeSent? = api.post(
        "/api/auth/register/email-code",
        JsonSupport.obj("email" to email.trim()),
        EmailCodeSent.serializer(),
    )

    suspend fun register(email: String, password: String, emailCode: String): User? = api.post(
        "/api/auth/register",
        JsonSupport.obj(
            "email" to email.trim(),
            "password" to password,
            "emailCode" to emailCode.trim(),
        ),
        User.serializer(),
    )?.also { store.user = it }

    suspend fun sendResetCode(email: String): EmailCodeSent? = api.post(
        "/api/auth/password-reset/email-code",
        JsonSupport.obj("email" to email.trim()),
        EmailCodeSent.serializer(),
    )

    suspend fun resetPassword(email: String, emailCode: String, newPassword: String) {
        api.postVoid(
            "/api/auth/password-reset",
            JsonSupport.obj(
                "email" to email.trim(),
                "emailCode" to emailCode.trim(),
                "newPassword" to newPassword,
            ),
        )
    }

    suspend fun logout() {
        runCatching { api.postVoid("/api/auth/logout") }
        store.clear()
    }

    fun cachedUser(): User? = store.user
}
