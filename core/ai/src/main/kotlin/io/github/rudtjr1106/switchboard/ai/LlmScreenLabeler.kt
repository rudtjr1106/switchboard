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

    private suspend fun ask(model: LanguageModel, batch: List<String>, appDescription: String?, groups: Set<String>): Map<String, ScreenInfo> {
        val text = model.complete(
            listOf(ChatMessage(ChatRole.SYSTEM, SYSTEM_PROMPT), ChatMessage(ChatRole.USER, request(batch, appDescription, groups))),
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

    private fun request(batch: List<String>, appDescription: String?, groups: Set<String>): String = buildString {
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
        appendLine("화면 id: ${batch.joinToString(", ")}")
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
