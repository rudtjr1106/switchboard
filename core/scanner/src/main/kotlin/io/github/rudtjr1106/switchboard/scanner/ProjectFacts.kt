package io.github.rudtjr1106.switchboard.scanner

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/** 생성할 안내 UI 가 기댈 Compose Material 버전 */
enum class ComposeMaterial { MATERIAL3, MATERIAL2, NONE }

/**
 * 스캔 결과에서 파생되는 사실들
 *
 * [AndroidProject] 의 모양은 UI 가 이미 기대고 있어 바꾸지 않고, 생성기가 더 알아야 하는 것들을 여기서 다시 계산한다.
 * 모두 [AndroidProject] 와 디스크(매니페스트)에서 결정적으로 나오므로 스캐너와 생성기 어느 쪽에서 불러도 같다.
 */
data class ProjectFacts(
    /** 모든 모듈의 의존성을 `group:artifact` 로 푼 것. 프로젝트 간 의존성은 뺀다 */
    val coordinates: Set<String>,
    val pluginIds: Set<String>,
    val usesCompose: Boolean,
    val material: ComposeMaterial,
    /** 앱 모듈 매니페스트에 INTERNET 권한이 있는지. 매니페스트를 못 찾으면 false */
    val hasInternetPermission: Boolean,
    val manifestPath: Path?,
) {
    fun has(coordinate: String): Boolean = coordinate in coordinates

    fun hasPrefix(prefix: String): Boolean = coordinates.any { it.startsWith(prefix) }

    companion object {
        private val internetPermission = Regex("""android\.permission\.INTERNET\b""")

        fun of(project: AndroidProject): ProjectFacts {
            val coordinates = project.modules.flatMapTo(LinkedHashSet()) { coordinatesOf(it, project.versionCatalog) }
            val plugins = project.modules.flatMapTo(LinkedHashSet()) { it.plugins }
            val manifest = project.appModule?.dir?.resolve("src/main/AndroidManifest.xml")?.takeIf { it.isRegularFile() }
            val manifestText = manifest?.let { runCatching { it.readText() }.getOrNull() }
            return ProjectFacts(
                coordinates = coordinates,
                pluginIds = plugins,
                usesCompose = usesCompose(coordinates, plugins),
                material = when {
                    "androidx.compose.material3:material3" in coordinates -> ComposeMaterial.MATERIAL3
                    "androidx.compose.material:material" in coordinates -> ComposeMaterial.MATERIAL2
                    else -> ComposeMaterial.NONE
                },
                hasInternetPermission = manifestText?.let(internetPermission::containsMatchIn) ?: false,
                manifestPath = manifest,
            )
        }

        fun usesCompose(coordinates: Set<String>, plugins: Set<String>): Boolean =
            coordinates.any { it.startsWith("androidx.compose") || it.startsWith("org.jetbrains.compose") || it.endsWith(":compose-bom") } ||
                "org.jetbrains.kotlin.plugin.compose" in plugins

        /** 모듈 하나의 의존성을 카탈로그로 풀어 `group:artifact` 집합으로 */
        fun coordinatesOf(module: GradleModule, versionCatalog: Map<String, String>): Set<String> =
            module.dependencies.flatMapTo(LinkedHashSet()) { resolve(it, versionCatalog) }

        /**
         * 의존성 표기 하나를 `group:artifact` 목록으로. 번들은 여러 개가 되고, 프로젝트 의존성이나 모르는 별칭은 빈 목록
         */
        fun resolve(dependency: String, versionCatalog: Map<String, String>): List<String> = when {
            dependency.startsWith("project(") -> emptyList()
            dependency.startsWith("libs.") -> versionCatalog[dependency]?.split(',')?.map { it.trim() }.orEmpty()
            dependency.count { it == ':' } >= 1 -> {
                val parts = dependency.split(':')
                listOf(parts[0] + ":" + parts[1])
            }
            else -> emptyList()
        }
    }
}

/** 프로젝트 루트가 Gradle 프로젝트가 아닐 때처럼 스캔을 이어갈 수 없는 경우. 메시지는 그대로 사용자에게 보여준다 */
class ProjectScanException(message: String) : Exception(message)
