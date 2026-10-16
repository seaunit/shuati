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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import site.seaunit.shuati.ui.review.WrongBookFilter
import site.seaunit.shuati.ui.theme.CellWhite
import site.seaunit.shuati.ui.theme.HairlineColor
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
    // 错题重练：按这些 id（顺序即错题本顺序）出题
    private val questionIds: List<Long>? = null,
) {
    /** 错题重练是一次性题集，不参与练习光标 */
    private val useCursor = questionIds == null
    private val scopeType = when {
        unitId != null -> "UNIT"
        bankId != null -> "BANK"
        else -> "ALL"
    }
    private val scopeId = unitId ?: bankId ?: 0L

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
    private var counts = RoundCounts.Empty

    val current: Question? get() = questions.getOrNull(index)
    val submitted: Boolean get() = choiceResult != null || essayResult != null
    val isLast: Boolean get() = index >= questions.lastIndex
    val canGoPrev: Boolean get() = index > 0
    val progressText: String get() = "${index + 1} / ${questions.size}"
    val canSubmit: Boolean
        get() = current?.let {
            if (it.isShort) textAnswer.isNotBlank() else selected.isNotEmpty()
        } ?: false

    /** 上一题：与网页端一致，往回走会把这一题的作答状态清空重来 */
    suspend fun prev() {
        if (index <= 0) return
        index -= 1
        resetForCurrent()
        saveCursor()
    }

    /**
     * 从头开始这一轮：不管刷到第几题、进度是多少，都回到第 1 题重来。
     *
     * 只重置"本轮的位置与计数" —— 每道题的作答记录在提交时就已写库，不受影响。
     */
    suspend fun restart() {
        index = 0
        counts = RoundCounts.Empty
        notice = ""
        resetForCurrent()
        // 从头开始 = 当前停在第 1 题，所以进度也记在第 1 题
        saveCursor()
    }

    /** 把「当前停在哪道题」记到服务端（切一题写一次；失败不影响做题） */
    private suspend fun saveCursor(targetQuestionId: Long? = current?.id) {
        if (!useCursor) return
        runCatching { container.banks.saveCursor(scopeType, scopeId, targetQuestionId) }
    }

    suspend fun load() {
        loading = true
        error = ""
        try {
            val list = when {
                questionIds != null -> WrongBookFilter.pickPracticeQuestions(
                    questionIds,
                    container.banks.allQuestions(),
                )
                unitId != null -> container.banks.unitQuestions(unitId)
                bankId != null -> container.banks.bankQuestions(bankId)
                else -> container.banks.allQuestions()
            }
            questions = list
            if (list.isEmpty()) {
                if (questionIds != null) {
                    error = "这些错题当前不在可练习范围里（可能已被下架或删除）"
                }
                return
            }

            // 会话记录失败不影响做题（网页端同样忽略）
            sessionId = runCatching {
                container.practice.startSession(
                    // 错题重练服务端没有这个 scope 类型，按"全部题目"记会话即可
                    scopeType = when {
                        questionIds != null -> "ALL"
                        unitId != null -> "UNIT"
                        bankId != null -> "BANK"
                        else -> "ALL"
                    },
                    scopeId = if (questionIds != null) null else unitId ?: bankId,
                    scopeName = scopeName,
                    totalQuestions = list.size,
                )
            }.getOrNull()

            // 落位：光标优先（当前刷到哪道就落在哪道），没有光标才回退到「第一道没答过的题」；
            // 错题重练既不看光标也不看已答记录，固定从第一题开始
            val cursorId = if (useCursor) {
                runCatching { container.banks.cursor(scopeType, scopeId) }.getOrNull()
            } else {
                null
            }
            val answeredIds = if (useCursor) {
                runCatching { container.banks.progress(bankId, unitId) }
                    .getOrNull()?.answeredIds.orEmpty()
            } else {
                emptyList()
            }
            val next = ResumeLocator.startIndex(list, cursorId, answeredIds)
            if (next > 0) {
                index = next
                notice = "已为你续上上次的进度"
            }
            resetForCurrent()
            // 落位即记录一次当前位置，这样即使没切题，退出再进也停在同一道
            saveCursor()
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
                result?.let { counts = counts.plus(it.verdict) }
            } else {
                val result = container.practice.submitChoice(
                    question.id,
                    selected.toList(),
                    System.currentTimeMillis() - startedAt,
                )
                choiceResult = result
                result?.let { counts = counts.plus(it.verdict) }
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
            saveCursor()
            return false
        }
        finish()
        return true
    }

    private suspend fun finish() {
        // 一轮做完清掉光标：下次进入回到「全部答完就从头开始」的老规则
        saveCursor(null)
        val id = sessionId ?: return
        container.practice.finishSession(
            id,
            counts.answered,
            counts.correct,
            counts.partial,
            counts.wrong,
            counts.score,
        )
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
    questionIds: List<Long>? = null,
) {
    val state = remember(bankId, unitId, questionIds) {
        PracticeState(container, bankId, unitId, scopeName, questionIds)
    }
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
                WeTextAction(
                    text = "从头开始",
                    color = TextGray,
                    onClick = { scope.launch { state.restart() } },
                )
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

            else -> {
                // 内容可滚动、操作条钉在底部：手机上不用滚到最后才能点提交
                PracticeBody(state = state, modifier = Modifier.weight(1f))
                PracticeActions(state = state, onNext = {
                    scope.launch { if (state.next()) onExit() }
                })
            }
        }
    }
}

@Composable
private fun PracticeBody(state: PracticeState, modifier: Modifier = Modifier) {
    val question = state.current ?: return

    Column(
        modifier = modifier
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
                        isMulti = question.isMulti,
                        enabled = !state.submitted,
                        onClick = { state.toggle(option.key) },
                    )
                }
            }
        }

        if (state.submitted) {
            Spacer(Modifier.height(10.dp))
            ResultBlock(state)
        }

        // 解析必须等提交后才出现，否则等于提前泄答案（规则与单测见 AnswerRules.canShowExplanation）
        if (AnswerRules.canShowExplanation(state.submitted, state.explanation)) {
            Spacer(Modifier.height(10.dp))
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

/**
 * 底部操作条：上一题 / AI 解析 / 提交答案或下一题。
 *
 * 与网页端一致：没提交时是「提交答案」，提交后才出现「AI 解析」和「下一题 · 完成练习」，
 * 会做的题可以直接提交，不需要先滚到底部找按钮。
 */
@Composable
private fun PracticeActions(state: PracticeState, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CellWhite)
            .drawBehind {
                drawLine(HairlineColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { scope.launch { state.prev() } },
                enabled = state.canGoPrev,
                shape = RoundedCornerShape(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
            ) {
                Text("上一题", fontSize = 15.sp, color = Ink)
            }

            if (state.submitted) {
                OutlinedButton(
                    onClick = { scope.launch { state.loadExplanation() } },
                    enabled = !state.explanationLoading,
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
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
                    text = if (state.isLast) "完成练习" else "下一题",
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                )
            } else {
                WeButton(
                    text = when {
                        state.submitting -> "提交中…"
                        state.current?.isShort == true -> "提交给 AI 判分"
                        else -> "提交答案"
                    },
                    onClick = { scope.launch { state.submit() } },
                    enabled = state.canSubmit && !state.submitting,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
    isMulti: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 作答后的状态要一眼能看出：字母徽章填充成实心色 + 整行浅色底 + 错选的文字也变红
    // （网页端就是"徽章填充 + border/bg 染色"这一套）
    val background = when (mark) {
        OptionMark.CORRECT -> Moss.copy(alpha = 0.10f)
        OptionMark.WRONG -> Rose.copy(alpha = 0.10f)
        // 未提交但已勾选：给一层很浅的绿底，和网页端 chosen 态一个意思
        OptionMark.NORMAL -> if (picked) Moss.copy(alpha = 0.05f) else Color.Transparent
    }
    val badgeBackground = when (mark) {
        OptionMark.CORRECT -> Moss
        OptionMark.WRONG -> Rose
        OptionMark.NORMAL -> if (picked) Moss.copy(alpha = 0.15f) else Color(0xFFF2F2F2)
    }
    val badgeColor = when (mark) {
        OptionMark.CORRECT, OptionMark.WRONG -> CellWhite
        OptionMark.NORMAL -> if (picked) Moss else TextGray
    }
    val textColor = if (mark == OptionMark.WRONG) Rose else Ink

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(badgeBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = optionKey,
                color = badgeColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Text(
            text = text,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = textColor,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
        // 未提交时给出明确的选中标记：单选圆形、多选方框（通用规范）
        when (mark) {
            OptionMark.NORMAL -> SelectionMark(
                checked = picked,
                square = isMulti,
            )
            OptionMark.CORRECT -> Text(
                text = "正确",
                color = Moss,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
            OptionMark.WRONG -> Text(
                text = "你选的",
                color = Rose,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * 选项右侧的选中标记：单选是圆圈、多选是方框。
 * 选中 = 实心绿 + 白勾，未选 = 灰色空心。
 */
@Composable
private fun SelectionMark(checked: Boolean, square: Boolean) {
    val shape = if (square) RoundedCornerShape(3.dp) else CircleShape
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .background(if (checked) Moss else Color.Transparent)
            .drawBehind {
                if (!checked) {
                    val strokePx = 1.5.dp.toPx()
                    val stroke = Stroke(width = strokePx)
                    if (square) {
                        val inset = strokePx / 2f
                        drawRoundRect(
                            color = Color(0xFFC8C8C8),
                            topLeft = Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(
                                size.width - strokePx,
                                size.height - strokePx,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                3.dp.toPx() * 0.6f,
                            ),
                            style = stroke,
                        )
                    } else {
                        drawCircle(
                            color = Color(0xFFC8C8C8),
                            radius = size.minDimension / 2f - strokePx / 2f,
                            style = stroke,
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Text("✓", color = CellWhite, fontSize = 12.sp)
        }
    }
}

private fun difficultyLabel(value: String?): String = when (value) {
    "EASY" -> "简单"
    "HARD" -> "困难"
    else -> "中等"
}
