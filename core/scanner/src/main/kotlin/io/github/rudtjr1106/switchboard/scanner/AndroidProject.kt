package io.github.rudtjr1106.switchboard.scanner

import java.nio.file.Path

enum class DiFramework { HILT, KOIN, NONE }

enum class HttpStack { RETROFIT_GSON, RETROFIT_KOTLINX, RETROFIT_MOSHI, KTOR, NONE }

enum class NavigationStyle { NAVIGATION_COMPOSE_TYPESAFE, NAVIGATION3, STRING_ROUTES, UNKNOWN }

data class GradleModule(
    /** `:presentation:home` */
    val path: String,
    val dir: Path,
    val namespace: String?,
    val isApplication: Boolean,
    val plugins: List<String>,
    /** 버전 카탈로그 별칭 또는 좌표 (예: `libs.retrofit`, `com.squareup.retrofit2:retrofit`) */
    val dependencies: List<String>,
)

/** 내비게이션 목적지 하나. `MainDestination.EmailSignUp` 이면 name = "EmailSignUp" */
data class Destination(
    val name: String,
    val file: Path,
    val hasArguments: Boolean,
    val comment: String? = null,
)

data class AndroidProject(
    val root: Path,
    val name: String,
    val modules: List<GradleModule>,
    val appModule: GradleModule?,
    val di: DiFramework,
    val http: HttpStack,
    val navigation: NavigationStyle,
    val destinations: List<Destination>,
    /** RemoteConfigApi 같은 연동 코드가 이미 있는지 */
    val hasRemoteConfigIntegration: Boolean,
    val versionCatalog: Map<String, String>,
    /** 스캔 중 알아둔 것 (감지 못한 항목 등). 사용자에게 그대로 보여준다 */
    val notes: List<String> = emptyList(),
) {
    val destinationNames: List<String> get() = destinations.map { it.name }
}

interface AndroidProjectScanner {
    suspend fun scan(root: Path): AndroidProject
}
