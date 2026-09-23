package io.github.rudtjr1106.switchboard.scanner

import io.github.rudtjr1106.switchboard.config.ScreenInfo
import io.github.rudtjr1106.switchboard.config.ValueSpec
import io.github.rudtjr1106.switchboard.config.ValueType
import io.github.rudtjr1106.switchboard.scanner.Fixtures.writeFile
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 자유 값(`values`)을 타입 있는 코틀린 코드로 만든다 */
class ValueCodegenTest {

    private val values = listOf(
        ValueSpec("eventBannerOn", ValueType.BOOLEAN, "이벤트 배너", default = JsonPrimitive(true)),
        ValueSpec("maxUploadCount", ValueType.INTEGER, "업로드 최대 개수", default = JsonPrimitive(5), minimum = 1.0, maximum = 10.0),
        ValueSpec("matchScore", ValueType.NUMBER, "매칭 점수", default = JsonPrimitive(0.75)),
        ValueSpec(
            "homeTabOrder", ValueType.STRING, "홈 탭 정렬",
            description = "홈 화면 목록 정렬 기준", default = JsonPrimitive("recent"), options = listOf("recent", "popular"),
        ),
    )

    private fun project(): Path = Fixtures.tempDir().apply {
        writeFile("settings.gradle.kts", "rootProject.name = \"acme\"\ninclude(\":app\")\n")
        writeFile(
            "app/build.gradle.kts",
            """
            plugins { id("com.android.application"); id("com.google.dagger.hilt.android") }
            android { namespace = "com.acme.app" }
            dependencies {
                implementation("com.squareup.retrofit2:retrofit:3.0.0")
                implementation("com.squareup.retrofit2:converter-gson:3.0.0")
            }
            """.trimIndent(),
        )
        writeFile("app/src/main/java/com/acme/app/App.kt", "package com.acme.app\nclass App\n")
    }

    private fun target(values: List<ValueSpec>) = IntegrationTarget(
        pagesBaseUrl = "https://acme.github.io/",
        configPath = "acme-config/app-config.json",
        repoFullName = "acme/acme-config",
        screens = listOf(ScreenInfo("ALL")),
        values = values,
    )

    private suspend fun plan(values: List<ValueSpec>): IntegrationPlan =
        TemplateIntegrationGenerator().plan(GradleAndroidProjectScanner().scan(project()), target(values))

    @Test
    fun `값마다 타입과 기본값이 박힌 클래스를 만든다`() = runTest {
        val file = plan(values).files.single { it.path.fileName.toString() == "RemoteValues.kt" }.content
        assertTrue("val eventBannerOn: Boolean = true," in file, file)
        assertTrue("val maxUploadCount: Int = 5," in file, file)
        assertTrue("val matchScore: Double = 0.75," in file, file)
        assertTrue("""val homeTabOrder: String = "recent",""" in file, file)
        // 설명과 고를 수 있는 값이 주석으로 남는다
        assertTrue("/** 홈 화면 목록 정렬 기준 (recent, popular) */" in file, file)
    }

    @Test
    fun `응답 DTO 가 값을 읽어 기본값으로 채운다`() = runTest {
        val file = plan(values).files.single { it.path.fileName.toString() == "AppConfigResponse.kt" }.content
        // Gson 변형에는 기본값(= null)이 붙지 않는다
        assertTrue("val values: RemoteValuesResponse?" in file, file)
        assertTrue("data class RemoteValuesResponse(" in file, file)
        assertTrue("eventBannerOn = eventBannerOn ?: true," in file, file)
        assertTrue("fun toValues(): RemoteValues" in file, file)
    }

    @Test
    fun `리포지토리와 유스케이스가 값을 내려 준다`() = runTest {
        val files = plan(values).files.associateBy { it.path.fileName.toString() }
        assertTrue("suspend fun getValues(): Result<RemoteValues>" in files.getValue("RemoteConfigRepository.kt").content)
        assertTrue("override suspend fun getValues()" in files.getValue("RemoteConfigRepositoryImpl.kt").content)
        val useCase = files.getValue("GetRemoteValuesUseCase.kt").content
        assertTrue("getOrElse { RemoteValues() }" in useCase, useCase)
    }

    @Test
    fun `값이 없으면 값 코드를 만들지 않는다`() = runTest {
        val plan = plan(emptyList())
        val names = plan.files.map { it.path.fileName.toString() }
        assertFalse("RemoteValues.kt" in names, names.toString())
        assertFalse("GetRemoteValuesUseCase.kt" in names, names.toString())
        assertFalse("RemoteValuesResponse" in plan.files.single { it.path.fileName.toString() == "AppConfigResponse.kt" }.content)
        assertFalse("getValues" in plan.files.single { it.path.fileName.toString() == "RemoteConfigRepository.kt" }.content)
    }

    @Test
    fun `값을 쓰는 방법을 직접 할 일로 알려 준다`() = runTest {
        val steps = plan(values).manualSteps
        val step = steps.single { "GetRemoteValuesUseCase" in it }
        assertTrue("values.eventBannerOn" in step, step)
        assertEquals(1, steps.count { "GetRemoteValuesUseCase" in it })
    }
}
