package io.github.rudtjr1106.switchboard.scanner

import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.rudtjr1106.switchboard.scanner.templates.AppConfigResponseFile
import io.github.rudtjr1106.switchboard.scanner.templates.GetRemoteNoticesUseCaseFile
import io.github.rudtjr1106.switchboard.scanner.templates.HiltRemoteConfigBindModuleFile
import io.github.rudtjr1106.switchboard.scanner.templates.DaggerModules
import io.github.rudtjr1106.switchboard.scanner.templates.HiltRemoteConfigModuleFile
import io.github.rudtjr1106.switchboard.scanner.templates.KoinRemoteConfigModuleFile
import io.github.rudtjr1106.switchboard.scanner.templates.KtorEngine
import io.github.rudtjr1106.switchboard.scanner.templates.MaterialFlavor
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteBlockingScreenFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteConfigApiFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteConfigContainerFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteConfigRemoteDataSourceFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteConfigRemoteDataSourceImplFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteConfigRepositoryFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteConfigRepositoryImplFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteNoticeFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteNoticeHostFile
import io.github.rudtjr1106.switchboard.scanner.templates.RemoteNoticeViewModelFile
import io.github.rudtjr1106.switchboard.scanner.templates.TemplateContext
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.streams.asSequence

private val logger = KotlinLogging.logger {}

/**
 * 스캔 결과에 맞춰 결정적인 Kotlin 템플릿으로 연동 코드를 만든다
 *
 * 참고 프로젝트(domain / data / app / presentation 로 나뉜 Hilt + Retrofit + Gson + Compose 앱)의 연동을 기준으로,
 * DI(Hilt·Koin·없음)와 HTTP(Retrofit gson·kotlinx·moshi, Ktor) 조합마다 같은 의미의 코드를 낸다.
 * 기존 파일은 절대 고치지 않고, 자동으로 못 하는 일은 [IntegrationPlan.manualSteps] 로 알려준다.
 */
class TemplateIntegrationGenerator : IntegrationGenerator {

    override fun plan(project: AndroidProject, target: IntegrationTarget): IntegrationPlan {
        val notes = mutableListOf<String>()
        val manualSteps = mutableListOf<String>()
        val facts = ProjectFacts.of(project)
        val layout = IntegrationLayout.resolve(project, facts, notes)

        val http = if (project.http == HttpStack.NONE) HttpStack.RETROFIT_GSON else project.http
        val material = when (facts.material) {
            ComposeMaterial.MATERIAL2 -> MaterialFlavor.MATERIAL2
            else -> MaterialFlavor.MATERIAL3
        }
        val ctx = TemplateContext(
            repoFullName = target.repoFullName,
            baseUrl = target.pagesBaseUrl.trim().let { if (it.endsWith("/")) it else "$it/" },
            configPath = target.configPath.trim().trimStart('/'),
            di = project.di,
            http = http,
            modelPackage = layout.domain.subPackage("model.remoteconfig"),
            repositoryPackage = layout.domain.subPackage("repository.remoteconfig"),
            useCasePackage = layout.domain.subPackage("usecase.remoteconfig"),
            dataPackage = layout.data.subPackage("remoteconfig"),
            diPackage = layout.di.subPackage("di"),
            uiPackage = layout.ui.subPackage("remotenotice"),
            material = material,
            koinViewModelImport = if (facts.has("io.insert-koin:koin-compose-viewmodel")) "org.koin.compose.viewmodel.koinViewModel" else "org.koin.androidx.compose.koinViewModel",
            koinViewModelDslImport = if (facts.has("io.insert-koin:koin-android")) "org.koin.androidx.viewmodel.dsl.viewModel" else "org.koin.core.module.dsl.viewModel",
            ktorEngine = ktorEngine(facts),
            kotlinxConverterImport = if (facts.has("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter")) {
                "com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory"
            } else {
                "retrofit2.converter.kotlinx.serialization.asConverterFactory"
            },
            // DI 없이 계층이 나뉘면 컨테이너는 app 에, Host 는 presentation 에 있어 Host 가 컨테이너를 볼 수 없다
            // Dagger 는 ViewModel 을 만들 팩토리가 프로젝트마다 달라 기본값을 줄 수 없다
            hostHasDefaultViewModel = project.di == DiFramework.HILT || project.di == DiFramework.KOIN ||
                (project.di == DiFramework.NONE && !layout.layered),
            exampleScreen = project.destinationNames.firstOrNull { it != "Splash" } ?: target.screens.firstOrNull { !it.isAll }?.id ?: "Home",
        )

        val files = mutableListOf<GeneratedFile>()
        fun add(placement: Placement, pkg: String, name: String, content: String) {
            val relative = placement.sourceRoot.resolve(pkg.replace('.', '/')).resolve(name)
            val existing = project.root.resolve(relative).takeIf { it.isRegularFile() }?.let { runCatching { it.readText() }.getOrNull() }
            files += GeneratedFile(
                path = relative,
                content = content,
                action = if (existing != null) FileAction.MODIFY else FileAction.CREATE,
                original = existing,
            )
        }

        add(layout.domain, ctx.modelPackage, "RemoteNotice.kt", RemoteNoticeFile.render(ctx))
        add(layout.domain, ctx.repositoryPackage, "RemoteConfigRepository.kt", RemoteConfigRepositoryFile.render(ctx))
        add(layout.domain, ctx.useCasePackage, "GetRemoteNoticesUseCase.kt", GetRemoteNoticesUseCaseFile.render(ctx))
        add(layout.data, ctx.dataPackage, "RemoteConfigApi.kt", RemoteConfigApiFile.render(ctx))
        add(layout.data, ctx.dataPackage, "AppConfigResponse.kt", AppConfigResponseFile.render(ctx))
        add(layout.data, ctx.dataPackage, "RemoteConfigRemoteDataSource.kt", RemoteConfigRemoteDataSourceFile.render(ctx))
        add(layout.data, ctx.dataPackage, "RemoteConfigRemoteDataSourceImpl.kt", RemoteConfigRemoteDataSourceImplFile.render(ctx))
        add(layout.data, ctx.dataPackage, "RemoteConfigRepositoryImpl.kt", RemoteConfigRepositoryImplFile.render(ctx))
        when (project.di) {
            DiFramework.HILT -> {
                add(layout.di, ctx.diPackage, "RemoteConfigModule.kt", HiltRemoteConfigModuleFile.render(ctx))
                add(layout.di, ctx.diPackage, "RemoteConfigBindModule.kt", HiltRemoteConfigBindModuleFile.render(ctx))
                notes += "Hilt 를 써서 자체 모듈 두 개(RemoteConfigModule 의 @Provides, RemoteConfigBindModule 의 @Binds)로 묶었어요. 기존 DI 모듈은 고치지 않아도 돼요"
            }
            DiFramework.KOIN -> {
                add(layout.di, ctx.diPackage, "RemoteConfigModule.kt", KoinRemoteConfigModuleFile.render(ctx))
                notes += "Koin 모듈 remoteConfigModule 하나에 클라이언트부터 ViewModel 까지 담았어요"
            }
            DiFramework.DAGGER -> {
                add(layout.di, ctx.diPackage, "RemoteConfigModule.kt", DaggerModules.module(ctx))
                add(layout.di, ctx.diPackage, "RemoteConfigBindModule.kt", DaggerModules.bindModule(ctx))
                notes += "Hilt 없이 Dagger 를 써서 @InstallIn 없는 모듈 두 개를 만들었어요. 앱의 @Component 에 직접 넣어야 해요"
            }
            DiFramework.NONE -> {
                add(layout.di, ctx.diPackage, "RemoteConfigContainer.kt", RemoteConfigContainerFile.render(ctx))
                notes += "DI 프레임워크를 찾지 못해 RemoteConfigContainer 가 싱글턴을 직접 들고 ViewModel 팩토리를 만들어요"
            }
        }
        // ViewModel 은 Compose 와 상관없는 AndroidX ViewModel 이라 안내 UI 를 직접 만들 때도 쓸 수 있다
        add(layout.ui, ctx.uiPackage, "RemoteNoticeViewModel.kt", RemoteNoticeViewModelFile.render(ctx))
        if (facts.usesCompose) {
            add(layout.ui, ctx.uiPackage, "RemoteNoticeHost.kt", RemoteNoticeHostFile.render(ctx))
            add(layout.ui, ctx.uiPackage, "RemoteBlockingScreen.kt", RemoteBlockingScreenFile.render(ctx))
            notes += when (facts.material) {
                ComposeMaterial.MATERIAL2 -> "안내 UI 는 프로젝트 디자인 시스템 대신 Material2 기본 컴포넌트(AlertDialog·Surface)로 만들었어요. 원하는 모양으로 바꿔도 돼요"
                ComposeMaterial.MATERIAL3 -> "안내 UI 는 프로젝트 디자인 시스템 대신 Material3 기본 컴포넌트(AlertDialog·Surface)로 만들었어요. 원하는 모양으로 바꿔도 돼요"
                ComposeMaterial.NONE -> "Compose Material 의존성을 찾지 못해 Material3 로 가정했어요. ${layout.ui.module.path} 에 androidx.compose.material3:material3 가 필요해요"
            }
        }

        describeHttp(project, http, notes)
        manualSteps += hostStep(project, ctx, facts)
        if (!facts.hasInternetPermission) {
            val manifest = facts.manifestPath?.let { project.root.relativize(it) } ?: "app/src/main/AndroidManifest.xml"
            manualSteps += "$manifest 의 <manifest> 안에 인터넷 권한을 추가하세요:\n    <uses-permission android:name=\"android.permission.INTERNET\" />"
        }
        if (project.di == DiFramework.DAGGER) {
            manualSteps += "앱의 Dagger 컴포넌트에 모듈을 넣으세요. Context 를 제공하는 모듈(@BindsInstance 등)도 있어야 해요:\n" +
                "    @Component(modules = [/* 기존 모듈 */, RemoteConfigModule::class, RemoteConfigBindModule::class])\n" +
                "RemoteNoticeHost 의 viewModel 에는 컴포넌트에서 주입받은 RemoteNoticeViewModel 을 넘기세요 (기존 ViewModelProvider.Factory 에 등록하면 화면 회전에도 유지돼요)"
        }
        if (project.di == DiFramework.KOIN) {
            manualSteps += "Application 의 startKoin 에 모듈을 등록하세요 (androidContext 가 캐시 폴더를 잡는 데 필요해요):\n" +
                "    startKoin {\n        androidContext(this@App)\n        modules(/* 기존 모듈들, */ remoteConfigModule)\n    }"
        }
        manualSteps += dependencySteps(project, facts, layout, http)

        val modified = files.count { it.action == FileAction.MODIFY }
        if (project.hasRemoteConfigIntegration) {
            notes += "이 프로젝트에는 이미 원격 설정 연동 코드가 있어요. 만든 파일이 기존 것과 겹치면 덮어쓰기(MODIFY)로 표시했으니 diff 를 보고 필요한 것만 적용하세요"
        }
        if (modified > 0) notes += "이미 있는 파일 ${modified}개는 덮어쓰기로 표시했어요. 적용 전에 diff 를 확인하세요"
        screenMismatch(project, target)?.let { notes += it }
        if (!facts.has("com.android.tools:desugar_jdk_libs")) {
            notes += "RemoteNotice 가 java.time.LocalDate 를 써요. minSdk 가 26 미만이면 core library desugaring 을 켜야 해요"
        }

        logger.info { "planned ${files.size} files for ${project.name} (layered=${layout.layered}, di=${project.di}, http=$http)" }
        return IntegrationPlan(files = files, notes = notes, manualSteps = manualSteps)
    }

    private fun ktorEngine(facts: ProjectFacts): KtorEngine? = when {
        facts.has("io.ktor:ktor-client-okhttp") -> KtorEngine("OkHttp", "io.ktor.client.engine.okhttp.OkHttp")
        facts.has("io.ktor:ktor-client-cio") -> KtorEngine("CIO", "io.ktor.client.engine.cio.CIO")
        facts.has("io.ktor:ktor-client-android") -> KtorEngine("Android", "io.ktor.client.engine.android.Android")
        else -> null
    }

    private fun describeHttp(project: AndroidProject, http: HttpStack, notes: MutableList<String>) {
        notes += when (project.http) {
            HttpStack.RETROFIT_GSON -> "설정 파일은 Retrofit + Gson 으로 읽어요. 전용 OkHttp 클라이언트에 1MB 디스크 캐시와 10초 타임아웃을 붙였어요"
            HttpStack.RETROFIT_KOTLINX -> "설정 파일은 Retrofit + kotlinx.serialization 으로 읽어요. 전용 OkHttp 클라이언트에 1MB 디스크 캐시와 10초 타임아웃을 붙였어요"
            HttpStack.RETROFIT_MOSHI -> "설정 파일은 Retrofit + Moshi(codegen) 로 읽어요. 전용 OkHttp 클라이언트에 1MB 디스크 캐시와 10초 타임아웃을 붙였어요"
            HttpStack.KTOR -> "설정 파일은 Ktor 클라이언트로 읽어요. HttpCache(디스크)와 10초 타임아웃을 붙였고, 오프라인이면 only-if-cached 로 캐시를 다시 읽어요"
            HttpStack.NONE -> "HTTP 라이브러리를 찾지 못해 Retrofit + Gson 코드로 만들었어요 ($http). 의존성을 추가해야 컴파일돼요"
        }
    }

    /** Activity 에 Host 를 붙이는 방법. 자동으로 고치지 않는 유일한 코드 변경이라 예시를 그대로 붙일 수 있게 쓴다 */
    private fun hostStep(project: AndroidProject, ctx: TemplateContext, facts: ProjectFacts): String {
        val activity = findMainActivity(project.root)?.let { project.root.relativize(it).toString() } ?: "앱의 메인 Activity"
        if (project.navigation == NavigationStyle.XML_GRAPH) {
            return "XML 내비게이션이라 화면 이름은 목적지의 android:id 이름이에요. $activity 에서 현재 화면 이름을 이렇게 얻어 안내를 고르세요:\n" +
                "    navController.addOnDestinationChangedListener { _, destination, _ ->\n" +
                "        val screen = resources.getResourceEntryName(destination.id)\n" +
                "        // notices.filter { it.targets(screen) && it.isShowable(LocalDate.now()) }\n" +
                "    }" + if (facts.usesCompose) "\nCompose 화면이 있으면 RemoteNoticeHost(currentRoute = screen) 에 그 값을 넘기면 돼요" else ""
        }
        if (!facts.usesCompose) {
            return "Compose 를 쓰지 않아 안내 UI(RemoteNoticeHost)는 만들지 않았어요. $activity 에서 RemoteNoticeViewModel 을 만들어 notices 를 관찰하고, " +
                "현재 화면 이름에 맞는 안내(notice.targets(screen) && notice.isShowable(LocalDate.now()))를 다이얼로그로 띄우세요. " +
                "BLOCKING 은 닫을 수 없게 전체 화면으로, INFO 는 실행 중 한 번만(viewModel.dismiss) 띄우면 돼요"
        }
        val viewModelArg = if (ctx.hostHasDefaultViewModel) "" else {
            ",\n            viewModel = viewModel(factory = RemoteConfigContainer.viewModelFactory(LocalContext.current))"
        }
        val extraImport = if (ctx.hostHasDefaultViewModel) "" else "\n    import androidx.lifecycle.viewmodel.compose.viewModel\n    import ${ctx.diPackage}.RemoteConfigContainer"
        return """
$activity 의 setContent 안에서 NavHost(또는 Scaffold)를 Box 로 감싸고 그 맨 뒤에 RemoteNoticeHost 를 두세요.
BLOCKING 안내가 하단바까지 덮어야 해서 Scaffold 바깥이어야 해요:
    import androidx.navigation.compose.currentBackStackEntryAsState
    import ${ctx.uiPackage}.RemoteNoticeHost$extraImport

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(/* … */) { innerPadding ->
            NavHost(navController = navController, /* … */)
        }
        RemoteNoticeHost(currentRoute = backStackEntry?.destination?.route$viewModelArg)
    }
""".trim()
    }

    /** build 폴더는 프로젝트 루트 기준으로만 거른다. 루트 자체가 어떤 build 폴더 아래 있어도 상관없도록 */
    private fun findMainActivity(root: Path): Path? = runCatching {
        Files.walk(root).use { stream ->
            stream.asSequence()
                .filter { it.name == "MainActivity.kt" }
                .filter { file -> root.relativize(file).none { it.name == "build" } }
                .sortedBy { it.toString() }
                .firstOrNull()
        }
    }.getOrNull()

    /** 모듈에 없는 의존성. 전이 의존성은 못 보므로 확실한 것만 말한다 */
    private fun dependencySteps(project: AndroidProject, facts: ProjectFacts, layout: IntegrationLayout, http: HttpStack): List<String> {
        val steps = mutableListOf<String>()
        fun coordinatesOf(module: GradleModule) = ProjectFacts.coordinatesOf(module, project.versionCatalog)
        fun missing(module: GradleModule, vararg any: String): Boolean = coordinatesOf(module).none { it in any }

        if (project.http == HttpStack.NONE) {
            val modules = listOf(layout.data.module, layout.di.module).distinctBy { it.path }.joinToString(", ") { it.path }
            steps += "$modules 에 Retrofit + Gson 의존성을 추가하세요:\n" +
                "    implementation(\"com.squareup.retrofit2:retrofit:3.0.0\")\n" +
                "    implementation(\"com.squareup.retrofit2:converter-gson:3.0.0\")\n" +
                "    implementation(\"com.squareup.okhttp3:okhttp:4.12.0\")"
        }
        if (http == HttpStack.KTOR) {
            if (!facts.has("io.ktor:ktor-client-content-negotiation") || !facts.has("io.ktor:ktor-serialization-kotlinx-json")) {
                steps += "${layout.di.module.path} 에 Ktor JSON 의존성을 추가하세요: io.ktor:ktor-client-content-negotiation, io.ktor:ktor-serialization-kotlinx-json (프로젝트의 ktor 버전으로)"
            }
            if (facts.hasPrefix("io.ktor:ktor-client-") && ktorEngine(facts) == null) {
                steps += "Ktor 엔진(okhttp·cio·android)을 찾지 못해 HttpClient() 로 두었어요. 엔진 의존성이 없다면 io.ktor:ktor-client-okhttp 를 추가하세요"
            }
        }
        if (project.di == DiFramework.HILT) {
            val inject = arrayOf("com.google.dagger:hilt-core", "com.google.dagger:hilt-android", "javax.inject:javax.inject", "com.google.dagger:dagger")
            listOf(layout.domain.module, layout.data.module).distinctBy { it.path }.forEach { module ->
                if (missing(module, *inject)) {
                    steps += "${module.path} 에 @Inject 를 쓸 수 있는 의존성이 없어요. implementation(\"javax.inject:javax.inject:1\") 또는 hilt-core 를 추가하세요"
                }
            }
            if (facts.usesCompose && missing(layout.ui.module, "androidx.hilt:hilt-navigation-compose")) {
                steps += "${layout.ui.module.path} 에 hiltViewModel() 용 의존성을 추가하세요: implementation(\"androidx.hilt:hilt-navigation-compose:1.2.0\")"
            }
        }
        if (project.di == DiFramework.KOIN && facts.usesCompose &&
            missing(layout.ui.module, "io.insert-koin:koin-androidx-compose", "io.insert-koin:koin-compose-viewmodel")
        ) {
            steps += "${layout.ui.module.path} 에 koinViewModel() 용 의존성을 추가하세요: implementation(\"io.insert-koin:koin-androidx-compose:<koin 버전>\")"
        }
        if (project.di == DiFramework.NONE && facts.usesCompose && layout.ui.module.path == layout.di.module.path &&
            missing(layout.ui.module, "androidx.lifecycle:lifecycle-viewmodel-compose", "androidx.navigation:navigation-compose", "androidx.hilt:hilt-navigation-compose")
        ) {
            steps += "${layout.ui.module.path} 에 viewModel(factory = …) 용 의존성이 없다면 추가하세요: implementation(\"androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7\")"
        }
        return steps
    }

    private fun screenMismatch(project: AndroidProject, target: IntegrationTarget): String? {
        val known = project.destinationNames.toSet()
        if (known.isEmpty()) return null
        val unknown = target.screens.filter { !it.isAll && it.id !in known }.map { it.id }
        if (unknown.isEmpty()) return null
        return "설정 저장소의 화면 중 이 프로젝트 목적지에 없는 이름이 있어요: ${unknown.joinToString(", ")}. screen 값은 route 의 경로 이름과 같아야 안내가 떠요"
    }
}

/** 코드를 넣을 모듈 하나. [sourceRoot] 는 프로젝트 루트 기준 상대 경로 (`app/src/main/java`) */
internal class Placement(val module: GradleModule, val sourceRoot: Path, val basePackage: String, val flat: Boolean) {
    /** 계층별 하위 패키지. 단일 모듈이거나 namespace 를 모르는 모듈이면 모두 [basePackage] 하나에 둔다 */
    fun subPackage(suffix: String): String = if (flat) basePackage else "$basePackage.$suffix"
}

/**
 * 어느 모듈·패키지에 무엇을 넣을지
 *
 * domain 과 data 모듈이 모두 있으면 참고 프로젝트처럼 계층별로 나누고, 아니면 앱 모듈의 `<namespace>.remoteconfig` 한 곳에 모은다.
 * 안내 UI 는 presentation(또는 ui) 모듈이 Compose 를 쓰면 거기, 아니면 앱 모듈이다.
 */
internal class IntegrationLayout(
    val layered: Boolean,
    val domain: Placement,
    val data: Placement,
    val di: Placement,
    val ui: Placement,
) {
    companion object {
        fun resolve(project: AndroidProject, facts: ProjectFacts, notes: MutableList<String>): IntegrationLayout {
            val app = project.appModule ?: project.modules.firstOrNull()
                ?: throw ProjectScanException("코드를 넣을 모듈이 없어요. settings.gradle 의 include 를 확인하세요")
            val appNamespace = app.namespace
                ?: throw ProjectScanException("${app.path} 모듈의 namespace 를 찾지 못했어요. build.gradle 의 android.namespace 를 확인하세요")
            if (project.appModule == null) notes += "앱 모듈이 없어 첫 모듈(${app.path})에 넣어요"

            fun find(vararg names: String): GradleModule? = project.modules
                .filter { it.path != app.path && it.path.substringAfterLast(':').lowercase().replace("-", "") in names }
                .minByOrNull { it.path.length }

            fun usesCompose(module: GradleModule) = ProjectFacts.usesCompose(ProjectFacts.coordinatesOf(module, project.versionCatalog), module.plugins.toSet())

            val domain = find("domain", "coredomain")
            val data = find("data", "coredata")
            val presentation = find("presentation", "ui", "feature")

            if (domain == null || data == null) {
                val base = "$appNamespace.remoteconfig"
                val placement = Placement(app, sourceRoot(project.root, app), base, flat = true)
                notes += "domain·data 모듈이 따로 없어 모든 파일을 ${app.path} 의 $base 패키지에 모아요"
                return IntegrationLayout(false, placement, placement, placement, placement)
            }

            val ui = when {
                presentation != null && (usesCompose(presentation) || !usesCompose(app)) -> presentation
                else -> app
            }
            fun placement(module: GradleModule): Placement {
                val namespace = module.namespace
                return if (namespace != null) {
                    Placement(module, sourceRoot(project.root, module), namespace, flat = false)
                } else {
                    notes += "${module.path} 모듈의 namespace 를 몰라 $appNamespace.remoteconfig 패키지를 써요"
                    Placement(module, sourceRoot(project.root, module), "$appNamespace.remoteconfig", flat = true)
                }
            }
            notes += "참고 프로젝트처럼 계층별로 나눠요: 모델·저장소 인터페이스·유스케이스 → ${domain.path}, API·응답·저장소 구현 → ${data.path}, " +
                "DI → ${app.path}, 안내 UI → ${ui.path}"
            return IntegrationLayout(
                layered = true,
                domain = placement(domain),
                data = placement(data),
                di = placement(app),
                ui = placement(ui),
            )
        }

        /** 모듈이 `src/main/kotlin` 을 쓰면 그쪽, 아니면 `src/main/java` */
        private fun sourceRoot(root: Path, module: GradleModule): Path {
            val relative = runCatching { root.relativize(module.dir) }.getOrDefault(module.dir)
            val kotlin = module.dir.resolve("src/main/kotlin")
            val java = module.dir.resolve("src/main/java")
            val set = if (kotlin.isDirectory() && !java.isDirectory()) "src/main/kotlin" else "src/main/java"
            return if (relative.toString().isEmpty()) Path.of(set) else relative.resolve(set)
        }
    }
}
