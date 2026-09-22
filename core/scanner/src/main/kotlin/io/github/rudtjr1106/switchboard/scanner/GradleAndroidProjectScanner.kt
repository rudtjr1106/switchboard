package io.github.rudtjr1106.switchboard.scanner

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.scanner.parse.BuildScript
import io.github.rudtjr1106.switchboard.scanner.parse.BuildScriptParser
import io.github.rudtjr1106.switchboard.scanner.parse.Contribution
import io.github.rudtjr1106.switchboard.scanner.parse.ConventionPluginScanner
import io.github.rudtjr1106.switchboard.scanner.parse.DestinationScanner
import io.github.rudtjr1106.switchboard.scanner.parse.KotlinSource
import io.github.rudtjr1106.switchboard.scanner.parse.NavigationScan
import io.github.rudtjr1106.switchboard.scanner.parse.SettingsScriptParser
import io.github.rudtjr1106.switchboard.scanner.parse.VersionCatalogParser
import io.github.rudtjr1106.switchboard.scanner.parse.XmlNavigationScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

private val logger = KotlinLogging.logger {}

/**
 * Gradle 파일과 Kotlin 소스를 직접 읽어 Android 프로젝트를 파악한다
 *
 * Gradle 을 띄우지 않는다. 대신 settings / build 스크립트 / 버전 카탈로그를 정규식으로 읽고, 소스는 줄 단위로 훑는다.
 * 그래서 빠르고 Android SDK 없이도 돌지만, 스크립트 로직으로 정해지는 값은 못 읽는다. 그런 경우는 [AndroidProject.notes] 에 남긴다.
 */
class GradleAndroidProjectScanner : AndroidProjectScanner {

    override suspend fun scan(root: Path): AndroidProject = withContext(Dispatchers.IO) {
        scanNow(root.toAbsolutePath().normalize())
    }

    private fun scanNow(root: Path): AndroidProject {
        val settingsFile = listOf("settings.gradle.kts", "settings.gradle").map(root::resolve).firstOrNull { it.isRegularFile() }
            ?: throw ProjectScanException("settings.gradle(.kts) 를 찾지 못했어요. Android 프로젝트의 루트 폴더를 고르세요: $root")
        val settingsText = settingsFile.readText()
        val settings = SettingsScriptParser.parse(settingsText)
        logger.debug { "settings: ${settings.modulePaths}" }

        val notes = mutableListOf<String>()
        val catalogFile = root.resolve("gradle/libs.versions.toml")
        val catalog = if (catalogFile.isRegularFile()) VersionCatalogParser.parse(catalogFile.readText()) else {
            notes += "gradle/libs.versions.toml 이 없어 의존성은 문자열 좌표로만 읽었어요"
            emptyMap()
        }

        val conventions = ConventionPluginScanner.scan(root, settingsText, catalog)
        val appliedConventions = LinkedHashSet<String>()
        val tree = SourceTree.collect(root)
        val missingScripts = mutableListOf<String>()
        val inferredNamespaces = mutableListOf<String>()
        val modules = settings.modulePaths.map { path ->
            val dir = root.resolve(settings.projectDirs[path] ?: path.removePrefix(":").replace(':', '/')).normalize()
            val scriptFile = listOf("build.gradle.kts", "build.gradle").map(dir::resolve).firstOrNull { it.isRegularFile() }
            val script = scriptFile?.let { BuildScriptParser.parse(it.readText()) }
                ?: BuildScript(null, null, emptyList(), emptyList(), false).also { missingScripts += path }
            val declared = script.pluginRefs.map { ref -> if (ref.startsWith("libs.plugins.")) catalog[ref] ?: ref else ref }
            // 컨벤션 플러그인이 넣는 의존성·플러그인을 그 플러그인을 쓰는 모듈에 붙인다
            val conventionIds = declared.filter { it in conventions.byPluginId }
            appliedConventions += conventionIds
            val contribution = conventionIds.map { conventions.byPluginId.getValue(it) }.fold(Contribution.EMPTY, Contribution::plus)
            val plugins = (declared + contribution.plugins).distinct()
            val namespace = script.namespace
                ?: manifestPackage(dir)
                ?: tree.inferPackage(dir, path)?.also { inferredNamespaces += "$path → $it" }
                ?: script.applicationId
            GradleModule(
                path = path,
                dir = dir,
                namespace = namespace,
                isApplication = "com.android.application" in plugins,
                plugins = plugins,
                dependencies = script.dependencyRefs + contribution.dependencies,
            )
        }
        if (missingScripts.isNotEmpty()) notes += "build 스크립트를 찾지 못한 모듈: ${missingScripts.joinToString(", ")}"
        if (inferredNamespaces.isNotEmpty()) notes += "namespace 가 없어 소스 패키지에서 추정한 모듈: ${inferredNamespaces.joinToString(", ")}"

        val appModule = modules.firstOrNull { it.isApplication }
        if (appModule == null) notes += "com.android.application 플러그인을 쓰는 모듈을 찾지 못했어요"

        val coordinates = modules.flatMapTo(LinkedHashSet()) { ProjectFacts.coordinatesOf(it, catalog) }
        val plugins = modules.flatMapTo(LinkedHashSet()) { it.plugins }
        if (conventions.dirs.isNotEmpty()) {
            val where = conventions.dirs.joinToString(", ") { root.relativize(it).toString() }
            if (appliedConventions.isNotEmpty()) {
                notes += "$where 의 컨벤션 플러그인 ${appliedConventions.size}개(${appliedConventions.joinToString(", ")})가 넣는 의존성까지 읽었어요"
            } else if (!conventions.all.isEmpty) {
                // 어느 모듈에 붙는지 모르지만 프로젝트 전체 판단(DI·HTTP·Compose)에는 넣는다
                coordinates += conventions.all.dependencies
                plugins += conventions.all.plugins
                notes += "$where 에서 의존성을 읽었지만 어느 모듈에 붙는지는 알아내지 못했어요. 프로젝트 전체 판단에만 썼어요"
            }
        }
        val di = detectDi(coordinates, plugins)
        val http = detectHttp(coordinates, notes)
        if (di == DiFramework.NONE) notes += "DI 프레임워크를 찾지 못해 수동 생성 코드로 만들어요"
        if (di == DiFramework.DAGGER) notes += "Hilt 없이 Dagger 를 써요. 만든 모듈을 앱의 @Component 에 직접 연결해야 해요"
        if (!ProjectFacts.usesCompose(coordinates, plugins)) notes += "Compose 를 쓰지 않아 안내 UI 는 직접 만들어야 해요"

        var navigation = DestinationScanner.scan(tree.kotlin, root)
        if (navigation.style == NavigationStyle.UNKNOWN) {
            val xml = XmlNavigationScanner.scan(root)
            if (xml.isNotEmpty()) {
                navigation = NavigationScan(NavigationStyle.XML_GRAPH, xml, listOf("res/navigation 의 XML 그래프에서 화면 ${xml.size}개를 읽었어요. 화면 이름은 android:id 이름이에요"))
            }
        }
        notes += navigation.notes

        val integrated = tree.kotlin.any { source ->
            source.path.name == "RemoteConfigApi.kt" || "app-config.json" in source.text || "RemoteNoticeHost" in source.text
        }
        if (integrated) notes += "RemoteConfigApi 같은 원격 설정 연동 코드가 이미 있어요. 다시 만들면 기존 파일과 겹칠 수 있어요"

        val project = AndroidProject(
            root = root,
            name = settings.rootProjectName ?: root.name,
            modules = modules,
            appModule = appModule,
            di = di,
            http = http,
            navigation = navigation.style,
            destinations = navigation.destinations,
            hasRemoteConfigIntegration = integrated,
            versionCatalog = catalog,
            notes = notes,
        )
        logger.info { "scanned ${project.name}: ${modules.size} modules, di=$di, http=$http, nav=${navigation.style} (${navigation.destinations.size})" }
        return project
    }

    private fun detectDi(coordinates: Set<String>, plugins: Set<String>): DiFramework = when {
        coordinates.any { it.startsWith("com.google.dagger:hilt-android") } ||
            "com.google.dagger.hilt.android" in plugins || "dagger.hilt.android.plugin" in plugins -> DiFramework.HILT
        coordinates.any { it.startsWith("io.insert-koin:") } -> DiFramework.KOIN
        coordinates.any { it == "com.google.dagger:dagger" || it == "com.google.dagger:dagger-android" } -> DiFramework.DAGGER
        else -> DiFramework.NONE
    }

    private fun detectHttp(coordinates: Set<String>, notes: MutableList<String>): HttpStack {
        val retrofit = "com.squareup.retrofit2:retrofit" in coordinates
        return when {
            retrofit && "com.squareup.retrofit2:converter-gson" in coordinates -> HttpStack.RETROFIT_GSON
            retrofit && ("com.squareup.retrofit2:converter-kotlinx-serialization" in coordinates ||
                "com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter" in coordinates) -> HttpStack.RETROFIT_KOTLINX
            retrofit && "com.squareup.retrofit2:converter-moshi" in coordinates -> HttpStack.RETROFIT_MOSHI
            retrofit -> {
                notes += "Retrofit 은 있지만 아는 컨버터(gson·kotlinx·moshi)가 없어 Gson 으로 가정했어요"
                HttpStack.RETROFIT_GSON
            }
            coordinates.any { it.startsWith("io.ktor:ktor-client-") } -> HttpStack.KTOR
            else -> {
                notes += "HTTP 라이브러리를 찾지 못했어요. 연동 코드는 Retrofit + Gson 으로 만들고 의존성 추가를 안내해요"
                HttpStack.NONE
            }
        }
    }

    private val manifestPackageAttr = Regex("""<manifest\b[^>]*\bpackage\s*=\s*"([^"]+)"""")

    /** AGP 7 이전 프로젝트는 매니페스트의 package 가 namespace 역할을 했다 */
    private fun manifestPackage(moduleDir: Path): String? {
        val manifest = moduleDir.resolve("src/main/AndroidManifest.xml")
        if (!manifest.isRegularFile()) return null
        return runCatching { manifestPackageAttr.find(manifest.readText())?.groupValues?.get(1) }.getOrNull()
    }

    /** 루트 아래 `src/main/{java,kotlin}` 의 소스 파일들. build·test 는 건너뛴다 */
    private class SourceTree(val kotlin: List<KotlinSource>, private val sourceFiles: List<Path>) {

        /**
         * 모듈의 소스 디렉터리 경로에서 패키지를 추정한다. `domain/src/main/java/com/umc/domain/model/...` → `com.umc.domain`
         *
         * 파일들의 공통 접두 패키지를 잡고, 그 안에 모듈 이름과 같은 조각이 있으면 거기서 끊는다.
         * 파일이 하나뿐이어도 `com.umc.domain.model.base` 처럼 너무 깊게 잡히지 않게 하려는 것이다.
         */
        fun inferPackage(moduleDir: Path, modulePath: String): String? {
            val packages = sourceFiles.mapNotNull { file ->
                val sourceRoot = listOf("src/main/java", "src/main/kotlin").map(moduleDir::resolve).firstOrNull { file.startsWith(it) }
                    ?: return@mapNotNull null
                sourceRoot.relativize(file).parent?.toString()?.replace(java.io.File.separatorChar, '.')
            }
            if (packages.isEmpty()) return null
            val common = packages.map { it.split('.') }.reduce { a, b -> a.zip(b).takeWhile { (x, y) -> x == y }.map { it.first } }
            val moduleName = modulePath.substringAfterLast(':').lowercase().replace("-", "").replace("_", "")
            val cut = common.indexOfFirst { it.lowercase() == moduleName }
            val segments = if (cut >= 0) common.subList(0, cut + 1) else common
            return segments.joinToString(".").ifEmpty { null }
        }

        companion object {
            private val skippedDirs = setOf("build", ".git", ".gradle", ".idea", "node_modules", "test", "androidTest")

            fun collect(root: Path): SourceTree {
                val kotlin = mutableListOf<KotlinSource>()
                val files = mutableListOf<Path>()
                Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                        if (dir != root && dir.name in skippedDirs) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        val name = file.name
                        if ((name.endsWith(".kt") || name.endsWith(".java")) && isMainSource(file)) {
                            // Path 는 Iterable<Path> 라 += 가 plus(Iterable) 로 풀린다
                            files.add(file)
                            if (name.endsWith(".kt")) {
                                runCatching { kotlin.add(KotlinSource(file, file.readText())) }
                                    .onFailure { logger.warn(it) { "소스를 읽지 못했어요: $file" } }
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult = FileVisitResult.CONTINUE
                })
                kotlin.sortBy { it.path.toString() }
                return SourceTree(kotlin, files)
            }

            private fun isMainSource(file: Path): Boolean {
                val text = file.toString().replace('\\', '/')
                return "/src/main/java/" in text || "/src/main/kotlin/" in text
            }
        }
    }
}
