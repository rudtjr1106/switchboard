package io.github.rudtjr1106.switchboard.app.setup

import io.github.rudtjr1106.switchboard.ai.CodeAdapter
import io.github.rudtjr1106.switchboard.scanner.AndroidProject
import io.github.rudtjr1106.switchboard.scanner.DiFramework
import io.github.rudtjr1106.switchboard.scanner.FileAction
import io.github.rudtjr1106.switchboard.scanner.GeneratedFile
import io.github.rudtjr1106.switchboard.scanner.HttpStack
import io.github.rudtjr1106.switchboard.scanner.NavigationStyle
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAdaptationTest {

    private val root = Files.createTempDirectory("switchboard-adapt")
    private val module = """
        package com.acme.di

        import dagger.Module
        import dagger.Provides

        @Module
        object RemoteConfigModule {
            @Provides
            fun provideApi(): RemoteConfigApi = RemoteConfigApi()
        }
    """.trimIndent()

    private fun project(di: DiFramework, http: HttpStack) = AndroidProject(
        root = root, name = "acme", modules = emptyList(), appModule = null, di = di, http = http,
        navigation = NavigationStyle.UNKNOWN, destinations = emptyList(), hasRemoteConfigIntegration = false, versionCatalog = emptyMap(),
    )

    private val files = listOf("RemoteNotice.kt", "RemoteConfigApi.kt", "RemoteConfigRemoteDataSourceImpl.kt", "RemoteConfigModule.kt", "RemoteConfigContainer.kt")
        .map { GeneratedFile(Path.of("app/src/main/java/com/acme/$it"), "package com.acme\n", FileAction.CREATE) }

    @Test
    fun `detected stacks need no ai at all`() {
        assertTrue(AiAdaptation.targets(project(DiFramework.HILT, HttpStack.RETROFIT_GSON), files).isEmpty())
        assertTrue(AiAdaptation.targets(project(DiFramework.KOIN, HttpStack.KTOR), files).isEmpty())
    }

    @Test
    fun `only the files behind a detection gap are picked`() {
        val dagger = AiAdaptation.targets(project(DiFramework.DAGGER, HttpStack.RETROFIT_GSON), files).map { it.file.path.fileName.toString() }
        assertEquals(listOf("RemoteConfigModule.kt"), dagger)
        val noHttp = AiAdaptation.targets(project(DiFramework.NONE, HttpStack.NONE), files).map { it.file.path.fileName.toString() }
        assertEquals(listOf("RemoteConfigContainer.kt", "RemoteConfigApi.kt", "RemoteConfigRemoteDataSourceImpl.kt"), noHttp)
    }

    @Test
    fun `broken model output is rejected`() {
        assertNull(AiAdaptation.reject(module, module.replace("fun provideApi", "fun provideRemoteConfigApi")))
        assertEquals("package 가 바뀜", AiAdaptation.reject(module, module.replace("package com.acme.di", "package com.other")))
        assertEquals("RemoteConfigModule 선언이 없어짐", AiAdaptation.reject(module, module.replace("object RemoteConfigModule", "object NetworkModule")))
        assertEquals("괄호 짝이 맞지 않음", AiAdaptation.reject(module, module.dropLast(1)))
        assertEquals("코드 블록 표시가 남음", AiAdaptation.reject(module, "```kotlin\n$module\n```"))
        assertEquals("길이가 너무 달라짐", AiAdaptation.reject(module, "package com.acme.di\nobject RemoteConfigModule {}"))
    }

    @Test
    fun `accepted output is marked as adapted and rejected output keeps the template`() = runTest {
        val target = AiAdaptation.Target(0, GeneratedFile(Path.of("RemoteConfigModule.kt"), module, FileAction.CREATE), "Dagger")
        val good = object : CodeAdapter {
            override suspend fun adapt(fileName: String, template: String, projectNotes: String) = template.replace("fun provideApi", "fun provideRemoteConfigApi")
        }
        val bad = object : CodeAdapter {
            override suspend fun adapt(fileName: String, template: String, projectNotes: String) = "모르겠어요"
        }
        val adapted = assertNotNull(AiAdaptation.adapt(good, target, "notes"))
        assertTrue(adapted.adaptedByAi)
        assertTrue("provideRemoteConfigApi" in adapted.content)
        assertNull(AiAdaptation.adapt(bad, target, "notes"))
    }

    @Test
    fun `imports follow a changed parameter type`() {
        val code = """
            package com.acme.di

            import android.content.Context
            import dagger.Module

            @Module
            object RemoteConfigModule {
                fun provide(application: Application) = application.cacheDir
            }
        """.trimIndent()
        val fixed = AiAdaptation.fixImports(code)
        assertTrue("import android.app.Application" in fixed, fixed)
        assertTrue("import android.content.Context" !in fixed, fixed)
        assertTrue("import dagger.Module" in fixed)
    }
}
