package io.github.rudtjr1106.switchboard.app.setup

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.ai.CodeAdapter
import io.github.rudtjr1106.switchboard.scanner.AndroidProject
import io.github.rudtjr1106.switchboard.scanner.DiFramework
import io.github.rudtjr1106.switchboard.scanner.GeneratedFile
import io.github.rudtjr1106.switchboard.scanner.HttpStack
import io.github.rudtjr1106.switchboard.scanner.ProjectFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

private val logger = KotlinLogging.logger {}

/**
 * 스캐너가 알아보지 못한 부분만 온디바이스 AI 에게 맡긴다
 *
 * 감지된 조합(Hilt·Koin + Retrofit·Ktor)은 템플릿이 이미 맞으므로 건드리지 않는다. DI 나 HTTP 를 못 찾았을 때,
 * 또는 Hilt 없는 Dagger 일 때만 해당 파일을 고르고, 프로젝트의 실제 코드(비슷한 모듈·네트워크 클라이언트)를
 * 근거로 함께 보여줘 모델이 추측하지 않고 따라 쓰게 한다. 결과는 [reject] 검사를 통과할 때만 쓴다.
 */
object AiAdaptation {

    data class Target(val index: Int, val file: GeneratedFile, val reason: String)

    /** AI 가 고칠 만한 파일. 없으면 빈 목록 (템플릿으로 충분하다) */
    fun targets(project: AndroidProject, files: List<GeneratedFile>): List<Target> {
        val out = mutableListOf<Target>()
        fun pick(nameSuffix: String, reason: String) {
            files.withIndex().firstOrNull { it.value.path.name == nameSuffix }?.let { out += Target(it.index, it.value, reason) }
        }
        when (project.di) {
            DiFramework.NONE -> pick("RemoteConfigContainer.kt", "DI 프레임워크를 못 찾아서 프로젝트의 의존성 연결 방식에 맞춰요")
            DiFramework.DAGGER -> pick("RemoteConfigModule.kt", "Hilt 없는 Dagger 라 프로젝트의 기존 @Module 모양에 맞춰요")
            else -> Unit
        }
        if (project.http == HttpStack.NONE) {
            pick("RemoteConfigApi.kt", "Retrofit·Ktor 가 없어 프로젝트가 쓰는 HTTP 라이브러리로 바꿔요")
            pick("RemoteConfigRemoteDataSourceImpl.kt", "Retrofit·Ktor 가 없어 프로젝트가 쓰는 HTTP 라이브러리로 바꿔요")
        }
        return out.distinctBy { it.index }
    }

    /** 모델에게 줄 프로젝트 설명과 실제 코드 근거 */
    suspend fun projectNotes(project: AndroidProject): String = withContext(Dispatchers.IO) {
        val facts = ProjectFacts.of(project)
        val evidence = evidence(project)
        buildString {
            appendLine("프로젝트: ${project.name}")
            appendLine("모듈: ${project.modules.joinToString(", ") { "${it.path}(${it.namespace ?: "?"})" }}")
            appendLine("감지한 DI: ${project.di}, HTTP: ${project.http}, 내비게이션: ${project.navigation}, Compose: ${facts.usesCompose}")
            appendLine("의존성: ${facts.coordinates.take(MAX_COORDINATES).joinToString(", ")}")
            if (evidence.isNotEmpty()) {
                appendLine()
                appendLine("이 프로젝트의 실제 코드 (이 방식을 따라 써):")
                evidence.forEach { (path, code) ->
                    appendLine("// ${project.root.relativize(path)}")
                    appendLine(code)
                    appendLine()
                }
            }
        }
    }

    /**
     * 모델이 쓴 파일을 받아도 되는지. 패키지·주요 선언 이름이 그대로고, 괄호가 맞고, 길이가 크게 달라지지 않아야 한다
     *
     * @return 받을 수 없는 이유. null 이면 받는다
     */
    fun reject(template: String, adapted: String): String? {
        val code = adapted.trim()
        if (code.isEmpty()) return "빈 답"
        if ("```" in code) return "코드 블록 표시가 남음"
        val pkg = packageLine.find(template)?.value
        if (pkg != null && pkg !in code) return "package 가 바뀜"
        val mainDeclaration = declaration.find(template)?.groupValues?.get(2)
        if (mainDeclaration != null && !Regex("""\b(class|object|interface|fun)\s+$mainDeclaration\b""").containsMatchIn(code)) {
            return "$mainDeclaration 선언이 없어짐"
        }
        if (!balanced(code)) return "괄호 짝이 맞지 않음"
        val ratio = code.length.toDouble() / template.trim().length.coerceAtLeast(1)
        if (ratio !in 0.4..3.0) return "길이가 너무 달라짐"
        return null
    }

    /** 파일마다 구체적으로 무엇을 바꿀지. 근거 코드만 주면 작은 모델은 템플릿을 거의 그대로 돌려준다 */
    fun task(target: Target): String = when (target.file.path.name) {
        "RemoteConfigModule.kt" ->
            "할 일: 위 프로젝트의 @Module 들이 이미 제공하는 타입만 @Provides 함수의 파라미터로 받게 고쳐. " +
                "Context 를 제공하는 곳이 없고 Application 을 제공하면 Context 대신 Application 을 받아 그 cacheDir 를 써. " +
                "그 밖의 구조·이름·주석은 그대로 둬."
        "RemoteConfigContainer.kt" ->
            "할 일: 위 프로젝트의 의존성 연결 방식(컨테이너·서비스 로케이터)에 맞춰 같은 객체를 만들게 고쳐. 공개 함수 이름과 반환 타입은 그대로 둬."
        else ->
            "할 일: Retrofit 대신 위 프로젝트가 이미 쓰는 HTTP 라이브러리로 같은 동작을 구현해. 클래스·함수 이름과 반환 타입은 그대로 둬."
    }

    suspend fun adapt(adapter: CodeAdapter, target: Target, notes: String): GeneratedFile? {
        val adapted = runCatching { adapter.adapt(target.file.path.name, target.file.content, notes + "\n" + task(target)) }
            .onFailure { logger.warn(it) { "AI 코드 적응 실패: ${target.file.path}" } }
            .getOrNull() ?: return null
        val problem = reject(target.file.content, adapted)
        if (problem != null) {
            logger.info { "AI 결과를 버림(${target.file.path.name}): $problem" }
            return null
        }
        return target.file.copy(content = fixImports(adapted.trim()) + "\n", adaptedByAi = true)
    }

    /**
     * 모델이 타입을 바꾸고 import 를 빠뜨리는 일이 잦다 (Context → Application). 자주 쓰는 타입의 import 를 채우고,
     * 더 이상 쓰지 않는 그 타입들의 import 는 뺀다. 모르는 타입은 건드리지 않는다
     */
    fun fixImports(code: String): String {
        val lines = code.lines().toMutableList()
        val body = lines.filterNot { it.trimStart().startsWith("import ") }.joinToString("\n")
        fun used(simple: String) = Regex("""\b$simple\b""").containsMatchIn(body)
        val imported = lines.filter { it.trimStart().startsWith("import ") }.map { it.trim().removePrefix("import ").trim() }.toSet()
        // 쓰지 않게 된 알려진 import 는 뺀다
        lines.removeAll { line ->
            val fq = line.trim().removePrefix("import ").trim()
            line.trimStart().startsWith("import ") && fq in KNOWN_TYPES.values && !used(fq.substringAfterLast('.'))
        }
        val missing = KNOWN_TYPES.filter { (simple, fq) -> fq !in imported && used(simple) }.values.sorted()
        if (missing.isEmpty()) return lines.joinToString("\n")
        val lastImport = lines.indexOfLast { it.trimStart().startsWith("import ") }
        val insertAt = if (lastImport >= 0) lastImport + 1 else (lines.indexOfFirst { it.startsWith("package ") } + 1).let { if (it == 0) 0 else it + 1 }
        lines.addAll(insertAt, missing.map { "import $it" })
        return lines.joinToString("\n")
    }

    private val KNOWN_TYPES = mapOf(
        "Application" to "android.app.Application",
        "Context" to "android.content.Context",
        "File" to "java.io.File",
        "TimeUnit" to "java.util.concurrent.TimeUnit",
        "OkHttpClient" to "okhttp3.OkHttpClient",
        "Cache" to "okhttp3.Cache",
        "Request" to "okhttp3.Request",
        "Retrofit" to "retrofit2.Retrofit",
        "GsonConverterFactory" to "retrofit2.converter.gson.GsonConverterFactory",
        "Inject" to "javax.inject.Inject",
        "Singleton" to "javax.inject.Singleton",
        "Qualifier" to "javax.inject.Qualifier",
        "Named" to "javax.inject.Named",
        "Module" to "dagger.Module",
        "Provides" to "dagger.Provides",
        "Binds" to "dagger.Binds",
    )

    // ---- 근거 찾기 ----

    /** DI·네트워크 쪽에서 이 프로젝트가 실제로 어떻게 쓰는지 보여줄 파일 몇 개 (앞부분만) */
    private fun evidence(project: AndroidProject): List<Pair<Path, String>> {
        val wanted = buildList {
            if (project.di == DiFramework.DAGGER) add(Regex("""@Module\b"""))
            if (project.di == DiFramework.NONE) add(Regex("""\b(object|class)\s+\w*(ServiceLocator|Container|Injector|Graph|Dependencies)\b"""))
            if (project.http == HttpStack.NONE) add(Regex("""OkHttpClient\b|Volley\.newRequestQueue|Fuel\.|HttpURLConnection|HttpClient\("""))
        }
        if (wanted.isEmpty()) return emptyList()
        val files = runCatching {
            Files.walk(project.root, MAX_DEPTH).use { stream ->
                stream.filter { it.isRegularFile() && it.extension == "kt" && isMainSource(it) }.limit(MAX_FILES_SCANNED).toList()
            }
        }.getOrDefault(emptyList())
        val found = mutableListOf<Pair<Path, String>>()
        for (pattern in wanted) {
            val hit = files.firstOrNull { file ->
                file.name !in generatedNames && runCatching { pattern.containsMatchIn(file.readText()) }.getOrDefault(false)
            } ?: continue
            if (found.none { it.first == hit }) found += hit to hit.readText().take(MAX_EVIDENCE_CHARS)
        }
        return found
    }

    private fun isMainSource(path: Path): Boolean {
        val text = path.toString().replace('\\', '/')
        return ("/src/main/java/" in text || "/src/main/kotlin/" in text) && "/build/" !in text
    }

    private fun balanced(code: String): Boolean {
        // 문자열·주석 안의 괄호는 대충 걸러낸다. 템플릿 수준의 코드에는 충분하다
        val stripped = code.replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "").replace(Regex("\"(\\\\.|[^\"\\\\])*\""), "")
            .replace(Regex("//[^\n]*"), "").replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        return stripped.count { it == '{' } == stripped.count { it == '}' } && stripped.count { it == '(' } == stripped.count { it == ')' }
    }

    private val packageLine = Regex("""(?m)^package\s+[\w.]+""")
    private val declaration = Regex("""(?m)^(?:internal\s+|abstract\s+|data\s+)*(class|object|interface)\s+(\w+)""")
    private val generatedNames = setOf("RemoteConfigModule.kt", "RemoteConfigContainer.kt", "RemoteConfigApi.kt", "RemoteConfigRemoteDataSourceImpl.kt")

    private const val MAX_DEPTH = 14
    private const val MAX_FILES_SCANNED = 4_000L
    private const val MAX_EVIDENCE_CHARS = 1_800
    private const val MAX_COORDINATES = 60
}
