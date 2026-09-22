package io.github.rudtjr1106.switchboard.scanner.parse

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

/** 컨벤션 플러그인 하나(또는 전체)가 모듈에 더하는 의존성(`group:artifact`)과 플러그인 id */
internal data class Contribution(val dependencies: Set<String>, val plugins: Set<String>) {
    val isEmpty: Boolean get() = dependencies.isEmpty() && plugins.isEmpty()

    operator fun plus(other: Contribution) = Contribution(dependencies + other.dependencies, plugins + other.plugins)

    companion object {
        val EMPTY = Contribution(emptySet(), emptySet())
    }
}

/**
 * @property byPluginId 컨벤션 플러그인 id → 그 플러그인이 (다른 컨벤션 플러그인을 거쳐서라도) 더하는 것
 * @property all 컨벤션 소스 전체에서 읽은 것. 어느 모듈에 붙는지 몰라도 프로젝트 전체 판단(DI, HTTP)에는 쓸 수 있다
 */
internal data class ConventionFacts(val dirs: List<Path>, val byPluginId: Map<String, Contribution>, val all: Contribution)

/**
 * `build-logic`, `buildSrc`, `includeBuild(...)` 의 컨벤션 플러그인이 모듈에 넣는 의존성과 플러그인을 읽는다
 *
 * 요즘 Android 프로젝트(nowinandroid 식)는 모듈 build 스크립트에 `alias(libs.plugins.app.android.hilt)` 한 줄만 두고
 * 실제 Hilt·Compose 의존성은 컨벤션 플러그인 안에서 `libs.findLibrary("hilt.android")` 로 넣는다. 이걸 읽지 않으면
 * Hilt 프로젝트를 DI 없음으로, Compose 프로젝트를 Compose 없음으로 잘못 본다.
 *
 * 플러그인 id 는 두 가지로 찾는다.
 * - `gradlePlugin { plugins { register("x") { id = "…"; implementationClass = "…" } } }` 의 id 와 구현 클래스 파일
 * - precompiled script plugin: `src/main/kotlin/app.android.compose.gradle.kts` 면 id 는 `app.android.compose`
 */
internal object ConventionPluginScanner {

    fun scan(root: Path, settingsText: String, catalog: Map<String, String>): ConventionFacts {
        val dirs = (listOf("build-logic", "buildSrc") + includeBuild.findAll(settingsText).map { it.groupValues[1] })
            .map { root.resolve(it).normalize() }
            .distinct()
            .filter { it.isDirectory() && it != root }
        if (dirs.isEmpty()) return ConventionFacts(emptyList(), emptyMap(), Contribution.EMPTY)

        val files = dirs.flatMap(::sources)
        val texts = files.associateWith { runCatching { it.readText() }.getOrDefault("") }
        val byFile = texts.mapValues { (_, text) -> extract(text, catalog) }

        // 플러그인 id → 그 플러그인을 구현한 파일
        val implementations = LinkedHashMap<String, Path>()
        for ((_, text) in texts) {
            for (block in registration.findAll(text)) {
                val body = block.groupValues[1]
                val id = pluginIdAssignment.find(body)?.groupValues?.get(1) ?: continue
                val className = implementationClass.find(body)?.groupValues?.get(1)?.substringAfterLast('.') ?: continue
                files.firstOrNull { it.name.substringBefore('.') == className }?.let { implementations[id] = it }
            }
        }
        for (file in files) {
            val name = file.name
            if (name.endsWith(".gradle.kts") || (name.endsWith(".gradle") && "src" in file.toString())) {
                val id = name.removeSuffix(".gradle.kts").removeSuffix(".gradle")
                if ('.' in id || '-' in id) implementations.putIfAbsent(id, file)
            }
        }

        // 컨벤션 플러그인이 다른 컨벤션 플러그인을 적용하면 따라가서 합친다
        fun resolve(id: String, seen: Set<String>): Contribution {
            val file = implementations[id] ?: return Contribution.EMPTY
            val own = byFile[file] ?: Contribution.EMPTY
            return own.plugins.filter { it in implementations && it !in seen }
                .fold(own) { acc, nested -> acc + resolve(nested, seen + nested) }
        }
        val byPluginId = implementations.keys.associateWith { resolve(it, setOf(it)) }
        val all = byFile.values.fold(Contribution.EMPTY) { acc, c -> acc + c }
        return ConventionFacts(dirs, byPluginId, all)
    }

    /** 파일 하나에서 의존성과 플러그인 id 를 뽑는다 */
    fun extract(text: String, catalog: Map<String, String>): Contribution {
        val dependencies = LinkedHashSet<String>()
        val plugins = LinkedHashSet<String>()
        fun addLibrary(key: String) = catalog[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let(dependencies::addAll)

        findLibrary.findAll(text).forEach { addLibrary("libs." + accessor(it.groupValues[1])) }
        findBundle.findAll(text).forEach { addLibrary("libs.bundles." + accessor(it.groupValues[1])) }
        findPlugin.findAll(text).forEach { m -> catalog["libs.plugins." + accessor(m.groupValues[1])]?.let(plugins::add) }
        // precompiled script plugin 은 libs.hilt.android 처럼 접근자를 바로 쓴다. `.get()` 같은 꼬리는 잘라 가며 찾는다
        typeSafeAccessor.findAll(text).forEach { m ->
            var key = "libs." + m.groupValues[1]
            while (key.count { it == '.' } >= 1) {
                val value = catalog[key]
                if (value != null) {
                    if (key.startsWith("libs.plugins.")) plugins += value else addLibrary(key)
                    break
                }
                key = key.substringBeforeLast('.')
            }
        }
        coordinate.findAll(text).forEach { dependencies += it.groupValues[1] + ":" + it.groupValues[2] }
        pluginIdCall.findAll(text).forEach { plugins += it.groupValues[1] }
        return Contribution(dependencies, plugins)
    }

    /** `hilt-android` → `hilt.android`. 카탈로그 별칭의 - 와 _ 는 접근자에서 . 이 된다 */
    private fun accessor(alias: String) = alias.replace('-', '.').replace('_', '.')

    private fun sources(dir: Path): List<Path> {
        val out = mutableListOf<Path>()
        Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(d: Path, attrs: BasicFileAttributes): FileVisitResult =
                if (d != dir && d.name in setOf("build", ".gradle", ".kotlin")) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val name = file.name
                if (name.endsWith(".kt") || name.endsWith(".kts") || name.endsWith(".gradle") || name.endsWith(".groovy")) out.add(file)
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
        return out.sorted()
    }

    private val includeBuild = Regex("""includeBuild\(\s*["']([^"']+)["']""")
    private val registration = Regex("""(?:register|create)\(\s*["'][^"']*["']\s*\)\s*\{([^}]*)}""")
    private val pluginIdAssignment = Regex("""\bid\s*=\s*["']([^"']+)["']""")
    private val implementationClass = Regex("""implementationClass\s*=\s*["']([^"']+)["']""")
    private val findLibrary = Regex("""findLibrary\(\s*["']([^"']+)["']\s*\)""")
    private val findBundle = Regex("""findBundle\(\s*["']([^"']+)["']\s*\)""")
    private val findPlugin = Regex("""findPlugin\(\s*["']([^"']+)["']\s*\)""")
    private val typeSafeAccessor = Regex("""\blibs\.(?!versions\b|findLibrary|findPlugin|findBundle|findVersion)([A-Za-z][\w.]*)""")
    private val coordinate = Regex("""["']([A-Za-z][\w\-]*(?:\.[\w\-]+)+):([\w.\-]+)(?::[^"']*)?["']""")
    private val pluginIdCall = Regex("""(?:\bapply|\bid|pluginManager\.apply)\(\s*(?:plugin\s*=\s*)?["']([A-Za-z][\w.\-]*)["']\s*\)""")
}
