package site.seaunit.shuati.ui.auth

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat

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
    var captchaImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
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

    AuthScaffold(title = "登录", subtitle = "拾题 · 手机上继续刷题") {
        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        AuthField("密码", password, { password = it }, KeyboardType.Password, isPassword = true)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AuthField(
                label = "验证码",
                value = captchaCode,
                onValueChange = { captchaCode = it },
                keyboardType = KeyboardType.Text,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { refreshCaptcha() } }) {
                val bitmap = captchaImage
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "点击刷新验证码",
                        modifier = Modifier.height(36.dp),
                    )
                } else {
                    Text("点击刷新", color = Oat, fontSize = 12.sp)
                }
            }
        }

        NoticeBar(error, error = true)

        Button(
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
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Moss)
            else Text("登录", fontSize = 15.sp)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = onRegister) { Text("注册", color = Moss) }
            TextButton(onClick = onForgotPassword) { Text("找回密码", color = Oat) }
        }
    }
}

/** 注册：邮箱验证码 + 密码，不需要图形验证码 */
@Composable
fun RegisterScreen(container: AppContainer, onRegistered: () -> Unit, onBack: () -> Unit) {
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

    AuthScaffold(title = "注册", subtitle = "邮箱验证码校验通过才能注册", onBack = onBack) {
        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AuthField(
                label = "邮箱验证码",
                value = code,
                onValueChange = { code = it },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    sending = true
                    error = ""
                    scope.launch {
                        try {
                            val sent = container.auth.sendRegisterCode(email)
                            cooldown = sent?.cooldownSeconds ?: 60
                            notice = "验证码已发送，请查收邮箱"
                        } catch (e: Throwable) {
                            error = e.toUserMessage("验证码发送失败")
                        } finally {
                            sending = false
                        }
                    }
                },
                enabled = !sending && cooldown == 0 && email.isNotBlank(),
            ) {
                Text(if (cooldown > 0) "${cooldown} 秒后重发" else "发送验证码", color = Moss, fontSize = 13.sp)
            }
        }
        AuthField("密码（6-64 位）", password, { password = it }, KeyboardType.Password, isPassword = true)

        NoticeBar(notice)
        NoticeBar(error, error = true)

        Button(
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        container.auth.register(email, password, code)
                        onRegistered()
                    } catch (e: Throwable) {
                        error = e.toUserMessage("注册失败，请稍后重试")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && email.isNotBlank() && code.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Moss)
            else Text("注册并登录", fontSize = 15.sp)
        }
    }
}

/** 找回密码：邮箱验证码 + 新密码，同样不需要图形验证码 */
@Composable
fun ForgotPasswordScreen(container: AppContainer, onDone: () -> Unit, onBack: () -> Unit) {
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

    AuthScaffold(title = "找回密码", subtitle = "用注册邮箱收验证码后重设密码", onBack = onBack) {
        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AuthField(
                label = "邮箱验证码",
                value = code,
                onValueChange = { code = it },
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    sending = true
                    error = ""
                    scope.launch {
                        try {
                            val sent = container.auth.sendResetCode(email)
                            cooldown = sent?.cooldownSeconds ?: 60
                            notice = "验证码已发送，请查收邮箱"
                        } catch (e: Throwable) {
                            error = e.toUserMessage("验证码发送失败")
                        } finally {
                            sending = false
                        }
                    }
                },
                enabled = !sending && cooldown == 0 && email.isNotBlank(),
            ) {
                Text(if (cooldown > 0) "${cooldown} 秒后重发" else "发送验证码", color = Moss, fontSize = 13.sp)
            }
        }
        AuthField("新密码（6-64 位）", password, { password = it }, KeyboardType.Password, isPassword = true)

        NoticeBar(notice)
        NoticeBar(error, error = true)

        Button(
            onClick = {
                busy = true
                error = ""
                scope.launch {
                    try {
                        container.auth.resetPassword(email, code, password)
                        onDone()
                    } catch (e: Throwable) {
                        error = e.toUserMessage("重置失败，请稍后重试")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy && email.isNotBlank() && code.isNotBlank() && password.length >= 6,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
        ) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Moss)
            else Text("重置密码", fontSize = 15.sp)
        }
    }
}

@Composable
private fun AuthScaffold(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        if (onBack != null) {
            TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("返回", color = Oat, fontSize = 13.sp)
            }
        }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Text(subtitle, fontSize = 13.sp, color = Oat, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
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
        visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}

private fun decodeCaptchaImage(dataUrl: String): androidx.compose.ui.graphics.ImageBitmap? {
    if (dataUrl.isBlank()) return null
    val base64 = dataUrl.substringAfter("base64,", dataUrl)
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
