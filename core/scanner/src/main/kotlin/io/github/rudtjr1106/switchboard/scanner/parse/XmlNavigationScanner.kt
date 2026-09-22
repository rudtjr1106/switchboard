package io.github.rudtjr1106.switchboard.scanner.parse

import io.github.rudtjr1106.switchboard.scanner.Destination
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * `res/navigation` 폴더의 XML 내비게이션 그래프에서 화면을 읽는다 (Fragment 기반 앱)
 *
 * - 화면 이름: 목적지의 `android:id` 이름 (`@+id/homeFragment` → `homeFragment`). 앱에서는
 *   `resources.getResourceEntryName(navController.currentDestination!!.id)` 로 같은 값을 얻는다
 * - 주석 힌트: `android:label`. `@string/…` 이면 strings.xml(values-ko 우선)에서 찾아 넣는다
 * - 구역: 목적지를 감싼 중첩 `<navigation>` 의 label(없으면 id). 그래프 파일이 여러 개면 파일의 그래프 이름
 */
internal object XmlNavigationScanner {

    fun scan(root: Path): List<Destination> {
        val graphs = mutableListOf<Path>()
        val strings = mutableListOf<Path>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (dir != root && dir.name in skipped) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val path = file.toString().replace('\\', '/')
                if (file.name.endsWith(".xml") && "/src/main/res/navigation/" in path) graphs.add(file)
                if (file.name == "strings.xml" && ("/src/main/res/values/" in path || "/src/main/res/values-ko/" in path)) strings.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        if (graphs.isEmpty()) return emptyList()

        // values 를 먼저, values-ko 를 나중에 넣어 한국어가 덮어쓰게 한다
        val stringValues = LinkedHashMap<String, String>()
        strings.sortedBy { if ("values-ko" in it.toString()) 1 else 0 }.forEach { file ->
            val text = runCatching { file.readText() }.getOrDefault("")
            stringEntry.findAll(text).forEach { stringValues[it.groupValues[1]] = unescape(it.groupValues[2]) }
        }

        val seen = LinkedHashMap<String, Destination>()
        for (graph in graphs.sorted()) {
            val text = runCatching { graph.readText() }.getOrDefault("")
            parse(graph, text, stringValues, multipleFiles = graphs.size > 1).forEach { seen.putIfAbsent(it.name, it) }
        }
        return seen.values.toList()
    }

    private fun parse(file: Path, text: String, strings: Map<String, String>, multipleFiles: Boolean): List<Destination> {
        val clean = SourceText.stripXmlComments(text)
        // <navigation> 여닫는 위치로 목적지가 어느 중첩 그래프 안에 있는지 안다
        data class Graph(val start: Int, var end: Int, val name: String?)
        val graphs = mutableListOf<Graph>()
        val open = ArrayDeque<Graph>()
        navigationTag.findAll(clean).forEach { m ->
            if (m.value.startsWith("</")) {
                open.removeLastOrNull()?.let { it.end = m.range.last }
            } else {
                val attrs = m.groupValues[1]
                val name = label(attrs, strings) ?: id(attrs)
                val graph = Graph(m.range.first, clean.length, name)
                graphs += graph
                if (!m.value.endsWith("/>")) open.addLast(graph)
            }
        }
        val rootGraph = graphs.firstOrNull()
        val fileSection = if (multipleFiles) rootGraph?.name ?: file.name.removeSuffix(".xml") else null

        return destinationTag.findAll(clean).mapNotNull { m ->
            val attrs = m.groupValues[2]
            val id = id(attrs) ?: return@mapNotNull null
            val start = m.range.first
            val body = if (m.value.endsWith("/>")) "" else clean.substring(m.range.last, clean.indexOf("</${m.groupValues[1]}>", m.range.last).takeIf { it > 0 } ?: m.range.last)
            val nested = graphs.filter { it !== rootGraph && start in it.start..it.end }.maxByOrNull { it.start }
            Destination(
                name = id,
                file = file,
                hasArguments = "<argument" in body,
                comment = label(attrs, strings),
                section = nested?.name ?: fileSection,
            )
        }.toList()
    }

    private fun id(attrs: String): String? = idAttr.find(attrs)?.groupValues?.get(1)

    private fun label(attrs: String, strings: Map<String, String>): String? {
        val raw = labelAttr.find(attrs)?.groupValues?.get(1)?.trim() ?: return null
        val value = if (raw.startsWith("@string/")) strings[raw.removePrefix("@string/")] else raw.takeUnless { it.startsWith("@") }
        return value?.takeIf { it.isNotBlank() }
    }

    private fun unescape(value: String) = value.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", " ").trim()

    private val skipped = setOf("build", ".git", ".gradle", ".idea", "node_modules")
    private val navigationTag = Regex("""<navigation\b([^>]*)>|</navigation>""")
    private val destinationTag = Regex("""<(fragment|dialog|activity|composable)\b([^>]*?)/?>""")
    private val idAttr = Regex("""android:id\s*=\s*"@\+?id/([\w.]+)"""")
    private val labelAttr = Regex("""android:label\s*=\s*"([^"]*)"""")
    private val stringEntry = Regex("""<string\s+name="([\w.]+)"[^>]*>([^<]*)</string>""")
}
