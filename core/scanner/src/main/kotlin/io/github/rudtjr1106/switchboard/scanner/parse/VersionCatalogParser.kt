package io.github.rudtjr1106.switchboard.scanner.parse

/**
 * gradle/libs.versions.toml 을 별칭 → 좌표 표로 바꾼다
 *
 * - `[libraries]` 의 `retrofit-android` → `libs.retrofit.android` = `com.squareup.retrofit2:retrofit`
 * - `[plugins]` 의 `hilt-android` → `libs.plugins.hilt.android` = `com.google.dagger.hilt.android`
 * - `[bundles]` 의 `network` → `libs.bundles.network` = 좌표들을 `,` 로 이은 값
 *
 * Gradle 은 별칭의 `-`, `_`, `.` 을 모두 `.` 접근자로 바꾸므로 여기서도 그렇게 맞춘다.
 */
internal object VersionCatalogParser {

    private val section = Regex("""^\s*\[(\w+)]\s*$""")
    private val entry = Regex("""^\s*([A-Za-z0-9_.\-]+)\s*=\s*(.+?)\s*$""")
    private val pair = Regex("""([\w.\-]+)\s*=\s*["']([^"']*)["']""")
    private val quoted = Regex("""["']([^"']+)["']""")

    fun parse(text: String): Map<String, String> {
        val libraries = LinkedHashMap<String, String>()
        val plugins = LinkedHashMap<String, String>()
        val bundles = LinkedHashMap<String, List<String>>()

        var current: String? = null
        for (line in logicalLines(text)) {
            val header = section.find(line)
            if (header != null) {
                current = header.groupValues[1].lowercase()
                continue
            }
            val match = entry.find(line) ?: continue
            val alias = match.groupValues[1]
            val value = match.groupValues[2]
            when (current) {
                "libraries" -> libraryCoordinate(value)?.let { libraries[alias] = it }
                "plugins" -> pluginId(value)?.let { plugins[alias] = it }
                "bundles" -> bundles[alias] = quoted.findAll(value).map { it.groupValues[1] }.toList()
            }
        }

        val catalog = LinkedHashMap<String, String>()
        libraries.forEach { (alias, coordinate) -> catalog["libs." + accessor(alias)] = coordinate }
        plugins.forEach { (alias, id) -> catalog["libs.plugins." + accessor(alias)] = id }
        bundles.forEach { (alias, members) ->
            val coordinates = members.mapNotNull { libraries[it] }
            if (coordinates.isNotEmpty()) catalog["libs.bundles." + accessor(alias)] = coordinates.joinToString(",")
        }
        return catalog
    }

    fun accessor(alias: String): String = alias.replace(Regex("[-_]"), ".")

    private fun libraryCoordinate(value: String): String? {
        if (value.startsWith("{")) {
            val fields = pair.findAll(value).associate { it.groupValues[1] to it.groupValues[2] }
            fields["module"]?.let { return withoutVersion(it) }
            val group = fields["group"] ?: return null
            val name = fields["name"] ?: return null
            return "$group:$name"
        }
        return quoted.find(value)?.groupValues?.get(1)?.let(::withoutVersion)
    }

    private fun pluginId(value: String): String? {
        if (value.startsWith("{")) {
            return pair.findAll(value).associate { it.groupValues[1] to it.groupValues[2] }["id"]
        }
        return quoted.find(value)?.groupValues?.get(1)?.substringBefore(':')
    }

    private fun withoutVersion(module: String): String {
        val parts = module.split(':')
        return if (parts.size >= 2) parts[0] + ":" + parts[1] else module
    }

    /** `#` 주석을 떼고, 여러 줄에 걸친 인라인 테이블을 한 줄로 합친다 */
    private fun logicalLines(text: String): List<String> {
        val lines = ArrayList<String>()
        val pending = StringBuilder()
        var depth = 0
        for (raw in text.lineSequence()) {
            val line = stripComment(raw)
            if (depth == 0 && pending.isEmpty()) {
                depth = line.count { it == '{' } - line.count { it == '}' }
                if (depth > 0) pending.append(line).append(' ') else lines += line
            } else {
                pending.append(line).append(' ')
                depth += line.count { it == '{' } - line.count { it == '}' }
                if (depth <= 0) {
                    lines += pending.toString()
                    pending.setLength(0)
                    depth = 0
                }
            }
        }
        if (pending.isNotEmpty()) lines += pending.toString()
        return lines
    }

    private fun stripComment(line: String): String {
        var inQuote: Char? = null
        for ((i, c) in line.withIndex()) {
            when {
                inQuote != null -> if (c == inQuote) inQuote = null
                c == '"' || c == '\'' -> inQuote = c
                c == '#' -> return line.substring(0, i)
            }
        }
        return line
    }
}
