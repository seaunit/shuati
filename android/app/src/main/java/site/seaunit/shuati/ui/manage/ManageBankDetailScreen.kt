package site.seaunit.shuati.ui.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.data.dto.BankUnit
import site.seaunit.shuati.data.dto.EmailCodeSent
import site.seaunit.shuati.data.dto.Question
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.SectionBox
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Mist
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Oat
import site.seaunit.shuati.ui.theme.Rose

private val OPTION_KEYS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

/**
 * 题库维护状态：与网页端 `BankManageView.vue` 的能力一一对应。
 *
 * 权限判定完全交给服务端（`requireManage`），客户端只负责把服务端的 403/404 文案显示出来。
 */
class ManageBankState(
    private val container: AppContainer,
    private val bankId: Long,
) {
    var bank by mutableStateOf<Bank?>(null)
        private set
    var name by mutableStateOf("")
    var description by mutableStateOf("")
    var units by mutableStateOf<List<BankUnit>>(emptyList())
        private set
    var questions by mutableStateOf<List<Question>>(emptyList())
        private set
    var filter by mutableStateOf("ALL")
        private set
    var keyword by mutableStateOf("")
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set

    val unitName: (Long) -> String = { id -> units.firstOrNull { it.id == id }?.name ?: "未分类" }

    suspend fun load() {
        loading = true
        error = ""
        try {
            val found = container.manage.banks().firstOrNull { it.id == bankId }
            bank = found
            if (found == null) {
                error = "题库不存在，或你没有维护权限"
                return
            }
            name = found.name
            description = found.description.orEmpty()
            units = container.manage.units(bankId)
            refreshQuestions()
        } catch (e: Throwable) {
            error = e.toUserMessage("加载失败")
        } finally {
            loading = false
        }
    }

    suspend fun refreshQuestions() {
        questions = container.manage.questions(bankId, filter, keyword.trim())
    }

    suspend fun saveBank() {
        busy = true
        error = ""
        try {
            container.manage.updateBank(bankId, name.trim(), description.trim())
            notice = "题库信息已保存"
            units = container.manage.units(bankId)
        } catch (e: Throwable) {
            error = e.toUserMessage("保存失败")
        } finally {
            busy = false
        }
    }

    suspend fun changeFilter(value: String) {
        filter = value
        runCatching { refreshQuestions() }.onFailure { error = it.toUserMessage("加载题目失败") }
    }

    suspend fun reloadQuestions() {
        runCatching { refreshQuestions() }.onFailure { error = it.toUserMessage("加载题目失败") }
    }

    suspend fun createUnit(unitName: String) {
        busy = true
        error = ""
        try {
            container.manage.createUnit(bankId, unitName.trim())
            units = container.manage.units(bankId)
        } catch (e: Throwable) {
            error = e.toUserMessage("新建单元失败")
        } finally {
            busy = false
        }
    }

    suspend fun renameUnit(id: Long, unitName: String) {
        error = ""
        try {
            container.manage.renameUnit(id, unitName.trim())
            units = container.manage.units(bankId)
        } catch (e: Throwable) {
            error = e.toUserMessage("重命名失败")
        }
    }

    suspend fun deleteUnit(id: Long) {
        error = ""
        try {
            container.manage.deleteUnit(id)
            units = container.manage.units(bankId)
        } catch (e: Throwable) {
            error = e.toUserMessage("删除单元失败")
        }
    }

    suspend fun toggleStatus(question: Question) {
        error = ""
        try {
            val next = if (question.status == "ON") "OFF" else "ON"
            container.manage.setQuestionStatus(question.id, next)
            refreshQuestions()
        } catch (e: Throwable) {
            error = e.toUserMessage("修改状态失败")
        }
    }

    suspend fun deleteQuestion(id: Long) {
        error = ""
        try {
            container.manage.deleteQuestion(id)
            refreshQuestions()
            units = container.manage.units(bankId)
        } catch (e: Throwable) {
            error = e.toUserMessage("删除题目失败")
        }
    }

    /** 保存题目：`existingId` 为空是新增，否则是编辑 */
    suspend fun saveQuestion(existingId: Long?, payload: JsonElement) {
        busy = true
        error = ""
        try {
            if (existingId == null) {
                container.manage.createQuestion(payload as kotlinx.serialization.json.JsonObject)
                notice = "题目已新增"
            } else {
                container.manage.updateQuestion(existingId, payload as kotlinx.serialization.json.JsonObject)
                notice = "题目已保存"
            }
            refreshQuestions()
            units = container.manage.units(bankId)
        } catch (e: Throwable) {
            error = e.toUserMessage("保存题目失败")
        } finally {
            busy = false
        }
    }

    suspend fun sendDeleteCode(): EmailCodeSent? = container.manage.sendDeleteCode(bankId)

    suspend fun deleteBank(code: String): Boolean {
        error = ""
        return try {
            container.manage.deleteBank(bankId, code)
            true
        } catch (e: Throwable) {
            error = e.toUserMessage("删除失败")
            false
        }
    }

    fun clearNotice() {
        notice = ""
    }

    fun clearError() {
        error = ""
    }
}

@Composable
fun ManageBankDetailScreen(
    container: AppContainer,
    bankId: Long,
    onBack: () -> Unit,
) {
    val state = remember(bankId) { ManageBankState(container, bankId) }
    val scope = rememberCoroutineScope()

    var unitInput by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<BankUnit?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Question?>(null) }
    var deleteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(bankId) { state.load() }
    // 检索防抖：与网页端一致，输入停 300ms 再查
    LaunchedEffect(state.keyword) {
        delay(300)
        state.reloadQuestions()
    }

    if (formOpen) {
        QuestionDialog(
            bankId = bankId,
            units = state.units,
            existing = editing,
            saving = state.busy,
            serverError = state.error,
            onSubmit = { payload ->
                scope.launch {
                    state.saveQuestion(editing?.id, payload)
                    if (state.error.isBlank()) {
                        formOpen = false
                        editing = null
                    }
                }
            },
            onDismiss = { formOpen = false; editing = null; state.clearError() },
        )
    }

    if (deleteOpen) {
        DeleteBankDialog(
            container = container,
            state = state,
            onDeleted = { deleteOpen = false; onBack() },
            onDismiss = { deleteOpen = false; state.clearError() },
        )
    }

    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名单元", fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("单元名称", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renaming ?: return@TextButton
                    scope.launch {
                        state.renameUnit(target.id, renameInput)
                        renaming = null
                    }
                }) { Text("保存", color = Moss) }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("取消", color = Oat) } },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                state.bank?.name ?: "题库管理",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("返回", color = Oat, fontSize = 13.sp) }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { NoticeBar(state.notice) }
            item { NoticeBar(state.error, error = true) }

            // ---- 题库信息 ----
            item {
                SectionBox {
                    Text("题库信息", fontSize = 13.sp, color = Oat)
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { state.name = it },
                        label = { Text("名称", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { state.description = it },
                        label = { Text("描述（可选）", fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = { scope.launch { state.saveBank() } },
                            enabled = !state.busy,
                            colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        ) { Text("保存", fontSize = 14.sp) }
                        OutlinedButton(onClick = { state.clearError(); deleteOpen = true }) {
                            Text("删除题库", fontSize = 13.sp, color = Rose)
                        }
                    }
                }
            }

            // ---- 单元 ----
            item {
                SectionBox {
                    Text("单元", fontSize = 13.sp, color = Oat)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = unitInput,
                            onValueChange = { unitInput = it },
                            label = { Text("新单元名称", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            enabled = unitInput.isNotBlank() && !state.busy,
                            onClick = {
                                scope.launch {
                                    state.createUnit(unitInput)
                                    if (state.error.isBlank()) unitInput = ""
                                }
                            },
                        ) { Text("新增", fontSize = 13.sp, color = Moss) }
                    }
                    state.units.forEach { unit ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(unit.name, fontSize = 14.sp, color = Ink)
                                Text(
                                    "${unit.question_count} 题" +
                                        if (unit.off_count > 0) "（${unit.off_count} 已下架）" else "",
                                    fontSize = 11.sp,
                                    color = Oat,
                                )
                            }
                            TextButton(onClick = { renaming = unit; renameInput = unit.name }) {
                                Text("重命名", fontSize = 12.sp, color = Moss)
                            }
                            TextButton(onClick = { scope.launch { state.deleteUnit(unit.id) } }) {
                                Text("删除", fontSize = 12.sp, color = Rose)
                            }
                        }
                    }
                }
            }

            // ---- 题目 ----
            item {
                SectionBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("题目", fontSize = 13.sp, color = Oat, modifier = Modifier.weight(1f))
                        Button(
                            // 打开表单前清掉上一次的报错，否则会带着旧错误进来、保存成功也不关窗
                            onClick = { state.clearError(); editing = null; formOpen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        ) { Text("新增题目", fontSize = 13.sp) }
                    }
                    OutlinedTextField(
                        value = state.keyword,
                        onValueChange = { state.keyword = it },
                        label = { Text("检索题干", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("ALL" to "全部", "ON" to "已上架", "OFF" to "已下架").forEach { (key, label) ->
                            val selected = state.filter == key
                            TextButton(onClick = { scope.launch { state.changeFilter(key) } }) {
                                Text(
                                    label,
                                    fontSize = 12.sp,
                                    color = if (selected) Moss else Oat,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            }

            item {
                if (state.questions.isEmpty()) {
                    Text("没有符合条件的题目", fontSize = 13.sp, color = Oat)
                }
            }

            items(state.questions, key = { it.id }) { question ->
                SectionBox {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(typeLabel(question.type), fontSize = 11.sp, color = Oat)
                        Text(
                            "  ${state.unitName(question.unit_id)}",
                            fontSize = 11.sp,
                            color = Oat,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (question.status == "ON") "已上架" else "已下架",
                            fontSize = 11.sp,
                            color = if (question.status == "ON") Moss else Oat,
                        )
                    }
                    Text(
                        question.content,
                        fontSize = 14.sp,
                        color = Ink,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { state.clearError(); editing = question; formOpen = true }) {
                            Text("编辑", fontSize = 12.sp, color = Moss)
                        }
                        TextButton(onClick = { scope.launch { state.toggleStatus(question) } }) {
                            Text(
                                if (question.status == "ON") "下架" else "上架",
                                fontSize = 12.sp,
                                color = Ink,
                            )
                        }
                        TextButton(onClick = { scope.launch { state.deleteQuestion(question.id) } }) {
                            Text("删除", fontSize = 12.sp, color = Rose)
                        }
                    }
                }
            }
        }
    }
}

/** 删除题库：先发邮箱验证码，再凭码删除（与网页端同一套 DELETE_BANK 用途） */
@Composable
private fun DeleteBankDialog(
    container: AppContainer,
    state: ManageBankState,
    onDeleted: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var cooldown by remember { mutableIntStateOf(0) }
    var sending by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf("") }

    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1000)
            cooldown -= 1
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除题库", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "题库下的单元、题目以及相关练习记录会一并删除，无法恢复。" +
                        "验证码会发到你的注册邮箱：${container.auth.cachedUser()?.email.orEmpty()}",
                    fontSize = 13.sp,
                    color = Ink,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("邮箱验证码", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = !sending && cooldown == 0,
                        onClick = {
                            sending = true
                            localError = ""
                            scope.launch {
                                try {
                                    val sent = state.sendDeleteCode()
                                    cooldown = sent?.cooldownSeconds ?: 60
                                } catch (e: Throwable) {
                                    localError = e.toUserMessage("验证码发送失败")
                                } finally {
                                    sending = false
                                }
                            }
                        },
                    ) {
                        Text(
                            if (cooldown > 0) "${cooldown} 秒后重发" else "发送验证码",
                            fontSize = 12.sp,
                            color = Moss,
                        )
                    }
                }
                if (localError.isNotBlank()) {
                    Column(Modifier.padding(top = 8.dp)) { NoticeBar(localError, error = true) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && code.isNotBlank(),
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = state.deleteBank(code.trim())
                        busy = false
                        if (ok) onDeleted()
                    }
                },
            ) { Text(if (busy) "删除中…" else "确认删除", color = Rose) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Oat) } },
    )
}

/** 新增 / 编辑题目：字段与校验与网页端一致 */
@Composable
private fun QuestionDialog(
    bankId: Long,
    units: List<BankUnit>,
    existing: Question?,
    saving: Boolean,
    serverError: String,
    onSubmit: (JsonElement) -> Unit,
    onDismiss: () -> Unit,
) {
    val isEdit = existing != null
    var type by remember { mutableStateOf(existing?.type ?: "SINGLE") }
    var unitId by remember { mutableStateOf<Long?>(existing?.unit_id) }
    var unitMenu by remember { mutableStateOf(false) }
    var content by remember { mutableStateOf(existing?.content.orEmpty()) }
    var options by remember {
        mutableStateOf(
            existing?.options?.map { it.text }?.plus(listOf("", "", "", ""))?.take(4)
                ?: listOf("", "", "", ""),
        )
    }
    // 注意：String.toSet() 得到的是 Set<Char>，这里要的是选项字母集合
    var picked by remember { mutableStateOf(existing?.answer.orEmpty().map { it.toString() }.toSet()) }
    var shortAnswer by remember {
        mutableStateOf(if (existing?.type == "SHORT") existing.answer.orEmpty() else "")
    }
    var difficulty by remember { mutableStateOf(existing?.difficulty ?: "MEDIUM") }
    var tags by remember { mutableStateOf(existing?.tags?.joinToString("，").orEmpty()) }
    var explanation by remember { mutableStateOf(existing?.explanation.orEmpty()) }
    var formError by remember { mutableStateOf("") }

    fun buildPayload(): JsonElement? {
        formError = ""
        val trimmed = content.trim()
        if (trimmed.isEmpty()) {
            formError = "题干不能为空"
            return null
        }
        val tagsJson = buildJsonArray {
            tags.split(",", "，").map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { add(JsonPrimitive(it)) }
        }

        return if (type == "SHORT") {
            val answer = shortAnswer.trim()
            if (answer.isEmpty()) {
                formError = "简答题要填参考答案"
                return null
            }
            buildJsonObject {
                put("bankId", JsonPrimitive(bankId))
                put("unitId", unitId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("type", JsonPrimitive(type))
                put("content", JsonPrimitive(trimmed))
                put("answer", JsonPrimitive(answer))
                put("options", JsonNull)
                // 编辑时原样保留原有评分要点，避免静默清空（网页端同样处理）
                put(
                    "keyPoints",
                    existing?.key_points?.takeIf { it.isNotEmpty() }?.let { list ->
                        buildJsonArray { list.forEach { add(JsonPrimitive(it)) } }
                    } ?: JsonNull,
                )
                put("difficulty", JsonPrimitive(difficulty))
                put("explanation", JsonPrimitive(explanation.trim()))
                put("tags", tagsJson)
            }
        } else {
            val cleaned = options.map { it.trim() }
            if (cleaned.takeWhile { it.isNotEmpty() }.size < 2) {
                formError = "选择题至少要有两个选项"
                return null
            }
            if (cleaned.any { it.isEmpty() }) {
                formError = "选项内容不能留空，没用的选项请删掉"
                return null
            }
            if (picked.isEmpty()) {
                formError = "请选择正确答案"
                return null
            }
            buildJsonObject {
                put("bankId", JsonPrimitive(bankId))
                put("unitId", unitId?.let { JsonPrimitive(it) } ?: JsonNull)
                put("type", JsonPrimitive(type))
                put("content", JsonPrimitive(trimmed))
                put("answer", JsonPrimitive(picked.sorted().joinToString("")))
                put(
                    "options",
                    buildJsonArray {
                        cleaned.forEachIndexed { index, text ->
                            add(buildJsonObject {
                                put("key", JsonPrimitive(OPTION_KEYS[index]))
                                put("text", JsonPrimitive(text))
                            })
                        }
                    },
                )
                put("keyPoints", JsonNull)
                put("difficulty", JsonPrimitive(difficulty))
                put("explanation", JsonPrimitive(explanation.trim()))
                put("tags", tagsJson)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑题目" else "新增题目", fontSize = 16.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                // 所属单元
                Box {
                    OutlinedButton(onClick = { unitMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "所属单元：" + (unitId?.let { id -> units.firstOrNull { it.id == id }?.name } ?: "默认单元（未分类）"),
                            fontSize = 13.sp,
                            color = Ink,
                        )
                    }
                    DropdownMenu(expanded = unitMenu, onDismissRequest = { unitMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("默认单元（未分类）", fontSize = 13.sp) },
                            onClick = { unitId = null; unitMenu = false },
                        )
                        units.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.name, fontSize = 13.sp) },
                                onClick = { unitId = unit.id; unitMenu = false },
                            )
                        }
                    }
                }

                // 题型
                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("SINGLE" to "单选", "MULTI" to "多选", "SHORT" to "简答").forEach { (key, label) ->
                        TextButton(onClick = {
                            if (type != key && key != "SHORT") {
                                // 从简答切到选择题：题干外的东西重新来过，
                                // 否则简答文本会被当成选项字母
                                picked = emptySet()
                                options = listOf("", "", "", "")
                            }
                            type = key
                            formError = ""
                        }) {
                            Text(
                                label,
                                fontSize = 13.sp,
                                color = if (type == key) Moss else Oat,
                                fontWeight = if (type == key) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("题干", fontSize = 12.sp) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )

                if (type == "SHORT") {
                    OutlinedTextField(
                        value = shortAnswer,
                        onValueChange = { shortAnswer = it },
                        label = { Text("参考答案", fontSize = 12.sp) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "评分要点不用填：留空后由 AI 判分时自行提炼",
                        fontSize = 11.sp,
                        color = Oat,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    options.forEachIndexed { index, text ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TextButton(onClick = {
                                val key = OPTION_KEYS[index]
                                picked = if (type == "MULTI") {
                                    if (picked.contains(key)) picked - key else picked + key
                                } else {
                                    setOf(key)
                                }
                            }) {
                                Text(
                                    OPTION_KEYS[index],
                                    fontSize = 13.sp,
                                    color = if (picked.contains(OPTION_KEYS[index])) Moss else Oat,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            OutlinedTextField(
                                value = text,
                                onValueChange = { newText ->
                                    options = options.toMutableList().also { it[index] = newText }
                                },
                                label = { Text("选项 ${OPTION_KEYS[index]}", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            if (options.size > 2) {
                                TextButton(onClick = {
                                    options = options.toMutableList().also { it.removeAt(index) }
                                }) { Text("×", fontSize = 14.sp, color = Oat) }
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(
                            enabled = options.size < OPTION_KEYS.size,
                            onClick = { options = options + "" },
                        ) { Text("加选项", fontSize = 12.sp, color = Moss) }
                        Text(
                            "当前答案：" + picked.sorted().joinToString("").ifEmpty { "未选" },
                            fontSize = 11.sp,
                            color = Oat,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("EASY" to "简单", "MEDIUM" to "中等", "HARD" to "困难").forEach { (key, label) ->
                        TextButton(onClick = { difficulty = key }) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                color = if (difficulty == key) Moss else Oat,
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签（可选，逗号分隔）", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("解析（可选）", fontSize = 12.sp) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )

                if (formError.isNotBlank()) {
                    Column(Modifier.padding(top = 8.dp)) { NoticeBar(formError, error = true) }
                }
                if (serverError.isNotBlank()) {
                    Column(Modifier.padding(top = 8.dp)) { NoticeBar(serverError, error = true) }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = { buildPayload()?.let(onSubmit) },
            ) { Text(if (saving) "保存中…" else "保存", color = Moss) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = Oat) } },
    )
}

private fun typeLabel(type: String): String = when (type) {
    "SINGLE" -> "单选"
    "MULTI" -> "多选"
    "SHORT" -> "简答"
    else -> type
}
