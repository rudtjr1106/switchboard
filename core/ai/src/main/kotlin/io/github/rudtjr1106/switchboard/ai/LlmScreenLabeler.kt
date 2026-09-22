package io.github.rudtjr1106.switchboard.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.config.ScreenCatalog
import io.github.rudtjr1106.switchboard.config.ScreenInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val logger = KotlinLogging.logger {}

/**
 * 화면 id(EmailSignUp 등)에 한국어 라벨과 구분을 붙인다
 *
 * [batchSize] 개씩 묶어 묻는다. 스키마에 `id` 를 요청한 값의 enum 으로, 항목 수를 묶음 크기로 못 박아
 * 작은 모델이 id 를 지어내거나 중간에 그만두지 못하게 한다. 그래도 빠진 id 는 [ScreenInfo] 기본값으로 채운다.
 * `ALL` 은 모델에게 묻지 않고 항상 맨 앞에 둔다.
 */
class LlmScreenLabeler(private val engine: LlmEngine, private val batchSize: Int = 20) : ScreenLabeler {

    override suspend fun label(screenIds: List<String>, appDescription: String?): List<ScreenInfo> {
        val ids = screenIds.map { it.trim() }.filter { it.isNotEmpty() && it != ScreenCatalog.ALL }.distinct()
        if (ids.isEmpty()) return listOf(ALL_SCREEN)
        val model = engine.require()
        val labeled = HashMap<String, ScreenInfo>()
        // 앞 묶음에서 쓴 구분 이름을 다음 묶음에 알려 같은 이름을 다시 쓰게 한다
        val groups = LinkedHashSet<String>()
        for (batch in ids.chunked(batchSize)) {
            val result = ask(model, batch, appDescription, groups)
            labeled += result
            result.values.mapNotNullTo(groups) { it.group }
        }
        return listOf(ALL_SCREEN) + ids.map { labeled[it] ?: ScreenInfo(it) }
    }

    /**
     * 하네스 경로: [ScreenDrafts] 로 초안을 만든 뒤 모델은 초안·주석·구역을 보고 다듬기만 한다
     *
     * 모델 답은 검증을 통과할 때만 받는다 (한글이 있고, 짧고, id 를 그대로 되풀이하지 않을 것).
     * 구역 제목이 있는 화면의 구분은 소스가 정한 것이라 모델 답보다 구역을 믿는다.
     */
    override suspend fun labelWithHints(hints: List<ScreenHint>, appDescription: String?): List<ScreenInfo> {
        val unique = hints.map { it.copy(id = it.id.trim()) }.filter { it.id.isNotEmpty() && it.id != ScreenCatalog.ALL }.distinctBy { it.id }
        if (unique.isEmpty()) return listOf(ALL_SCREEN)
        val drafts = unique.associate { it.id to ScreenDrafts.analyze(it) }
        // 사람이 쓴 주석과 id 가 맞아떨어진 화면은 이미 정답에 가깝다. 모델에게 보내지 않아 시간도 아낀다
        val toRefine = unique.filter { drafts.getValue(it.id).source != ScreenDrafts.Source.COMMENT }
        val answered = HashMap<String, ScreenInfo>()
        if (toRefine.isNotEmpty()) {
            val model = engine.require()
            // 초안의 구분 이름을 처음부터 알려 주면 모델이 비슷한 새 이름을 지어내지 않는다
            val groups = LinkedHashSet(drafts.values.mapNotNull { it.screen.group })
            for (batch in toRefine.chunked(batchSize)) {
                val context = batch.associate { hint -> hint.id to contextLine(hint, drafts.getValue(hint.id)) }
                answered += ask(model, batch.map { it.id }, appDescription, groups, context)
            }
        }
        return listOf(ALL_SCREEN) + unique.map { hint -> accept(hint, drafts.getValue(hint.id), answered[hint.id]) }
    }

    /** 모델에게 보여줄 한 줄. 초안이 믿지 않은 주석(옛 이름, 문장)은 모델도 헷갈리게 하므로 넣지 않는다 */
    private fun contextLine(hint: ScreenHint, draft: ScreenDrafts.Draft): String = buildString {
        append(hint.id)
        draft.usableComment?.let { append(" | 주석: ").append(it) }
        hint.section?.takeIf { it.isNotBlank() }?.let { append(" | 구역: ").append(it) }
        append(" | 초안: ").append(draft.screen.label).append(" / ").append(draft.screen.group ?: "-")
    }

    private fun accept(hint: ScreenHint, draft: ScreenDrafts.Draft, answer: ScreenInfo?): ScreenInfo {
        val base = draft.screen
        if (answer == null || draft.source == ScreenDrafts.Source.COMMENT) return base
        val candidate = answer.label.trim()
        val label = candidate.takeIf { isUsableLabel(it, hint.id) && !dropsWords(candidate, base.label, draft.source) } ?: base.label
        val group = when {
            hint.section != null && base.group != null -> base.group
            else -> answer.group?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_GROUP_LENGTH } ?: base.group
        }
        return ScreenInfo(hint.id, label, group)
    }

    /** 모델이 초안에서 말만 빼 버린 경우 (공지 검색 → 검색). 사전으로 다 옮긴 초안이면 정보가 줄어든 것이다 */
    private fun dropsWords(candidate: String, draft: String, source: ScreenDrafts.Source): Boolean {
        if (source != ScreenDrafts.Source.GLOSSARY) return false
        val mine = ScreenDrafts.words(candidate.replace(PAREN, ""))
        val theirs = ScreenDrafts.words(draft.replace(PAREN, ""))
        return mine.isNotEmpty() && mine != theirs && theirs.containsAll(mine)
    }

    private fun isUsableLabel(label: String, id: String): Boolean =
        label.isNotEmpty() && label.length <= MAX_LABEL_LENGTH && label != id && HANGUL.containsMatchIn(label) &&
            JUNK.none { label.contains(it) } && !label.startsWith("(")

    private suspend fun ask(
        model: LanguageModel,
        batch: List<String>,
        appDescription: String?,
        groups: Set<String>,
        context: Map<String, String> = emptyMap(),
    ): Map<String, ScreenInfo> {
        val text = model.complete(
            listOf(ChatMessage(ChatRole.SYSTEM, SYSTEM_PROMPT), ChatMessage(ChatRole.USER, request(batch, appDescription, groups, context))),
            GenerationOptions(temperature = 0.3f, maxTokens = (TOKENS_PER_SCREEN * batch.size + 32).coerceAtLeast(256), jsonSchema = schema(batch)),
        )
        val rows = JsonOutput.parse<List<LabeledScreen>>(text) ?: run {
            logger.warn { "화면 라벨 JSON 을 읽지 못함: ${text.take(200)}" }
            return emptyMap()
        }
        val wanted = batch.toSet()
        val result = LinkedHashMap<String, ScreenInfo>()
        for (row in rows) {
            val id = row.id.trim()
            // 요청하지 않은 id 와 중복은 버린다
            if (id !in wanted || id in result) continue
            result[id] = ScreenInfo(
                id = id,
                label = row.label.trim().ifBlank { ScreenCatalog.defaultLabel(id) },
                group = row.group.trim().ifBlank { null },
            )
        }
        return result
    }

    private fun request(batch: List<String>, appDescription: String?, groups: Set<String>, context: Map<String, String>): String = buildString {
        appendLine("아래 안드로이드 앱 화면 id 마다 한국어 label 과 group 을 붙여 줘.")
        appDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("앱 설명: $it") }
        appendLine("규칙:")
        appendLine("- label: 사용자가 보는 화면 이름을 짧은 한국어로 쓴다 (예: EmailSignUp → 이메일 회원가입)")
        appendLine("- group: 화면을 묶는 짧은 한국어 구분. 관련 있는 화면끼리 같은 group 을 쓴다 (예: 시작·인증, 홈, 공지, 활동, 커뮤니티, MY)")
        if (groups.isNotEmpty()) appendLine("- 앞에서 이미 쓴 group 이름은 그대로 다시 쓴다: ${groups.joinToString(", ")}")
        appendLine("- 요청한 id 만, 요청한 순서대로, 하나도 빠짐없이 답한다")
        appendLine("예시:")
        appendLine(EXAMPLE)
        appendLine()
        if (context.isEmpty()) {
            appendLine("화면 id: ${batch.joinToString(", ")}")
        } else {
            appendLine("화면마다 소스 주석·구역과 초안을 함께 줄게. 초안이 맞으면 그대로 쓰고, 주석이 더 정확한 이름이면 주석을 따라.")
            appendLine("구역이 있으면 group 은 초안의 group 을 그대로 쓴다. 주석이 옛 이름이거나 문장이면 무시한다.")
            batch.forEach { appendLine("- ${context[it] ?: it}") }
        }
    }

    /** id 는 요청한 값 중 하나, 항목 수는 정확히 묶음 크기 */
    private fun schema(batch: List<String>): String = buildJsonObject {
        put("type", "array")
        put("minItems", batch.size)
        put("maxItems", batch.size)
        putJsonObject("items") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("id") {
                    put("type", "string")
                    putJsonArray("enum") { batch.forEach { add(it) } }
                }
                putJsonObject("label") { put("type", "string") }
                putJsonObject("group") { put("type", "string") }
            }
            putJsonArray("required") {
                add("id")
                add("label")
                add("group")
            }
        }
    }.toString()

    @Serializable
    private data class LabeledScreen(val id: String = "", val label: String = "", val group: String = "")

    companion object {
        val ALL_SCREEN = ScreenInfo(ScreenCatalog.ALL, ScreenCatalog.ALL_LABEL, ScreenCatalog.ALL_GROUP)

        private const val MAX_LABEL_LENGTH = 20
        private const val MAX_GROUP_LENGTH = 12
        private val HANGUL = Regex("[가-힣]")
        private val PAREN = Regex("\\(.*?\\)")
        private val JUNK = listOf("{", "\"", "->", "→", "구 ", "신 ")

        /** 한 항목이 대략 `{"id":"AdminStudyGroupSchedule","label":"관리자 스터디 일정","group":"활동"}` 만큼 */
        private const val TOKENS_PER_SCREEN = 64

        private val SYSTEM_PROMPT = """
            너는 안드로이드 앱의 화면 이름(경로 id)에 한국어 이름과 구분을 붙이는 도우미야.
            설명 없이 JSON 배열만 출력해.
        """.trimIndent()

        /** 실제 앱에서 가져온 대응 예 */
        private val EXAMPLE = """
            [{"id":"Login","label":"로그인","group":"시작·인증"},
             {"id":"EmailSignUp","label":"이메일 회원가입","group":"시작·인증"},
             {"id":"Home","label":"홈","group":"홈"},
             {"id":"ScheduleAdd","label":"일정 생성","group":"홈"},
             {"id":"Notice","label":"공지 목록","group":"공지"},
             {"id":"NoticeDetail","label":"공지 상세","group":"공지"},
             {"id":"Act","label":"활동","group":"활동"},
             {"id":"Community","label":"커뮤니티","group":"커뮤니티"},
             {"id":"Mypage","label":"설정","group":"MY"},
             {"id":"Qrcode","label":"내 QR 코드","group":"MY"}]
        """.trimIndent()
    }
}
