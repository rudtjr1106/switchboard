package io.github.rudtjr1106.switchboard.app.setup

import io.github.rudtjr1106.switchboard.ai.LlamaCppEngine
import io.github.rudtjr1106.switchboard.ai.LlmCodeAdapter
import io.github.rudtjr1106.switchboard.ai.ModelCatalog
import io.github.rudtjr1106.switchboard.config.ScreenInfo
import io.github.rudtjr1106.switchboard.scanner.GradleAndroidProjectScanner
import io.github.rudtjr1106.switchboard.scanner.IntegrationTarget
import io.github.rudtjr1106.switchboard.scanner.TemplateIntegrationGenerator
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * 실제 모델로 AI 코드 적응을 한 번 돌린다: `SWITCHBOARD_AI_EVAL=1 ./gradlew :app:test --tests '*AiAdaptationModelTest*'`
 *
 * Hilt 없는 Dagger 프로젝트에서 만든 RemoteConfigModule 을, 그 프로젝트의 기존 @Module 을 근거로 고치게 하고
 * 결과가 [AiAdaptation.reject] 검사를 통과하는지 본다.
 */
class AiAdaptationModelTest {

    @Test
    fun `gemma adapts a dagger module against the project's own module`() = runBlocking {
        assumeTrue(System.getenv("SWITCHBOARD_AI_EVAL") == "1", "SWITCHBOARD_AI_EVAL=1 일 때만")
        val models = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Switchboard", "models")
        val spec = listOf(ModelCatalog.GEMMA_3_4B, ModelCatalog.GEMMA_3_1B)
            .firstOrNull { Files.exists(models.resolve(it.fileName)) && Files.size(models.resolve(it.fileName)) == it.sizeBytes }
        assumeTrue(spec != null, "받아 둔 모델이 없어요")

        val root = Files.createTempDirectory("dagger-app")
        fun write(path: String, text: String) = root.resolve(path).also { it.parent.createDirectories() }.writeText(text.trimIndent())
        write("settings.gradle.kts", "rootProject.name = \"legacy\"\ninclude(\":app\")")
        write(
            "app/build.gradle.kts",
            """
            plugins { id("com.android.application") }
            android { namespace = "com.legacy.app" }
            dependencies {
                implementation("com.google.dagger:dagger:2.57")
                implementation("com.squareup.retrofit2:retrofit:3.0.0")
                implementation("com.squareup.retrofit2:converter-gson:3.0.0")
            }
            """,
        )
        write(
            "app/src/main/java/com/legacy/app/di/NetworkModule.kt",
            """
            package com.legacy.app.di

            import android.app.Application
            import dagger.Module
            import dagger.Provides
            import okhttp3.OkHttpClient
            import javax.inject.Singleton

            @Module
            class NetworkModule(private val application: Application) {
                @Provides
                @Singleton
                fun provideApplication(): Application = application

                @Provides
                @Singleton
                fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder().build()
            }
            """,
        )
        val project = GradleAndroidProjectScanner().scan(root)
        val plan = TemplateIntegrationGenerator().plan(
            project,
            IntegrationTarget("https://legacy.github.io/", "legacy-config/app-config.json", "legacy/legacy-config", listOf(ScreenInfo("ALL"))),
        )
        val target = AiAdaptation.targets(project, plan.files).single()

        val engine = LlamaCppEngine()
        engine.load(spec!!, models.resolve(spec.fileName))
        try {
            val notes = AiAdaptation.projectNotes(project)
            val started = TimeSource.Monotonic.markNow()
            val adapted = AiAdaptation.adapt(LlmCodeAdapter(engine), target, notes)
            val raw = adapted?.content.orEmpty()
            val elapsed = started.elapsedNow()
            val problem = AiAdaptation.reject(target.file.content, raw)
            println("=== ${spec.displayName} · ${elapsed.inWholeSeconds}s · ${problem ?: "통과"} ===")
            println(raw)
            println("Application 을 쓰는가: ${"application: Application" in raw || "Application)" in raw}")
            assertTrue(adapted != null && problem == null, "검사 실패: $problem")
        } finally {
            engine.unload()
        }
    }
}
