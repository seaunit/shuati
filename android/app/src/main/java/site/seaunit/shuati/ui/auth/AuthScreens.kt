package site.seaunit.shuati.ui.auth

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.ui.components.Hairline
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.WeButton
import site.seaunit.shuati.ui.components.WeTextAction
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.PageGray
import site.seaunit.shuati.ui.theme.TextGray
import site.seaunit.shuati.ui.theme.TextHint

/** 登录：与网页端一致 —— 只有登录要图形验证码 */
@Composable
fun LoginScreen(
    container: AppContainer,
    onLoggedIn: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var captchaCode by remember { mutableStateOf("") }
    var captchaTicket by remember { mutableStateOf("") }
    var captchaImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    suspend fun refreshCaptcha() {
        runCatching { container.auth.captcha() }
            .onSuccess { data ->
                captchaTicket = data?.ticket.orEmpty()
                captchaImage = decodeCaptchaImage(data?.imageBase64.orEmpty())
                captchaCode = ""
            }
            .onFailure { error = it.toUserMessage("验证码加载失败，点图片重试") }
    }

    LaunchedEffect(Unit) { refreshCaptcha() }

    AuthPage(title = "登录拾题", subtitle = "手机上继续刷题") {
        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Hairline(startIndent = 16.dp)
        AuthField("密码", password, { password = it }, KeyboardType.Password, isPassword = true)
        Hairline(startIndent = 16.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthField(
                label = "验证码",
                value = captchaCode,
                onValueChange = { captchaCode = it },
                keyboardType = KeyboardType.Text,
                modifier = Modifier.weight(1f),
            )
            // 服务端出图 150×48，这里按接近原始比例展示（点一下换一张）
            Box(
                modifier = Modifier
                    .width(132.dp)
                    .height(46.dp)
                    .padding(end = 16.dp)
                    // 浅灰底把验证码从白色表单块里"抠"出来，一眼能看出是可点的控件
                    .background(PageGray, RoundedCornerShape(3.dp))
                    .clickable { scope.launch { refreshCaptcha() } },
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = captchaImage
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "点击刷新验证码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("点击刷新", color = TextHint, fontSize = 12.sp)
                }
            }
        }

        if (error.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                NoticeBar(error, error = true)
            }
        }

        WeButton(
            text = if (busy) "登录中…" else "登录",
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        container.auth.login(email, password, captchaTicket, captchaCode)
                        onLoggedIn()
                    } catch (e: Throwable) {
                        error = e.toUserMessage("登录失败，请稍后重试")
                        refreshCaptcha()
                    } finally {
                        busy = false
                    }
                }
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            WeTextAction("注册", onClick = onRegister)
            Spacer(Modifier.width(24.dp))
            WeTextAction("找回密码", color = TextGray, onClick = onForgotPassword)
        }
    }
}

/** 注册：邮箱验证码 + 密码，不需要图形验证码 */
@Composable
fun RegisterScreen(container: AppContainer, onRegistered: () -> Unit, onBack: () -> Unit) {
    EmailCodeAuthScreen(
        container = container,
        title = "注册拾题",
        subtitle = "邮箱验证码校验通过才能注册",
        submitText = "注册并登录",
        passwordLabel = "密码（6-64 位）",
        sendCode = { container.auth.sendRegisterCode(it) },
        submit = { email, code, password -> container.auth.register(email, password, code) },
        onSuccess = onRegistered,
        onBack = onBack,
    )
}

/** 找回密码：邮箱验证码 + 新密码，同样不需要图形验证码 */
@Composable
fun ForgotPasswordScreen(container: AppContainer, onDone: () -> Unit, onBack: () -> Unit) {
    EmailCodeAuthScreen(
        container = container,
        title = "找回密码",
        subtitle = "用注册邮箱收验证码后重设密码",
        submitText = "重置密码",
        passwordLabel = "新密码（6-64 位）",
        sendCode = { container.auth.sendResetCode(it) },
        submit = { email, code, password ->
            container.auth.resetPassword(email, code, password)
        },
        onSuccess = onDone,
        onBack = onBack,
    )
}

/** 注册与找回密码共用同一套表单：邮箱 + 验证码 + 密码 */
@Composable
private fun EmailCodeAuthScreen(
    container: AppContainer,
    title: String,
    subtitle: String,
    submitText: String,
    passwordLabel: String,
    sendCode: suspend (String) -> site.seaunit.shuati.data.dto.EmailCodeSent?,
    submit: suspend (String, String, String) -> Unit,
    onSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var cooldown by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf("") }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    AuthPage(title = title, subtitle = subtitle, onBack = onBack) {
        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Hairline(startIndent = 16.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthField(
                label = "邮箱验证码",
                value = code,
                onValueChange = { code = it },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.padding(end = 10.dp)) {
                WeTextAction(
                    text = if (cooldown > 0) "${cooldown} 秒后重发" else "发送验证码",
                    enabled = !sending && cooldown == 0 && email.isNotBlank(),
                    onClick = {
                        sending = true
                        error = ""
                        scope.launch {
                            try {
                                val sent = sendCode(email)
                                cooldown = sent?.cooldownSeconds ?: 60
                                notice = "验证码已发送，请查收邮箱"
                            } catch (e: Throwable) {
                                error = e.toUserMessage("验证码发送失败")
                            } finally {
                                sending = false
                            }
                        }
                    },
                )
            }
        }
        Hairline(startIndent = 16.dp)
        AuthField(passwordLabel, password, { password = it }, KeyboardType.Password, isPassword = true)

        if (notice.isNotBlank() || error.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                NoticeBar(notice)
                if (error.isNotBlank()) {
                    Spacer(Modifier.padding(top = 6.dp))
                    NoticeBar(error, error = true)
                }
            }
        }

        WeButton(
            text = if (busy) "提交中…" else submitText,
            enabled = !busy && email.isNotBlank() && code.isNotBlank() && password.length >= 6,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        submit(email, code, password)
                        onSuccess()
                    } catch (e: Throwable) {
                        error = e.toUserMessage("提交失败，请稍后重试")
                    } finally {
                        busy = false
                    }
                }
            },
        )
    }
}

/** 登录类页面的公共骨架：浅灰底 + 白色表单块 + 底部通栏按钮 */
@Composable
private fun AuthPage(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
            ) {
                WeTextAction("返回", color = TextGray, onClick = onBack)
            }
        }
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 14.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = TextGray,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Column(Modifier.fillMaxWidth().background(CellWhite)) { content() }
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private fun decodeCaptchaImage(dataUrl: String): ImageBitmap? {
    if (dataUrl.isBlank()) return null
    val base64 = dataUrl.substringAfter("base64,", dataUrl)
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
