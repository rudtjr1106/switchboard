package io.github.rudtjr1106.switchboard.scanner.parse

import io.github.rudtjr1106.switchboard.scanner.Destination
import io.github.rudtjr1106.switchboard.scanner.NavigationStyle
import java.nio.file.Path

/** 읽어 둔 Kotlin 소스 하나. 목적지 찾기와 연동 여부 확인이 같은 내용을 쓰므로 한 번만 읽는다 */
internal class KotlinSource(val path: Path, val text: String)

internal data class NavigationScan(
    val style: NavigationStyle,
    val destinations: List<Destination>,
    val notes: List<String>,
)

/**
 * 앱의 내비게이션 목적지를 소스에서 찾는다
 *
 * 1. `sealed interface|class X` 안(또는 같은 파일 최상위)의 `@Serializable data object|class` 멤버 → 타입 세이프 Navigation Compose.
 *    후보가 여럿이면 멤버가 가장 많은 sealed 타입을 고른다 (UiState 같은 sealed 타입은 멤버에 @Serializable 이 없어 걸러진다)
 * 2. 그 멤버(또는 sealed 타입)가 `NavKey` 를 구현하면 Navigation 3
 * 3. 둘 다 없으면 `composable("literal")` / `route = "literal"` 문자열 route
 *
 * 컴파일러 없이 정규식과 중괄호 깊이로만 읽는다. 문자열·주석 속 중괄호에 속지 않도록 가린 사본으로 구조를 보고, 주석은 원문에서 읽는다.
 */
internal object DestinationScanner {

    // `::class` 나 `Foo.class` 처럼 선언이 아닌 자리는 앞 글자로 거른다. 이름은 같은 줄에 있어야 한다
    private val declaration = Regex("""(?<![\w.:])(object|class|interface)[ \t]+(\w+)""")
    private val serializable = Regex("""@Serializable\b""")
    private val navKey = Regex("""\bNavKey\b""")
    private val stringRoute = Regex("""(?<![\w.])(?:composable|navigation|dialog)\(\s*(?:route\s*=\s*)?"([^"]+)"""")
    private val routeAssignment = Regex("""(?<![\w.])(?:route|startDestination)\s*=\s*"([^"]+)"""")

    private val preferredNames = setOf(
        "MainDestination", "Destination", "Destinations", "Route", "Routes", "Screen", "Screens",
        "NavRoute", "NavRoutes", "AppRoute", "AppRoutes", "NavDestination", "NavKey", "AppScreen",
    )

    fun scan(sources: List<KotlinSource>, root: Path): NavigationScan {
        val groups = sources.flatMap { source ->
            runCatching { groupsIn(source) }.getOrElse { emptyList() }
        }.filter { it.destinations().isNotEmpty() }

        val best = groups.maxWithOrNull(
            compareBy<Group> { it.destinations().size }
                .thenBy { it.name in preferredNames }
                .thenByDescending { it.file.toString() },
        )
        if (best != null) {
            val style = if (best.isNavKey || best.members.any { it.navKey }) NavigationStyle.NAVIGATION3 else NavigationStyle.NAVIGATION_COMPOSE_TYPESAFE
            val destinations = best.destinations()
            val relative = runCatching { root.relativize(best.file) }.getOrDefault(best.file)
            val notes = mutableListOf("내비게이션 목적지는 $relative 의 ${best.name} 에서 읽었어요 (${destinations.size}개)")
            val others = groups.filter { it !== best }.map { it.name }.distinct()
            if (others.isNotEmpty()) notes += "다른 후보도 있었어요: ${others.joinToString(", ")}. 멤버가 가장 많은 쪽을 골랐어요"
            return NavigationScan(style, destinations, notes)
        }

        val routes = stringRoutes(sources)
        if (routes.isNotEmpty()) {
            return NavigationScan(
                NavigationStyle.STRING_ROUTES,
                routes,
                listOf("타입 세이프 목적지가 없어 문자열 route 에서 화면 ${routes.size}개를 읽었어요"),
            )
        }
        return NavigationScan(NavigationStyle.UNKNOWN, emptyList(), listOf("내비게이션 목적지를 찾지 못했어요. 화면 목록은 직접 채워야 해요"))
    }

    private class Member(
        val name: String,
        val serializable: Boolean,
        val hasArguments: Boolean,
        val comment: String?,
        val navKey: Boolean,
        val file: Path,
        val section: String? = null,
    )

    private class Group(val name: String, val file: Path, val isNavKey: Boolean) {
        val members = mutableListOf<Member>()
        fun destinations(): List<Destination> = members
            .filter { it.serializable || it.navKey || isNavKey }
            .map { Destination(it.name, it.file, it.hasArguments, it.comment, it.section) }
    }

    private class SealedType(val name: String, val body: IntRange?, val group: Group)

    private fun groupsIn(source: KotlinSource): List<Group> {
        val text = source.text
        val sections = sectionHeaders(text)
        if (!text.contains("sealed") && !text.contains("NavKey")) return emptyList()

        val clean = SourceText.stripComments(text)
        val masked = SourceText.maskStrings(clean)
        val depths = SourceText.braceDepths(masked)
        val matches = declaration.findAll(masked).toList()

        val sealedTypes = mutableListOf<SealedType>()
        val groups = LinkedHashMap<String, Group>()
        var navKeyGroup: Group? = null

        // 먼저 sealed 타입과 그 몸통 범위를 알아 둔다
        for ((index, match) in matches.withIndex()) {
            val modifiers = lineModifiers(masked, match.range.first)
            if ("sealed" !in modifiers) continue
            val keyword = match.groupValues[1]
            if (keyword == "object") continue
            val name = match.groupValues[2]
            val limit = matches.getOrNull(index + 1)?.range?.first ?: masked.length
            val (header, body) = headerAndBody(masked, match.range.last + 1, limit)
            val group = Group(name, source.path, navKey.containsMatchIn(header))
            sealedTypes += SealedType(name, body, group)
            groups[name] = group
        }

        for (match in matches) {
            val start = match.range.first
            val keyword = match.groupValues[1]
            val name = match.groupValues[2]
            val modifiers = lineModifiers(masked, start)
            if ("sealed" in modifiers || "companion" in modifiers || "enum" in modifiers || "annotation" in modifiers) continue
            if (keyword == "interface") continue

            val depth = depths[start]
            val nameEnd = match.range.last + 1
            val (constructor, supertypes) = memberHeader(masked, nameEnd)

            // sealed 몸통 바로 안의 멤버 → 그 타입. 최상위 선언이면 상위 타입 이름으로 찾고, NavKey 만 구현하면 파일 단위 묶음
            val nested = sealedTypes.firstOrNull { type ->
                val body = type.body ?: return@firstOrNull false
                start in body && depth == depths[body.first]
            }
            val implemented = if (nested == null && depth == 0) {
                sealedTypes.firstOrNull { type -> Regex("""\b${type.name}\b""").containsMatchIn(supertypes) }
            } else null
            val owner: Group = when {
                nested != null -> nested.group
                implemented != null -> implemented.group
                depth == 0 && navKey.containsMatchIn(supertypes) ->
                    navKeyGroup ?: Group("NavKey", source.path, true).also { navKeyGroup = it; groups["NavKey@" + source.path] = it }
                else -> continue
            }

            val lineStart = masked.lastIndexOf('\n', start - 1) + 1
            val annotations = annotationPrefix(clean, lineStart) + clean.substring(lineStart, start)
            owner.members += Member(
                name = name,
                serializable = serializable.containsMatchIn(annotations),
                hasArguments = keyword == "class" && constructor.isNotBlank(),
                comment = commentAbove(text, clean, lineStart, start),
                navKey = navKey.containsMatchIn(supertypes),
                file = source.path,
                section = sections.lastOrNull { it.first < start }?.second,
            )
        }
        return groups.values.toList()
    }

    /**
     * 구역 제목 주석의 위치와 이름. 목적지 그룹(구분)을 정하는 힌트다
     *
     * - `/**공지 섹션**/`, `/* 커뮤니티 섹션 */` 처럼 '섹션' 으로 끝나는 한 줄 블록 주석
     * - Android Studio 의 `// region 인증` (`//region`) 접기 표시
     */
    private fun sectionHeaders(text: String): List<Pair<Int, String>> {
        val headers = mutableListOf<Pair<Int, String>>()
        sectionBlock.findAll(text).forEach { headers += it.range.first to it.groupValues[1].trim() }
        regionMarker.findAll(text).forEach { headers += it.range.first to it.groupValues[1].trim() }
        return headers.filter { it.second.isNotEmpty() }.sortedBy { it.first }
    }

    /** 선언 키워드 앞, 같은 줄에 있는 수식어들 (`private data`, `sealed`, `companion`) */
    private fun lineModifiers(masked: String, start: Int): Set<String> {
        val lineStart = masked.lastIndexOf('\n', start - 1) + 1
        return masked.substring(lineStart, start).split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
    }

    /** sealed 타입 이름 뒤의 헤더(상위 타입 목록)와 몸통 범위. 몸통이 없으면 (`sealed interface Route : NavKey`) null */
    private fun headerAndBody(masked: String, from: Int, limit: Int): Pair<String, IntRange?> {
        var i = from
        while (i < limit) {
            when (masked[i]) {
                '(' -> {
                    val close = SourceText.matching(masked, i, '(', ')')
                    i = if (close < 0) limit else close + 1
                    continue
                }
                '{' -> {
                    val close = SourceText.matching(masked, i)
                    val body = if (close < 0) (i + 1) until masked.length else (i + 1) until close
                    return masked.substring(from, i) to body
                }
            }
            i++
        }
        val headerEnd = masked.indexOf('\n', from).let { if (it < 0 || it > limit) limit else it }
        return masked.substring(from, headerEnd) to null
    }

    /** 멤버 이름 뒤의 생성자 안쪽과 상위 타입 목록. 상위 타입은 `{` 또는 줄 끝까지 본다 */
    private fun memberHeader(masked: String, from: Int): Pair<String, String> {
        var i = from
        while (i < masked.length && (masked[i] == ' ' || masked[i] == '\t')) i++
        var constructor = ""
        if (i < masked.length && masked[i] == '(') {
            val close = SourceText.matching(masked, i, '(', ')')
            if (close > i) {
                constructor = masked.substring(i + 1, close)
                i = close + 1
            }
        }
        val end = masked.indexOfAny(charArrayOf('{', '\n'), i).let { if (it < 0) masked.length else it }
        val header = masked.substring(i, end)
        val supertypes = if (header.trimStart().startsWith(":")) header.substringAfter(':') else ""
        return constructor to supertypes
    }

    /** 선언 바로 위에 붙은 애노테이션 줄들. 주석은 이미 지워져 있어 빈 줄로 보인다 */
    private fun annotationPrefix(clean: String, lineStart: Int): String {
        val collected = StringBuilder()
        var end = lineStart
        while (end > 0) {
            val prevStart = clean.lastIndexOf('\n', end - 2) + 1
            val line = clean.substring(prevStart, end).trim()
            if (line.isEmpty() || line.startsWith("@")) {
                collected.insert(0, line + "\n")
                end = prevStart
                if (prevStart == 0) break
            } else {
                break
            }
        }
        return collected.toString()
    }

    /**
     * 선언에 붙은 주석. 같은 줄 끝의 `// …` 가 있으면 그것, 없으면 위쪽에서 가장 가까운 주석 덩어리 하나
     *
     * 애노테이션 줄은 건너뛰고, 빈 줄이나 코드를 만나면 멈춘다. 섹션 제목처럼 한 덩어리 더 위에 있는 주석은 가져오지 않는다.
     */
    private fun commentAbove(text: String, clean: String, lineStart: Int, declStart: Int): String? {
        val lineEnd = text.indexOf('\n', declStart).let { if (it < 0) text.length else it }
        val trailing = text.substring(declStart, lineEnd)
        val trailingClean = clean.substring(declStart, lineEnd)
        val slash = trailing.indexOf("//")
        if (slash >= 0 && trailingClean[slash] == ' ') {
            cleanComment(listOf(trailing.substring(slash)))?.let { return it }
        }

        val lines = text.substring(0, lineStart).lines().dropLast(1)
        var i = lines.lastIndex
        while (i >= 0) {
            val line = lines[i].trim()
            when {
                line.startsWith("@") -> i--
                line.endsWith("*/") -> {
                    var start = i
                    while (start >= 0 && !lines[start].contains("/*")) start--
                    if (start < 0) return null
                    return cleanComment(lines.subList(start, i + 1))
                }
                line.startsWith("//") -> {
                    var start = i
                    while (start - 1 >= 0 && lines[start - 1].trim().startsWith("//")) start--
                    return cleanComment(lines.subList(start, i + 1))
                }
                else -> return null
            }
        }
        return null
    }

    private fun cleanComment(lines: List<String>): String? {
        val joined = lines.joinToString(" ") { line ->
            line.trim()
                .removePrefix("/**").removePrefix("/*").removeSuffix("**/").removeSuffix("*/")
                .trim().removePrefix("//").trimStart('*').trim()
        }.replace(Regex("\\s+"), " ").trim()
        return joined.ifEmpty { null }
    }

    private fun stringRoutes(sources: List<KotlinSource>): List<Destination> {
        val seen = LinkedHashMap<String, Destination>()
        for (source in sources) {
            if (!source.text.contains("composable(") && !source.text.contains("route")) continue
            val clean = SourceText.stripComments(source.text)
            val literals = stringRoute.findAll(clean).map { it.groupValues[1] } + routeAssignment.findAll(clean).map { it.groupValues[1] }
            for (literal in literals) {
                val name = literal.substringBefore('/').substringBefore('?').trim()
                if (name.isEmpty() || name.contains('{') || name in seen) continue
                seen[name] = Destination(name, source.path, hasArguments = literal.contains('{') || literal.contains('?'))
            }
        }
        return seen.values.toList()
    }

    private val sectionBlock = Regex("""/\*+\s*([^*\n]*?섹션)\s*\*+/""")
    private val regionMarker = Regex("""//\s*region\b[ \t]*([^\n]*)""")
}
