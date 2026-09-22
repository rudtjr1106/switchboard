package io.github.rudtjr1106.switchboard.scanner

import io.github.rudtjr1106.switchboard.config.ScreenInfo
import io.github.rudtjr1106.switchboard.scanner.Fixtures.writeFile
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory
import kotlin.io.path.relativeTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TemplateIntegrationGeneratorTest {

    private val scanner = GradleAndroidProjectScanner()
    private val generator = TemplateIntegrationGenerator()

    private val target = IntegrationTarget(
        pagesBaseUrl = "https://umc-product.github.io/",
        configPath = "umc-product-android-config/app-config.json",
        repoFullName = "UMC-PRODUCT/umc-product-android-config",
        screens = listOf(ScreenInfo("ALL"), ScreenInfo("Home"), ScreenInfo("Act")),
    )

    private fun IntegrationPlan.content(fileName: String): String =
        files.single { it.path.fileName.toString() == fileName }.content

    private fun IntegrationPlan.paths(): List<String> = files.map { it.path.joinToString("/") }

    private fun assertWellFormed(plan: IntegrationPlan) {
        for (file in plan.files) {
            val where = file.path.toString()
            assertTrue(file.content.startsWith("package "), "$where: package 선언으로 시작해야 해요")
            assertTrue(file.content.endsWith("\n"), "$where: 마지막 줄바꿈이 없어요")
            assertTrue(Fixtures.bracesBalanced(file.content), "$where: 괄호 짝이 안 맞아요")
            assertFalse("{{" in file.content || "$$" in file.content || "\${" in file.content, "$where: 템플릿 자리표시자가 남았어요")
            assertTrue(target.repoFullName in file.content, "$where: KDoc 에 저장소 이름이 없어요")
            assertFalse(Regex("^import [^\\n]*\\.\\.").containsMatchIn(file.content), "$where: 빈 패키지 import 가 있어요")
        }
    }

    @Test
    fun `umc-like plan mirrors the layered reference layout`() = runTest {
        val project = scanner.scan(Fixtures.project("umc-like"))
        val plan = generator.plan(project, target)
        val paths = plan.paths()

        val expected = listOf(
            "domain/src/main/java/com/umc/domain/model/remoteconfig/RemoteNotice.kt",
            "domain/src/main/java/com/umc/domain/repository/remoteconfig/RemoteConfigRepository.kt",
            "domain/src/main/java/com/umc/domain/usecase/remoteconfig/GetRemoteNoticesUseCase.kt",
            "data/src/main/java/com/umc/data/remoteconfig/RemoteConfigApi.kt",
            "data/src/main/java/com/umc/data/remoteconfig/AppConfigResponse.kt",
            "data/src/main/java/com/umc/data/remoteconfig/RemoteConfigRemoteDataSource.kt",
            "data/src/main/java/com/umc/data/remoteconfig/RemoteConfigRemoteDataSourceImpl.kt",
            "data/src/main/java/com/umc/data/remoteconfig/RemoteConfigRepositoryImpl.kt",
            "app/src/main/java/com/umc/product/di/RemoteConfigModule.kt",
            "app/src/main/java/com/umc/product/di/RemoteConfigBindModule.kt",
            "presentation/src/main/java/com/umc/presentation/remotenotice/RemoteNoticeViewModel.kt",
            "presentation/src/main/java/com/umc/presentation/remotenotice/RemoteNoticeHost.kt",
            "presentation/src/main/java/com/umc/presentation/remotenotice/RemoteBlockingScreen.kt",
        )
        assertEquals(expected, paths)
        assertTrue(plan.files.all { it.action == FileAction.CREATE })
        assertWellFormed(plan)

        val api = plan.content("RemoteConfigApi.kt")
        assertTrue("const val BASE_URL = \"https://umc-product.github.io/\"" in api)
        assertTrue("const val APP_CONFIG_PATH = \"umc-product-android-config/app-config.json\"" in api)
        assertTrue("interface RemoteConfigApi" in api)
        assertTrue("@Header(\"Cache-Control\") cacheControl: String?" in api)

        val module = plan.content("RemoteConfigModule.kt")
        assertTrue("@InstallIn(SingletonComponent::class)" in module)
        assertTrue("GsonConverterFactory.create()" in module)
        assertTrue("Cache(File(context.cacheDir, CACHE_DIR), CACHE_SIZE_BYTES)" in module)
        assertTrue("annotation class RemoteConfigOkHttpClient" in module)
        assertTrue("import com.umc.data.remoteconfig.RemoteConfigApi" in module)

        val bind = plan.content("RemoteConfigBindModule.kt")
        assertTrue("@Binds" in bind && "abstract class RemoteConfigBindModule" in bind)

        val response = plan.content("AppConfigResponse.kt")
        assertTrue("@SerializedName(\"notices\")" in response)
        assertTrue("if (version != SUPPORTED_VERSION) return emptyList()" in response)

        val model = plan.content("RemoteNotice.kt")
        assertTrue("package com.umc.domain.model.remoteconfig" in model)
        assertTrue("fun isShowable(today: LocalDate): Boolean" in model)
        assertTrue("entries.firstOrNull { it.name == value } ?: UNKNOWN" in model)

        val useCase = plan.content("GetRemoteNoticesUseCase.kt")
        assertTrue("class GetRemoteNoticesUseCase @Inject constructor(" in useCase)
        assertTrue("import com.umc.domain.repository.remoteconfig.RemoteConfigRepository" in useCase)

        val viewModel = plan.content("RemoteNoticeViewModel.kt")
        assertTrue("@HiltViewModel" in viewModel)
        assertTrue("get() = \"\$screen|\$title|\$body\"" in viewModel)
        assertTrue("substringBefore('/').substringBefore('?').substringAfterLast('.')" in viewModel)

        val host = plan.content("RemoteNoticeHost.kt")
        assertTrue("viewModel: RemoteNoticeViewModel = hiltViewModel()," in host)
        assertTrue("LifecycleEventEffect(Lifecycle.Event.ON_START)" in host)
        assertTrue("import androidx.compose.material3.AlertDialog" in host)
        assertTrue("candidates.firstOrNull { it.template == RemoteNoticeTemplate.BLOCKING }" in host)

        assertTrue(plan.manualSteps.any { "RemoteNoticeHost" in it && "currentBackStackEntryAsState" in it })
        assertFalse(plan.manualSteps.any { "INTERNET" in it }, plan.manualSteps.toString())
        assertFalse(plan.manualSteps.any { "hilt-navigation-compose" in it }, plan.manualSteps.toString())
        assertFalse(plan.manualSteps.any { "javax.inject" in it }, plan.manualSteps.toString())
        assertTrue(plan.notes.any { ":domain" in it && ":data" in it && ":presentation" in it })
        assertTrue(plan.notes.any { "Hilt" in it })
        assertFalse(plan.notes.any { "LocalDate" in it }, "desugaring 이 켜져 있으면 LocalDate 안내를 하지 않아요")
    }

    @Test
    fun `single koin ktor plan lives under the app namespace`() = runTest {
        val project = scanner.scan(Fixtures.project("single-koin-ktor"))
        val plan = generator.plan(project, target)
        val paths = plan.paths()

        assertTrue(paths.all { it.startsWith("app/src/main/java/com/example/demo/remoteconfig/") }, paths.toString())
        assertEquals(12, paths.size, paths.toString())
        assertTrue("app/src/main/java/com/example/demo/remoteconfig/RemoteConfigModule.kt" in paths)
        assertTrue("app/src/main/java/com/example/demo/remoteconfig/RemoteNoticeHost.kt" in paths)
        assertWellFormed(plan)

        val module = plan.content("RemoteConfigModule.kt")
        assertTrue("val remoteConfigModule = module {" in module)
        assertTrue("viewModel { RemoteNoticeViewModel(get()) }" in module)
        assertTrue("HttpClient(OkHttp) {" in module)
        assertTrue("install(HttpCache)" in module)
        assertTrue("import org.koin.androidx.viewmodel.dsl.viewModel" in module)
        assertFalse("import com.example.demo.remoteconfig." in module, "같은 패키지 import 는 빼야 해요")

        val api = plan.content("RemoteConfigApi.kt")
        assertTrue("class RemoteConfigApi(" in api)
        assertTrue("client.get(BASE_URL + APP_CONFIG_PATH)" in api)

        val response = plan.content("AppConfigResponse.kt")
        assertTrue("@Serializable" in response)
        assertTrue("@SerialName(\"screen\") val screen: String? = null," in response)

        val host = plan.content("RemoteNoticeHost.kt")
        assertTrue("viewModel: RemoteNoticeViewModel = koinViewModel()," in host)
        assertTrue("import org.koin.androidx.compose.koinViewModel" in host)

        assertFalse(plan.files.any { "javax.inject" in it.content })
        assertFalse(plan.files.any { "@Inject" in it.content })
        assertTrue(plan.manualSteps.any { "INTERNET" in it })
        assertTrue(plan.manualSteps.any { "startKoin" in it })
        assertTrue(plan.manualSteps.any { "MainActivity.kt" in it && "RemoteNoticeHost" in it })
        assertTrue(plan.notes.any { "com.example.demo.remoteconfig" in it })
        assertTrue(plan.notes.any { "Koin" in it })
        assertTrue(plan.notes.any { "LocalDate" in it })
    }

    @Test
    fun `no di and no http falls back to container + retrofit gson with dependency steps`() = runTest {
        val root = Fixtures.tempDir()
        try {
            root.writeFile("settings.gradle.kts", "rootProject.name = \"plain\"\ninclude(\":app\")\n")
            root.writeFile(
                "app/build.gradle.kts",
                """
                plugins { id("com.android.application") }
                android { namespace = "com.plain" }
                dependencies {
                    implementation("androidx.compose.material:material:1.6.0")
                    implementation("androidx.navigation:navigation-compose:2.8.0")
                }
                """,
            )
            root.resolve("app/src/main/kotlin").createDirectories()
            root.writeFile("app/src/main/AndroidManifest.xml", "<manifest><uses-permission android:name=\"android.permission.INTERNET\"/></manifest>")

            val project = scanner.scan(root)
            assertEquals(DiFramework.NONE, project.di)
            assertEquals(HttpStack.NONE, project.http)

            val plan = generator.plan(project, target)
            assertWellFormed(plan)
            assertTrue(plan.paths().all { it.startsWith("app/src/main/kotlin/com/plain/remoteconfig/") }, plan.paths().toString())

            val container = plan.content("RemoteConfigContainer.kt")
            assertTrue("object RemoteConfigContainer" in container)
            assertTrue("fun viewModelFactory(context: Context): ViewModelProvider.Factory" in container)
            assertTrue("GsonConverterFactory.create()" in container)

            val host = plan.content("RemoteNoticeHost.kt")
            assertTrue("viewModel(factory = RemoteConfigContainer.viewModelFactory(LocalContext.current))" in host)
            assertTrue("import androidx.compose.material.AlertDialog" in host)
            assertTrue("MaterialTheme.typography.h6" in plan.content("RemoteBlockingScreen.kt"))

            assertTrue(plan.manualSteps.any { "com.squareup.retrofit2:retrofit" in it })
            assertFalse(plan.manualSteps.any { "INTERNET" in it })
        } finally {
            Fixtures.deleteRecursively(root)
        }
    }

    @Test
    fun `existing files become modifications with the original content`() = runTest {
        val root = Fixtures.tempDir()
        try {
            copyTree(Fixtures.project("single-koin-ktor"), root)
            val existing = root.writeFile(
                "app/src/main/java/com/example/demo/remoteconfig/RemoteNotice.kt",
                "package com.example.demo.remoteconfig\n\nclass RemoteNotice\n",
            )

            val plan = generator.plan(scanner.scan(root), target)
            val file = plan.files.single { it.path.fileName.toString() == "RemoteNotice.kt" }
            assertEquals(FileAction.MODIFY, file.action)
            assertEquals(Files.readString(existing), file.original)
            assertTrue(plan.files.filter { it !== file }.all { it.action == FileAction.CREATE })
            assertTrue(plan.notes.any { "덮어쓰기" in it })
        } finally {
            Fixtures.deleteRecursively(root)
        }
    }

    @Test
    fun `already integrated project gets a warning note`() = runTest {
        val plan = generator.plan(scanner.scan(Fixtures.project("umc-like-integrated")), target)
        assertTrue(plan.notes.any { "이미 원격 설정 연동 코드" in it })
    }

    private fun copyTree(from: Path, to: Path) {
        Files.walk(from).use { stream ->
            stream.forEach { source ->
                val destination = to.resolve(source.relativeTo(from).toString())
                if (source.isDirectory()) destination.createDirectories() else Files.copy(source, destination)
            }
        }
    }
}
