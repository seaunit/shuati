package site.seaunit.shuati.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.ui.auth.ForgotPasswordScreen
import site.seaunit.shuati.ui.auth.LoginScreen
import site.seaunit.shuati.ui.auth.RegisterScreen
import site.seaunit.shuati.ui.banks.BankListScreen
import site.seaunit.shuati.ui.banks.UnitListScreen
import site.seaunit.shuati.ui.practice.PracticeScreen
import site.seaunit.shuati.ui.profile.ProfileScreen
import site.seaunit.shuati.ui.review.StatsScreen
import site.seaunit.shuati.ui.review.WrongBookScreen
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat

private const val ROUTE_BANKS = "banks"
private const val ROUTE_WRONG = "wrong"
private const val ROUTE_STATS = "stats"
private const val ROUTE_PROFILE = "profile"

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/**
 * 单 Activity 导航壳。
 *
 * 底部四个入口与网页端的侧栏一一对应；做题、登录这类页面是全屏的，不显示底栏。
 * 登录态来自本地会话缓存（冷启动先信缓存，再用 /api/me 校准）。
 */
@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    var loggedIn by remember { mutableStateOf(container.auth.cachedUser() != null) }
    val expired by container.sessionExpired.collectAsState()

    // 任意接口 401 → 清会话并回登录页（spec FR-010）
    LaunchedEffect(expired) {
        if (expired) {
            container.consumeSessionExpired()
            loggedIn = false
            nav.navigate("login") { launchSingleTop = true }
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showBottomBar = route in setOf(ROUTE_BANKS, ROUTE_WRONG, ROUTE_STATS, ROUTE_PROFILE)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(route, nav)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = ROUTE_BANKS,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_BANKS) {
                BankListScreen(
                    container = container,
                    isLoggedIn = loggedIn,
                    onOpenBank = { id, name -> nav.navigate("units/$id/${Uri.encode(name)}") },
                    onNeedLogin = { nav.navigate("login") },
                )
            }

            composable(
                route = "units/{bankId}/{bankName}",
                arguments = listOf(
                    navArgument("bankId") { type = NavType.LongType },
                    navArgument("bankName") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val bankId = backStackEntry.arguments?.getLong("bankId") ?: 0L
                val bankName = backStackEntry.arguments?.getString("bankName").orEmpty()
                if (!loggedIn) {
                    LaunchedEffect(bankId) { nav.navigate("login") }
                } else {
                    UnitListScreen(
                        container = container,
                        bankId = bankId,
                        bankName = bankName,
                        onStartBank = { nav.navigate("practice/$bankId/0/${Uri.encode("$bankName · 整库")}") },
                        onStartUnit = { unitId, unitName ->
                            nav.navigate("practice/0/$unitId/${Uri.encode(unitName)}")
                        },
                        onBack = { nav.popBackStack() },
                    )
                }
            }

            composable(
                route = "practice/{bankId}/{unitId}/{scopeName}",
                arguments = listOf(
                    navArgument("bankId") { type = NavType.LongType },
                    navArgument("unitId") { type = NavType.LongType },
                    navArgument("scopeName") { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val bankId = backStackEntry.arguments?.getLong("bankId")?.takeIf { it > 0 }
                val unitId = backStackEntry.arguments?.getLong("unitId")?.takeIf { it > 0 }
                val scopeName = backStackEntry.arguments?.getString("scopeName").orEmpty().ifBlank { "刷题" }
                if (!loggedIn) {
                    LaunchedEffect(scopeName) { nav.navigate("login") }
                } else {
                    PracticeScreen(
                        container = container,
                        bankId = bankId,
                        unitId = unitId,
                        scopeName = scopeName,
                        onExit = { nav.popBackStack() },
                    )
                }
            }

            composable(ROUTE_WRONG) { WrongBookScreen(container, loggedIn) }
            composable(ROUTE_STATS) { StatsScreen(container, loggedIn) }
            composable(ROUTE_PROFILE) {
                ProfileScreen(
                    container = container,
                    isLoggedIn = loggedIn,
                    onLogin = { nav.navigate("login") },
                    onLoggedOut = { loggedIn = false },
                )
            }

            composable("login") {
                LoginScreen(
                    container = container,
                    onLoggedIn = {
                        loggedIn = true
                        nav.navigate(ROUTE_BANKS) { popUpTo(ROUTE_BANKS) { inclusive = true } }
                    },
                    onRegister = { nav.navigate("register") },
                    onForgotPassword = { nav.navigate("forgot") },
                )
            }

            composable("register") {
                RegisterScreen(
                    container = container,
                    onRegistered = {
                        loggedIn = true
                        nav.navigate(ROUTE_BANKS) { popUpTo(ROUTE_BANKS) { inclusive = true } }
                    },
                    onBack = { nav.popBackStack() },
                )
            }

            composable("forgot") {
                ForgotPasswordScreen(
                    container = container,
                    onDone = { nav.popBackStack() },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(route: String, nav: NavHostController) {
    val items = listOf(
        Tab(ROUTE_BANKS, "题库", Icons.Filled.Home),
        Tab(ROUTE_WRONG, "错题本", Icons.Filled.Warning),
        Tab(ROUTE_STATS, "统计", Icons.Filled.List),
        Tab(ROUTE_PROFILE, "我的", Icons.Filled.Person),
    )
    NavigationBar {
        items.forEach { tab ->
            val selected = route == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        nav.navigate(tab.route) {
                            popUpTo(ROUTE_BANKS) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, fontSize = 11.sp) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = Moss,
                    selectedTextColor = Moss,
                    unselectedIconColor = Oat,
                    unselectedTextColor = Oat,
                ),
            )
        }
    }
}
