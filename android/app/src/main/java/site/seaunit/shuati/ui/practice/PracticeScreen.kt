package site.seaunit.shuati.ui.practice

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import site.seaunit.shuati.AppContainer
import site.seaunit.shuati.core.toUserMessage
import site.seaunit.shuati.data.dto.ChoiceResult
import site.seaunit.shuati.data.dto.EssayResult
import site.seaunit.shuati.data.dto.Question
import site.seaunit.shuati.ui.components.Hairline
import site.seaunit.shuati.ui.components.NoticeBar
import site.seaunit.shuati.ui.components.WeButton
import site.seaunit.shuati.ui.components.WeTag
import site.seaunit.shuati.ui.components.WeTextAction
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.Ink
import site.seaunit.shuati.ui.theme.Moss
import site.seaunit.shuati.ui.theme.Rose
import site.seaunit.shuati.ui.theme.TextGray

/**
 * 刷题状态。
 *
 * 交互规则逐条对齐网页端 `frontend/src/views/PracticeView.vue`：
 * 选择题靠服务端判定（列表接口不下发答案）；简答题走 AI 判分；
 * AI 解析按题目共享缓存；退出再进靠进度接口续做。
 */
class PracticeState(
    private val container: AppContainer,
    private val bankId: Long?,
    private val unitId: Long?,
    private val scopeName: String,
) {
    var questions by mutableStateOf<List<Question>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    var textAnswer by mutableStateOf("")
    var choiceResult by mutableStateOf<ChoiceResult?>(null)
        private set
    var essayResult by mutableStateOf<EssayResult?>(null)
        private set
    var explanation by mutableStateOf("")
        private set
    var explanationFromLibrary by mutableStateOf(false)
        private set
    var loading by mutableStateOf(true)
        private set
    var submitting by mutableStateOf(false)
        private set
    var explanationLoading by mutableStateOf(false)
        private set
    var notice by mutableStateOf("")
        private set
    var error by mutableStateOf("")
        private set

    private var sessionId: Long? = null
    private var startedAt = System.currentTimeMillis()
    private var answered = 0
    private var correct = 0
    private var partial = 0
    private var wrong = 0

    val current: Question? get() = questions.getOrNull(index)
    val submitted: Boolean get() = choiceResult != null || essayResult != null
    val isLast: Boolean get() = index >= questions.lastIndex
    val progressText: String get() = "${index + 1} / ${questions.size}"
    val canSubmit: Boolean
        get() = current?.let {
            if (it.isShort) textAnswer.isNotBlank() else selected.isNotEmpty()
        } ?: false

    suspend fun load() {
        loading = true
        error = ""
        try {
            val list = when {
                unitId != null -> container.banks.unitQuestions(unitId)
                bankId != null -> container.banks.bankQuestions(bankId)
                else -> container.banks.allQuestions()
            }
            questions = list
            if (list.isEmpty()) return

            // 会话记录失败不影响做题（网页端同样忽略）
            sessionId = runCatching {
                container.practice.startSession(
                    scopeType = if (unitId != null) "UNIT" else if (bankId != null) "BANK" else "ALL",
                    scopeId = unitId ?: bankId,
                    scopeName = scopeName,
                    totalQuestions = list.size,
                )
            }.getOrNull()

            val answeredIds = runCatching { container.banks.progress(bankId, unitId) }
                .getOrNull()?.answeredIds.orEmpty()
            val next = ResumeLocator.nextIndex(list, answeredIds)
            if (next > 0) {
                index = next
                notice = "已为你续上上次的进度"
            }
            resetForCurrent()
        } catch (e: Throwable) {
            error = e.toUserMessage("题目加载失败")
        } finally {
            loading = false
        }
    }

    fun toggle(key: String) {
        if (submitted) return
        selected = if (current?.isMulti == true) {
            if (selected.contains(key)) selected - key else selected + key
        } else {
            setOf(key)
        }
    }

    suspend fun submit() {
        val question = current ?: return
        if (submitting || !canSubmit) return
        submitting = true
        error = ""
        try {
            if (question.isShort) {
                val result = container.practice.submitEssay(
                    question.id,
                    textAnswer.trim(),
                    System.currentTimeMillis() - startedAt,
                )
                essayResult = result
                result?.let {
                    answered += 1
                    when (it.verdict) {
                        "CORRECT" -> correct += 1
                        "PARTIAL" -> partial += 1
                        else -> wrong += 1
                    }
                }
            } else {
                val result = container.practice.submitChoice(
                    question.id,
                    selected.toList(),
                    System.currentTimeMillis() - startedAt,
                )
                choiceResult = result
                result?.let {
                    answered += 1
                    if (it.verdict == "CORRECT") correct += 1 else wrong += 1
                }
            }
        } catch (e: Throwable) {
            // 402 点数不足、429 限流都用服务端原文，不做本地改写
            error = e.toUserMessage("提交失败，请稍后重试")
        } finally {
            submitting = false
        }
    }

    suspend fun loadExplanation() {
        val question = current ?: return
        if (!submitted || explanationLoading) return
        explanationLoading = true
        error = ""
        try {
            explanation = container.practice.explanation(question.id)
            explanationFromLibrary = false
        } catch (e: Throwable) {
            error = e.toUserMessage("解析失败")
        } finally {
            explanationLoading = false
        }
    }

    suspend fun selfEval(verdict: String) {
        val recordId = essayResult?.recordId ?: return
        try {
            container.practice.selfEval(recordId, verdict)
            essayResult = essayResult?.copy(verdict = verdict)
        } catch (e: Throwable) {
            error = e.toUserMessage("自评失败，请稍后重试")
        }
    }

    /** 返回 true 表示这一轮做完了，界面该退出 */
    suspend fun next(): Boolean {
        if (!isLast) {
            index += 1
            resetForCurrent()
            return false
        }
        finish()
        return true
    }

    private suspend fun finish() {
        val id = sessionId ?: return
        val score = if (answered > 0) Math.round(correct * 100f / answered) else null
        container.practice.finishSession(id, answered, correct, partial, wrong, score)
    }

    private fun resetForCurrent() {
        selected = emptySet()
        textAnswer = ""
        choiceResult = null
        essayResult = null
        error = ""
        val stored = current?.explanation
        explanation = stored.orEmpty()
        explanationFromLibrary = !stored.isNullOrBlank()
        startedAt = System.currentTimeMillis()
    }
}

@Composable
fun PracticeScreen(
    container: AppContainer,
    bankId: Long?,
    unitId: Long?,
    scopeName: String,
    onExit: () -> Unit,
) {
    val state = remember(bankId, unitId) { PracticeState(container, bankId, unitId, scopeName) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(bankId, unitId) { state.load() }

    Column(Modifier.fillMaxSize()) {
        // 顶部：白色通栏 + 进度，微信那种"标题条"
        Column(Modifier.fillMaxWidth().background(CellWhite)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(scopeName, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text("第 ${state.progressText} 题", fontSize = 12.sp, color = TextGray)
                }
                WeTextAction("退出", color = TextGray, onClick = onExit)
            }
            if (state.questions.isNotEmpty()) {
                LinearProgressIndicator(
                    progress = { (state.index + 1).toFloat() / state.questions.size },
                    modifier = Modifier.fillMaxWidth(),
                    color = Moss,
                    trackColor = Color(0xFFE8E8E8),
                )
                Spacer(Modifier.height(1.dp))
            } else {
                Hairline()
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Moss)
            }

            state.current == null -> Column(Modifier.padding(16.dp)) {
                NoticeBar(
                    state.error.ifBlank { "这个范围里还没有上架的题目" },
                    error = state.error.isNotBlank(),
                )
            }

            else -> PracticeBody(state = state, onNext = {
                scope.launch { if (state.next()) onExit() }
            })
        }
    }
}

@Composable
private fun PracticeBody(state: PracticeState, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val question = state.current ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.notice.isNotBlank() || state.error.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                NoticeBar(state.notice)
                if (state.error.isNotBlank()) {
                    Spacer(Modifier.padding(top = 6.dp))
                    NoticeBar(state.error, error = true)
                }
            }
        }

        // 题干块
        Column(Modifier.fillMaxWidth().background(CellWhite).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeTag(
                    text = when {
                        question.isShort -> "简答"
                        question.isMulti -> "多选"
                        else -> "单选"
                    },
                    color = TextGray,
                )
                Text(
                    text = "  ${difficultyLabel(question.difficulty)}",
                    fontSize = 12.sp,
                    color = TextGray,
                )
            }
            Text(
                text = question.content,
                fontSize = 17.sp,
                lineHeight = 26.sp,
                color = Ink,
                modifier = Modifier.padding(top = 10.dp),
            )
            if (question.images.isNotEmpty()) {
                Text(
                    "该题含图片，请在网页端查看",
                    fontSize = 12.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (question.isShort) {
            Column(Modifier.fillMaxWidth().background(CellWhite).padding(16.dp)) {
                OutlinedTextField(
                    value = state.textAnswer,
                    onValueChange = { state.textAnswer = it },
                    label = { Text("你的答案", fontSize = 13.sp) },
                    enabled = !state.submitted,
                    minLines = 4,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(Modifier.fillMaxWidth().background(CellWhite)) {
                question.options.orEmpty().forEachIndexed { index, option ->
                    if (index > 0) Hairline(startIndent = 50.dp)
                    OptionRow(
                        optionKey = option.key,
                        text = option.text,
                        mark = AnswerRules.mark(option.key, state.selected, state.choiceResult),
                        picked = state.selected.contains(option.key),
                        enabled = !state.submitted,
                        onClick = { state.toggle(option.key) },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!state.submitted) {
            WeButton(
                text = if (state.submitting) "提交中…" else if (question.isShort) "提交给 AI 判分" else "提交答案",
                onClick = { scope.launch { state.submit() } },
                enabled = state.canSubmit && !state.submitting,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            ResultBlock(state)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { scope.launch { state.loadExplanation() } },
                    enabled = !state.explanationLoading,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = when {
                            state.explanationLoading -> "生成中…"
                            state.explanation.isBlank() -> "AI 解析"
                            else -> "重新生成"
                        },
                        fontSize = 15.sp,
                        color = Moss,
                    )
                }
                WeButton(
                    text = if (state.isLast) "完成本轮" else "下一题",
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.explanation.isNotBlank()) {
            Column(Modifier.fillMaxWidth().background(CellWhite).padding(16.dp)) {
                Text(
                    text = if (state.explanationFromLibrary) "来自解析库" else "本次 AI 解析",
                    fontSize = 12.sp,
                    color = TextGray,
                )
                Text(
                    text = state.explanation,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    color = Ink,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun ResultBlock(state: PracticeState) {
    val scope = rememberCoroutineScope()
    val choice = state.choiceResult
    val essay = state.essayResult
    val verdict = choice?.verdict ?: essay?.verdict
    val color = when (verdict) {
        "CORRECT" -> Moss
        "WRONG" -> Rose
        else -> TextGray
    }

    Column(Modifier.fillMaxWidth().background(CellWhite).padding(16.dp)) {
        Text(
            text = AnswerRules.verdictLabel(verdict),
            fontSize = 17.sp,
            color = color,
            fontWeight = FontWeight.Medium,
        )
        if (choice != null) {
            Text(
                text = "正确答案：${choice.correctAnswer} · 得分 ${choice.score}",
                fontSize = 14.sp,
                color = Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (essay != null) {
            essay.score?.let {
                Text(
                    "得分 $it / 10",
                    fontSize = 14.sp,
                    color = Ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            essay.feedback?.hit_points?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    "命中要点：${it.joinToString("；")}",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Moss,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            essay.feedback?.missed_points?.takeIf { it.isNotEmpty() }?.let {
                Text(
                    "遗漏要点：${it.joinToString("；")}",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Rose,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            essay.feedback?.comment?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            essay.referenceAnswer?.takeIf { it.isNotBlank() }?.let {
                Text(
                    "参考答案：$it",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (essay.degraded) {
                Text(
                    "AI 暂不可用，请自行判定：",
                    fontSize = 13.sp,
                    color = TextGray,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Row {
                    listOf("CORRECT" to "答对了", "PARTIAL" to "部分对", "WRONG" to "答错了").forEach { (key, label) ->
                        WeTextAction(
                            text = label,
                            onClick = { scope.launch { state.selfEval(key) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    optionKey: String,
    text: String,
    mark: OptionMark,
    picked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = when (mark) {
        OptionMark.CORRECT -> Moss
        OptionMark.WRONG -> Rose
        OptionMark.NORMAL -> if (picked) Moss else TextGray
    }
    val background = when (mark) {
        OptionMark.CORRECT -> Moss.copy(alpha = 0.06f)
        OptionMark.WRONG -> Rose.copy(alpha = 0.06f)
        OptionMark.NORMAL -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = optionKey,
            color = accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Ink,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        if (mark == OptionMark.CORRECT || mark == OptionMark.WRONG) {
            Text(
                text = if (mark == OptionMark.CORRECT) "正确" else "你选的",
                color = accent,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private fun difficultyLabel(value: String?): String = when (value) {
    "EASY" -> "简单"
    "HARD" -> "困难"
    else -> "中等"
}
