package io.github.rudtjr1106.switchboard.scanner.templates

import io.github.rudtjr1106.switchboard.config.ValueSpec
import io.github.rudtjr1106.switchboard.scanner.DiFramework
import io.github.rudtjr1106.switchboard.scanner.HttpStack

/** 안내 UI 가 기댈 Material 패키지. M2 와 M3 는 이름이 같고 패키지·타이포그래피만 다르다 */
internal enum class MaterialFlavor(val pkg: String, val titleStyle: String, val bodyStyle: String) {
    MATERIAL3("androidx.compose.material3", "headlineSmall", "bodyMedium"),
    MATERIAL2("androidx.compose.material", "h6", "body1"),
}

/** Ktor 엔진. 없으면 `HttpClient()` 로 두고 클래스패스의 엔진을 쓴다 */
internal data class KtorEngine(val name: String, val import: String)

/**
 * 템플릿에 들어가는 값 전부. 생성기가 프로젝트를 보고 한 번 채우면 템플릿은 이것만 본다
 *
 * 패키지는 계층별로 따로 둔다. 단일 모듈이면 모두 같은 값이고, 그때 같은 패키지 import 는 [source] 가 걸러 낸다.
 */
internal data class TemplateContext(
    val repoFullName: String,
    /** `https://owner.github.io/` — 끝에 `/` 가 있다 */
    val baseUrl: String,
    /** `repo/app-config.json` — 앞에 `/` 가 없다 */
    val configPath: String,
    val di: DiFramework,
    val http: HttpStack,
    val modelPackage: String,
    val repositoryPackage: String,
    val useCasePackage: String,
    val dataPackage: String,
    val diPackage: String,
    val uiPackage: String,
    val material: MaterialFlavor,
    /** `org.koin.androidx.compose.koinViewModel` 또는 `org.koin.compose.viewmodel.koinViewModel` */
    val koinViewModelImport: String,
    /** Koin 모듈 DSL 의 `viewModel { }` import */
    val koinViewModelDslImport: String,
    val ktorEngine: KtorEngine?,
    /** kotlinx 컨버터의 `asConverterFactory` import (공식 또는 jakewharton) */
    val kotlinxConverterImport: String,
    /** Host 의 viewModel 파라미터에 기본값을 줄 수 있는지. DI 없이 계층 분리된 프로젝트는 컨테이너가 다른 모듈에 있어 못 준다 */
    val hostHasDefaultViewModel: Boolean,
    /** KDoc 예시에 쓸 화면 이름 */
    val exampleScreen: String,
    /** 스키마의 자유 값. 비어 있으면 값 코드를 만들지 않는다 */
    val values: List<ValueSpec> = emptyList(),
) {
    val isHilt: Boolean get() = di == DiFramework.HILT

    /** 생성자 주입을 쓰는 DI (Hilt, Dagger) */
    val usesInject: Boolean get() = di == DiFramework.HILT || di == DiFramework.DAGGER

    /** Hilt·Dagger 면 ` @Inject constructor`, 아니면 빈 문자열. 클래스 이름 바로 뒤에 붙인다 */
    val injectConstructor: String get() = if (usesInject) " @Inject constructor" else ""

    val injectImports: List<String> get() = if (usesInject) listOf("javax.inject.Inject") else emptyList()

    val isRetrofit: Boolean get() = http != HttpStack.KTOR

    /** 파일 하나를 조립한다. import 는 정렬하고 같은 패키지 것은 뺀다 */
    fun source(pkg: String, imports: Collection<String>, body: String): String {
        val lines = imports.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.substringBeforeLast('.') != pkg }
            .distinct()
            .sorted()
            .map { "import $it" }
            .toList()
        return buildString {
            append("package ").append(pkg).append('\n')
            if (lines.isNotEmpty()) {
                append('\n')
                lines.forEach { append(it).append('\n') }
            }
            append('\n')
            append(body.trim())
            append('\n')
        }
    }
}
