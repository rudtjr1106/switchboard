package io.github.rudtjr1106.switchboard.scanner.parse

internal data class BuildScript(
    val namespace: String?,
    val applicationId: String?,
    /** 플러그인 별칭(`libs.plugins.hilt.android`) 또는 id (`com.android.application`). 별칭은 스캐너가 카탈로그로 푼다 */
    val pluginRefs: List<String>,
    /** `libs.retrofit.android`, `com.squareup.retrofit2:retrofit:3.0.0`, `project(:domain)` */
    val dependencyRefs: List<String>,
    /** `buildFeatures { compose = true }` */
    val composeEnabled: Boolean,
)

/**
 * build.gradle(.kts) 를 정규식으로 읽는다. Kotlin DSL 과 Groovy DSL 을 같이 받는다
 *
 * Gradle 을 띄우지 않으므로 값이 변수나 함수 결과인 경우(`namespace = appId`)는 못 읽는다. 그런 항목은 null 로 두고
 * 스캐너가 다른 단서(소스 패키지)로 보완한다.
 */
internal object BuildScriptParser {

    private val namespace = Regex("""(?<![\w.])namespace\s*=?\s*["']([^"']+)["']""")
    // applicationIdSuffix 는 뒤에 공백·등호·따옴표가 오지 않아 걸리지 않는다
    private val applicationId = Regex("""(?<![\w.])applicationId\s*=?\s*["']([^"']+)["']""")
    private val composeFeature = Regex("""(?<![\w.])compose\s*=?\s*true\b""")

    private val pluginAlias = Regex("""alias\(\s*(libs\.plugins\.[\w.]+)\s*\)""")
    private val pluginId = Regex("""(?<![\w.])id\s*\(?\s*["']([^"']+)["']""")
    private val pluginKotlin = Regex("""(?<![\w.])kotlin\(\s*["']([^"']+)["']\s*\)""")
    private val applyPlugin = Regex("""apply\s+plugin\s*:\s*["']([^"']+)["']""")
    private val applyPluginKts = Regex("""apply\(\s*plugin\s*=\s*["']([^"']+)["']\s*\)""")

    private val catalogRef = Regex("""(?<![\w.])libs\.((?!plugins\.|versions\.)[\w.]+)""")
    private val coordinate = Regex("""["']([\w.\-]+:[\w.\-]+(?::[^"']*)?)["']""")
    private val projectRef = Regex("""(?<![\w.])project\(\s*["']([^"']+)["']""")
    private val projectAccessor = Regex("""(?<![\w.])projects\.([\w.]+)""")
    private val kotlinDependency = Regex("""(?<![\w.])kotlin\(\s*["']([\w\-]+)["']\s*\)""")

    fun parse(text: String): BuildScript {
        val clean = SourceText.stripComments(text)
        val masked = SourceText.maskStrings(clean)

        val dependencyBlocks = SourceText.blocks(masked, "dependencies")
        val pluginBlocks = SourceText.blocks(masked, "plugins")
        val withoutDependencies = SourceText.blank(clean, dependencyBlocks)

        val pluginScope = if (pluginBlocks.isEmpty()) withoutDependencies else pluginBlocks.joinToString("\n") { clean.substring(it) }
        val plugins = LinkedHashSet<String>()
        pluginAlias.findAll(pluginScope).forEach { plugins += it.groupValues[1] }
        pluginId.findAll(pluginScope).forEach { plugins += it.groupValues[1] }
        pluginKotlin.findAll(pluginScope).forEach { plugins += "org.jetbrains.kotlin." + it.groupValues[1] }
        applyPlugin.findAll(withoutDependencies).forEach { plugins += it.groupValues[1] }
        applyPluginKts.findAll(withoutDependencies).forEach { plugins += it.groupValues[1] }

        val dependencies = LinkedHashSet<String>()
        for (block in dependencyBlocks) {
            val body = clean.substring(block)
            catalogRef.findAll(body).forEach { dependencies += "libs." + it.groupValues[1].removeSuffix(".get") }
            coordinate.findAll(body).forEach { dependencies += it.groupValues[1] }
            projectRef.findAll(body).forEach { dependencies += "project(" + normalizeProjectPath(it.groupValues[1]) + ")" }
            projectAccessor.findAll(body).forEach { dependencies += "project(:" + it.groupValues[1].replace('.', ':') + ")" }
            kotlinDependency.findAll(body).forEach { dependencies += "org.jetbrains.kotlin:kotlin-" + it.groupValues[1] }
        }

        return BuildScript(
            namespace = namespace.find(withoutDependencies)?.groupValues?.get(1)?.trim(),
            applicationId = applicationId.find(withoutDependencies)?.groupValues?.get(1)?.trim(),
            pluginRefs = plugins.toList(),
            dependencyRefs = dependencies.toList(),
            composeEnabled = composeFeature.containsMatchIn(withoutDependencies),
        )
    }

    private fun normalizeProjectPath(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith(":")) trimmed else ":$trimmed"
    }
}
