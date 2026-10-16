package site.seaunit.shuati

import android.app.Application
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import site.seaunit.shuati.core.ApiClient
import site.seaunit.shuati.core.PersistentCookieJar
import site.seaunit.shuati.core.SessionStore
import site.seaunit.shuati.data.repo.AuthRepository
import site.seaunit.shuati.data.repo.BankRepository
import site.seaunit.shuati.data.repo.BillingRepository
import site.seaunit.shuati.data.repo.ManageRepository
import site.seaunit.shuati.data.repo.PracticeRepository
import site.seaunit.shuati.data.repo.ReviewRepository

/**
 * 手写依赖容器：只有五个仓储，引 Hilt 不划算。
 * 单例挂在 Application 上，Activity 重建不会丢会话。
 */
class AppContainer(context: Context) {

    val session = SessionStore(context.applicationContext)
    val api = ApiClient(BuildConfig.BASE_URL, PersistentCookieJar(session))

    val auth = AuthRepository(api, session)
    val banks = BankRepository(api)
    val practice = PracticeRepository(api)
    val review = ReviewRepository(api)
    val billing = BillingRepository(api)
    val manage = ManageRepository(api)

    private val _sessionExpired = MutableStateFlow(false)

    /**
     * 错题重练：从错题本带过来的一组题目 id（顺序即错题本顺序）。
     * 放在容器里而不是导航参数里 —— 错题可能上百条，塞进路由字符串不合适。
     */
    var pendingWrongIds: List<Long> = emptyList()

    /** 任意请求收到 401 时置位，导航层据此回登录页（spec FR-010） */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired

    init {
        api.onUnauthorized = {
            session.user = null
            _sessionExpired.value = true
        }
    }

    fun consumeSessionExpired() {
        _sessionExpired.value = false
    }
}

class ShuatiApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
