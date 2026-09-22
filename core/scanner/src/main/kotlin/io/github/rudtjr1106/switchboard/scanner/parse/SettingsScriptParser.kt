package io.github.rudtjr1106.switchboard.scanner.parse

internal data class SettingsScript(
    val rootProjectName: String?,
    /** `:app`, `:presentation:home` 처럼 항상 `:` 로 시작한다. 선언 순서대로 */
    val modulePaths: List<String>,
    /** `project(":x").projectDir = file("…")` 로 바꾼 디렉터리. 모듈 경로 → 루트 기준 상대 경로 */
    val projectDirs: Map<String, String>,
)

/**
 * settings.gradle(.kts) 에서 모듈 목록을 읽는다
 *
 * `include(":a", ":b")`, `include(":a")`, `include ':a', ':b'`, 여러 줄에 걸친 호출을 모두 받는다.
 * 문자열 안의 `//` 나 주석 속 include 에 속지 않도록 주석을 먼저 지운다.
 */
internal object SettingsScriptParser {

    private val rootName = Regex("""rootProject\.name\s*=\s*["']([^"']+)["']""")
    // includeBuild / includeGroupByRegex 는 단어 경계 때문에 걸리지 않는다
    private val includeKeyword = Regex("""(?<![\w.])include\b""")
    private val literal = Regex("""["']([^"']+)["']""")
    private val projectDir = Regex(
        """project\(\s*["']([^"']+)["']\s*\)\.projectDir\s*=\s*(?:new\s+)?[Ff]ile\(\s*["']([^"']+)["']""",
    )

    fun parse(text: String): SettingsScript {
        val clean = SourceText.stripComments(text)
        val masked = SourceText.maskStrings(clean)
        val paths = LinkedHashSet<String>()

        for (match in includeKeyword.findAll(masked)) {
            var i = match.range.last + 1
            while (i < masked.length && masked[i] != '\n' && masked[i].isWhitespace()) i++
            val args: IntRange = if (i < masked.length && masked[i] == '(') {
                val close = SourceText.matching(masked, i, '(', ')')
                if (close < 0) continue
                (i + 1) until close
            } else {
                // Groovy: include ':a', ':b' — 줄 끝이 쉼표면 다음 줄까지 이어진다
                var end = lineEnd(masked, i)
                while (end < masked.length && masked.substring(i, end).trimEnd().endsWith(",")) {
                    end = lineEnd(masked, end + 1)
                }
                i until end
            }
            if (args.isEmpty()) continue
            for (lit in literal.findAll(clean.substring(args))) {
                normalize(lit.groupValues[1])?.let(paths::add)
            }
        }

        val dirs = projectDir.findAll(clean).associate { match ->
            val path = normalize(match.groupValues[1]) ?: match.groupValues[1]
            path to match.groupValues[2].trim().trimStart('.', '/')
        }

        return SettingsScript(
            rootProjectName = rootName.find(clean)?.groupValues?.get(1)?.trim(),
            modulePaths = paths.toList(),
            projectDirs = dirs,
        )
    }

    private fun lineEnd(text: String, from: Int): Int = text.indexOf('\n', from).let { if (it < 0) text.length else it }

    private fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith(":")) trimmed else ":$trimmed"
    }
}
