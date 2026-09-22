package io.github.rudtjr1106.switchboard.ai

import io.github.rudtjr1106.switchboard.config.NoticeTemplate
import io.github.rudtjr1106.switchboard.config.ScreenInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * 온디바이스 AI 하네스 평가. 정답은 UMC-PRODUCT/umc-product-android 의 실제 화면 34개와 원격 설정 저장소 README 의 이름표다
 *
 * - `deterministic drafts` 는 모델 없이 매번 돈다. 초안 품질이 떨어지면(사전·규칙을 잘못 고치면) 테스트가 깨진다.
 * - `real model` 은 `./gradlew :core:ai:aiEval` 로만 돈다 (SWITCHBOARD_AI_EVAL=1). 받아 둔 모델(4B 우선, 없으면 1B)로
 *   라벨·문구를 만들어 점수를 `build/reports/ai-eval/` 에 남긴다. 점수는 기록만 하고 통과 기준으로 쓰지 않는다.
 */
class HarnessEvalTest {

    @Serializable
    data class GoldenScreen(val id: String, val label: String, val group: String, val comment: String? = null, val section: String? = null) {
        val hint: ScreenHint get() = ScreenHint(id, comment, section)
    }

    @Serializable
    data class ScreenSet(val source: String, val screens: List<GoldenScreen>)

    @Serializable
    data class DraftCase(val situation: String, val template: String)

    @Serializable
    data class PolishCase(val title: String, val body: String, val template: String)

    @Serializable
    data class NoticeSet(val source: String, val draft: List<DraftCase>, val polish: List<PolishCase>)

    private val json = Json { ignoreUnknownKeys = true }
    private val golden: List<GoldenScreen> = json.decodeFromString<ScreenSet>(resource("harness/umc-screens.json")).screens
    private val notices: NoticeSet = json.decodeFromString(resource("harness/notices.json"))
    private val reportDir = File("build/reports/ai-eval").apply { mkdirs() }

    @Test
    fun `deterministic drafts meet the baseline on the UMC golden set`() {
        val drafts = golden.map { ScreenDrafts.draft(it.hint) }
        val score = LabelScore.of(golden, drafts)
        writeReport("screens-baseline.md", "화면 라벨 · 결정적 초안 (모델 없음)", score)
        println(score.summary("결정적 초안"))
        // 구역 제목이 있는 프로젝트라 구분은 거의 다 맞아야 한다
        assertTrue(score.groupExact >= 0.95, "구분 정확도 ${score.groupExact}")
        assertTrue(score.labelExact >= 0.7, "라벨 정확도 ${score.labelExact}")
        assertTrue(score.labelPartial >= 0.85, "라벨 부분 일치 ${score.labelPartial}")
    }

    @Test
    fun `real model eval writes a report`() = runBlocking {
        assumeTrue(System.getenv("SWITCHBOARD_AI_EVAL") == "1", "SWITCHBOARD_AI_EVAL=1 일 때만 (./gradlew :core:ai:aiEval)")
        val (spec, path) = installedModel() ?: run {
            assumeTrue(false, "받아 둔 모델이 없어요")
            error("unreachable")
        }
        val engine = LlamaCppEngine()
        val started = TimeSource.Monotonic.markNow()
        engine.load(spec, path)
        val loadTime = started.elapsedNow()
        try {
            val labeler = LlmScreenLabeler(engine)
            val idsOnly = timed { labeler.label(golden.map { it.id }).drop(1) }
            val hinted = timed { labeler.labelWithHints(golden.map { it.hint }).drop(1) }
            val drafts = golden.map { ScreenDrafts.draft(it.hint) }

            val copywriter = LlmNoticeCopywriter(engine)
            val copyRows = mutableListOf<List<String>>()
            var copyOk = 0
            var toneOk = 0
            var factsOk = 0
            val copyTime = timed {
                for (case in notices.draft) {
                    val out = copywriter.draft(case.situation, NoticeTemplate.valueOf(case.template), 40, 200)
                    val row = judge(case.situation, out)
                    copyRows += listOf("초안", case.situation, out.title, out.body) + row.marks
                    if (row.ok) copyOk++
                    if (row.tone) toneOk++
                    if (row.facts) factsOk++
                }
                for (case in notices.polish) {
                    val out = copywriter.polish(case.title, case.body, NoticeTemplate.valueOf(case.template), 40, 200)
                    val row = judge(case.title + " " + case.body, out)
                    copyRows += listOf("다듬기", case.title, out.title, out.body) + row.marks
                    if (row.ok) copyOk++
                    if (row.tone) toneOk++
                    if (row.facts) factsOk++
                }
            }
            val copyTotal = notices.draft.size + notices.polish.size

            val scores = listOf(
                "결정적 초안" to LabelScore.of(golden, drafts),
                "모델 · id 만" to LabelScore.of(golden, idsOnly.value),
                "모델 · 하네스(초안+힌트)" to LabelScore.of(golden, hinted.value),
            )
            val report = buildString {
                appendLine("# 온디바이스 AI 평가 · ${spec.displayName}")
                appendLine()
                appendLine("모델 올리기 ${loadTime.inWholeMilliseconds}ms · 라벨(id 만) ${idsOnly.millis}ms · 라벨(하네스) ${hinted.millis}ms · 문구 $copyTotal 건 ${copyTime.millis}ms")
                appendLine()
                appendLine("## 화면 라벨 (정답 ${golden.size}개)")
                appendLine()
                appendLine("| 방식 | 라벨 정확 | 라벨 부분 일치 | 구분 정확 |")
                appendLine("|---|---|---|---|")
                scores.forEach { (name, s) -> appendLine("| $name | ${pct(s.labelExact)} | ${pct(s.labelPartial)} | ${pct(s.groupExact)} |") }
                appendLine()
                appendLine("| id | 정답 | 초안 | 모델 · 하네스 | 모델 · id 만 |")
                appendLine("|---|---|---|---|---|")
                golden.forEachIndexed { i, g ->
                    appendLine("| ${g.id} | ${g.label} / ${g.group} | ${drafts[i].label} / ${drafts[i].group} | ${hinted.value[i].label} / ${hinted.value[i].group} | ${idsOnly.value[i].label} / ${idsOnly.value[i].group} |")
                }
                appendLine()
                appendLine("## 안내 문구 ($copyTotal 건)")
                appendLine()
                appendLine("모든 검사 통과 $copyOk/$copyTotal · '~해요' 말투 $toneOk/$copyTotal · 숫자 보존 $factsOk/$copyTotal")
                appendLine()
                appendLine("| 종류 | 입력 | 제목 | 본문 | 글자 수 | 말투 | 숫자 |")
                appendLine("|---|---|---|---|---|---|---|")
                copyRows.forEach { appendLine("| " + it.joinToString(" | ") { cell -> cell.replace("|", "/").replace("\n", " ") } + " |") }
            }
            val file = File(reportDir, "model-${spec.id}.md").apply { writeText(report) }
            println(report)
            println("보고서: ${file.absolutePath}")
            assertTrue(hinted.value.size == golden.size)
        } finally {
            engine.unload()
        }
    }

    // ---- 채점 ----

    data class LabelScore(val labelExact: Double, val labelPartial: Double, val groupExact: Double, val misses: List<String>) {
        fun summary(name: String) = "$name: 라벨 정확 ${pct(labelExact)}, 부분 일치 ${pct(labelPartial)}, 구분 정확 ${pct(groupExact)}"

        companion object {
            fun of(golden: List<GoldenScreen>, actual: List<ScreenInfo>): LabelScore {
                val byId = actual.associateBy { it.id }
                var exact = 0
                var partial = 0
                var group = 0
                val misses = mutableListOf<String>()
                for (g in golden) {
                    val a = byId[g.id]
                    if (a != null && normalize(a.label) == normalize(g.label)) exact++ else misses += "${g.id}: ${a?.label} ≠ ${g.label}"
                    if (a != null && words(a.label).intersect(words(g.label)).isNotEmpty()) partial++
                    if (a?.group == g.group) group++
                }
                val n = golden.size.toDouble()
                return LabelScore(exact / n, partial / n, group / n, misses)
            }

            /** 괄호 속 부연 설명, 띄어쓰기, 가운뎃점은 무시한다 */
            fun normalize(label: String) = label.replace(Regex("\\(.*?\\)"), "").replace(" ", "").replace("·", "").lowercase()

            private fun words(label: String) = label.replace(Regex("\\(.*?\\)"), "").split(' ', '·').map { it.lowercase() }.filter { it.isNotBlank() }.toSet()
        }
    }

    private data class Judgement(val ok: Boolean, val tone: Boolean, val facts: Boolean, val marks: List<String>)

    private fun judge(source: String, out: NoticeDraft): Judgement {
        val fits = out.title.codePointCount(0, out.title.length) <= 40 && out.body.codePointCount(0, out.body.length) <= 200
        val tone = NoticeChecks.isFriendlyTone(out.title + " " + out.body)
        val facts = NoticeChecks.missingNumbers(source, out.title + " " + out.body).isEmpty()
        val ok = NoticeChecks.problems(out, source, 40, 200).isEmpty()
        fun mark(b: Boolean) = if (b) "O" else "X"
        return Judgement(ok, tone, facts, listOf(mark(fits), mark(tone), mark(facts)))
    }

    private fun writeReport(name: String, title: String, score: LabelScore) {
        File(reportDir, name).writeText(
            buildString {
                appendLine("# $title")
                appendLine()
                appendLine(score.summary("결과"))
                appendLine()
                appendLine("틀린 라벨:")
                score.misses.forEach { appendLine("- $it") }
            },
        )
    }

    // ---- 도구 ----

    private class Timed<T>(val value: T, val millis: Long)

    private suspend fun <T> timed(block: suspend () -> T): Timed<T> {
        val mark = TimeSource.Monotonic.markNow()
        val value = block()
        return Timed(value, mark.elapsedNow().inWholeMilliseconds)
    }

    private fun installedModel(): Pair<ModelSpec, Path>? {
        val dir = System.getenv("SWITCHBOARD_MODELS_DIR")?.let { Path(it) }
            ?: Path(System.getProperty("user.home"), "Library", "Application Support", "Switchboard", "models")
        return listOf(ModelCatalog.GEMMA_3_4B, ModelCatalog.GEMMA_3_1B)
            .map { it to dir.resolve(it.fileName) }
            .firstOrNull { (spec, path) -> Files.exists(path) && Files.size(path) == spec.sizeBytes }
    }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream(path)) { "missing $path" }.readBytes().decodeToString()

    companion object {
        fun pct(value: Double) = "%.0f%%".format(value * 100)
    }
}
