package site.seaunit.shuati.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.data.dto.Bank
import site.seaunit.shuati.data.dto.BankUnit
import site.seaunit.shuati.data.dto.EmailCodeSent
import site.seaunit.shuati.data.dto.Question
import site.seaunit.shuati.ui.components.Hairline
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.SectionHeader
import site.seaunit.shuati.ui.components.WeButton
import site.seaunit.shuati.ui.components.WeCell
import site.seaunit.shuati.ui.components.WeTag
import site.seaunit.shuati.ui.components.WeTextAction
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Rose
import site.seaunit.shuati.ui.theme.TextGray

private val OPTION_KEYS = listOf("A", "B", "C", "D", "E", "F", "G", "H")

/**
 * 题库维护状态：与网页端 `BankManageView.vue` 的能力一一对应。
 *
 * 权限判定完全交给服务端（`requireManage`），客户端只负责把 403/404 的文案显示出来。
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

    fun unitNameOf(id: Long): String = units.firstOrNull { it.id == id }?.name ?: "未分类"

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
        reloadQuestions()
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
            val body = payload as JsonObject
            if (existingId == null) {
                container.manage.createQuestion(body)
                notice = "题目已新增"
            } else {
                container.manage.updateQuestion(existingId, body)
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
            shape = RoundedCornerShape(6.dp),
            title = { Text("重命名单元", fontSize = 17.sp) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("单元名称", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
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
                }) { Text("保存", color = Moss, fontSize = 16.sp) }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) {
                    Text("取消", color = TextGray, fontSize = 16.sp)
                }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 6.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.bank?.name ?: "题库管理",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            WeTextAction("返回", onClick = onBack, color = TextGray)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    NoticeBar(state.notice)
                    if (state.error.isNotBlank()) {
                        Spacer(Modifier.padding(top = 6.dp))
                        NoticeBar(state.error, error = true)
                    }
                }
            }

            // ---- 题库信息 ----
            item { SectionHeader("题库信息") }
            item {
                Column(Modifier.fillMaxWidth().background(CellWhite).padding(16.dp)) {
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { state.name = it },
                        label = { Text("名称", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = { state.description = it },
                        label = { Text("描述（可选）", fontSize = 13.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    WeButton(
                        text = if (state.busy) "保存中…" else "保存",
                        onClick = { scope.launch { state.saveBank() } },
                        enabled = !state.busy,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        WeTextAction(
                            text = "删除题库",
                            color = Rose,
                            onClick = { state.clearError(); deleteOpen = true },
                        )
                    }
                }
            }

            // ---- 单元 ----
            item { SectionHeader("单元") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CellWhite)
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = unitInput,
                        onValueChange = { unitInput = it },
                        label = { Text("新单元名称", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f),
                    )
                    WeTextAction(
                        text = "新增",
                        enabled = unitInput.isNotBlank() && !state.busy,
                        onClick = {
                            scope.launch {
                                state.createUnit(unitInput)
                                if (state.error.isBlank()) unitInput = ""
                            }
                        },
                    )
                }
                Hairline()
            }
            items(state.units, key = { it.id }) { unit ->
                Column(Modifier.fillMaxWidth().background(CellWhite)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(unit.name, color = Ink, fontSize = 16.sp)
                            Text(
                                text = "${unit.question_count} 题" +
                                    if (unit.off_count > 0) "（${unit.off_count} 已下架）" else "",
                                color = TextGray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        WeTextAction(
                            text = "重命名",
                            onClick = { renaming = unit; renameInput = unit.name },
                        )
                        WeTextAction(
                            text = "删除",
                            color = Rose,
                            onClick = { scope.launch { state.deleteUnit(unit.id) } },
                        )
                    }
                    Hairline(startIndent = 16.dp)
                }
            }

            // ---- 题目 ----
            item { SectionHeader("题目") }
            item {
                Column(Modifier.fillMaxWidth().background(CellWhite).padding(16.dp)) {
                    OutlinedTextField(
                        value = state.keyword,
                        onValueChange = { state.keyword = it },
                        label = { Text("检索题干", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        listOf("ALL" to "全部", "ON" to "已上架", "OFF" to "已下架").forEach { (key, label) ->
                            val selected = state.filter == key
                            WeTextAction(
                                text = label,
                                color = if (selected) Moss else TextGray,
                                onClick = {
                                    if (!selected) scope.launch { state.changeFilter(key) }
                                },
                            )
                        }
                    }
                    WeButton(
                        text = "新增题目",
                        onClick = { state.clearError(); editing = null; formOpen = true },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Hairline()
            }

            if (state.questions.isEmpty()) {
                item {
                    Text(
                        "没有符合条件的题目",
                        color = TextGray,
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().background(CellWhite).padding(16.dp),
                    )
                }
            }

            items(state.questions, key = { it.id }) { question ->
                QuestionRow(
                    question = question,
                    unitName = state.unitNameOf(question.unit_id),
                    onEdit = { state.clearError(); editing = question; formOpen = true },
                    onToggle = { scope.launch { state.toggleStatus(question) } },
                    onDelete = { scope.launch { state.deleteQuestion(question.id) } },
                )
            }

            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun QuestionRow(
    question: Question,
    unitName: String,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(CellWhite)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeTag(typeLabel(question.type), color = TextGray)
                Text(
                    text = "  $unitName",
                    color = TextGray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                WeTag(
                    text = if (question.status == "ON") "已上架" else "已下架",
                    color = if (question.status == "ON") Moss else TextGray,
                )
            }
            Text(
                text = question.content,
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeTextAction("编辑", onClick = onEdit)
            WeTextAction(
                text = if (question.status == "ON") "下架" else "上架",
                color = TextGray,
                onClick = onToggle,
            )
            WeTextAction("删除", color = Rose, onClick = onDelete)
        }
        Hairline(startIndent = 16.dp)
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
        shape = RoundedCornerShape(6.dp),
        title = { Text("删除题库", fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    text = "题库下的单元、题目以及相关练习记录会一并删除，无法恢复。" +
                        "验证码会发到你的注册邮箱：${container.auth.cachedUser()?.email.orEmpty()}",
                    fontSize = 13.sp,
                    color = Ink,
                    lineHeight = 19.sp,
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
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.weight(1f),
                    )
                    WeTextAction(
                        text = if (cooldown > 0) "${cooldown} 秒后重发" else "发送验证码",
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
                    )
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
            ) { Text(if (busy) "删除中…" else "确认删除", color = Rose, fontSize = 16.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextGray, fontSize = 16.sp) }
        },
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
        shape = RoundedCornerShape(6.dp),
        title = { Text(if (isEdit) "编辑题目" else "新增题目", fontSize = 17.sp) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Box {
                    OutlinedButton(
                        onClick = { unitMenu = true },
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "所属单元：" + (
                                unitId?.let { id -> units.firstOrNull { it.id == id }?.name }
                                    ?: "默认单元（未分类）"
                                ),
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

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("SINGLE" to "单选", "MULTI" to "多选", "SHORT" to "简答").forEach { (key, label) ->
                        WeTextAction(
                            text = label,
                            color = if (type == key) Moss else TextGray,
                            onClick = {
                                if (type != key) {
                                    if (key != "SHORT") {
                                        // 从简答切到选择题：选项与答案重新开始
                                        picked = emptySet()
                                        options = listOf("", "", "", "")
                                    }
                                    type = key
                                }
                                formError = ""
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("题干", fontSize = 12.sp) },
                    minLines = 3,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )

                if (type == "SHORT") {
                    OutlinedTextField(
                        value = shortAnswer,
                        onValueChange = { shortAnswer = it },
                        label = { Text("参考答案", fontSize = 12.sp) },
                        minLines = 3,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Text(
                        "评分要点不用填：留空后由 AI 判分时自行提炼",
                        fontSize = 11.sp,
                        color = TextGray,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                } else {
                    options.forEachIndexed { index, text ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WeTextAction(
                                text = OPTION_KEYS[index],
                                color = if (picked.contains(OPTION_KEYS[index])) Moss else TextGray,
                                onClick = {
                                    val key = OPTION_KEYS[index]
                                    picked = if (type == "MULTI") {
                                        if (picked.contains(key)) picked - key else picked + key
                                    } else {
                                        setOf(key)
                                    }
                                },
                            )
                            OutlinedTextField(
                                value = text,
                                onValueChange = { newText ->
                                    options = options.toMutableList().also { it[index] = newText }
                                },
                                label = { Text("选项 ${OPTION_KEYS[index]}", fontSize = 12.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.weight(1f),
                            )
                            if (options.size > 2) {
                                WeTextAction(
                                    text = "×",
                                    color = TextGray,
                                    onClick = {
                                        options = options.toMutableList().also { it.removeAt(index) }
                                    },
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        WeTextAction(
                            text = "加选项",
                            enabled = options.size < OPTION_KEYS.size,
                            onClick = { options = options + "" },
                        )
                        Text(
                            text = "当前答案：" + picked.sorted().joinToString("").ifEmpty { "未选" },
                            fontSize = 11.sp,
                            color = TextGray,
                        )
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf("EASY" to "简单", "MEDIUM" to "中等", "HARD" to "困难").forEach { (key, label) ->
                        WeTextAction(
                            text = label,
                            color = if (difficulty == key) Moss else TextGray,
                            onClick = { difficulty = key },
                        )
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签（可选，逗号分隔）", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("解析（可选）", fontSize = 12.sp) },
                    minLines = 2,
                    shape = RoundedCornerShape(4.dp),
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
            ) { Text(if (saving) "保存中…" else "保存", color = Moss, fontSize = 16.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextGray, fontSize = 16.sp) }
        },
    )
}

private fun typeLabel(type: String): String = when (type) {
    "SINGLE" -> "单选"
    "MULTI" -> "多选"
    "SHORT" -> "简答"
    else -> type
}
