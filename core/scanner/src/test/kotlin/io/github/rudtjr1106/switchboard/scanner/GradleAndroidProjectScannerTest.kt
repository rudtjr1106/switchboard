package io.github.rudtjr1106.switchboard.scanner

import io.github.rudtjr1106.switchboard.scanner.Fixtures.writeFile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GradleAndroidProjectScannerTest {

    private val scanner = GradleAndroidProjectScanner()

    @Test
    fun `umc-like project is hilt + retrofit gson with type-safe destinations`() = runTest {
        val project = scanner.scan(Fixtures.project("umc-like"))

        assertEquals("UMC product", project.name)
        assertEquals(DiFramework.HILT, project.di)
        assertEquals(HttpStack.RETROFIT_GSON, project.http)
        assertEquals(NavigationStyle.NAVIGATION_COMPOSE_TYPESAFE, project.navigation)
        assertFalse(project.hasRemoteConfigIntegration)

        assertEquals(18, project.modules.size)
        assertEquals(":app", project.modules.first().path)
        assertEquals(":app", project.appModule?.path)

        fun namespace(path: String) = project.modules.first { it.path == path }.namespace
        assertEquals("com.umc.product", namespace(":app"))
        assertEquals("com.umc.data", namespace(":data"))
        assertEquals("com.umc.domain", namespace(":domain"))
        assertEquals("com.umc.presentation", namespace(":presentation"))
        assertNull(namespace(":lint-rules"))

        val app = project.appModule!!
        assertTrue(app.isApplication)
        assertTrue("com.android.application" in app.plugins)
        assertTrue("com.google.dagger.hilt.android" in app.plugins)
        assertTrue("libs.retrofit.converter.gson" in app.dependencies)
        assertTrue("project(:presentation)" in app.dependencies)
        assertTrue("com.android.tools:desugar_jdk_libs:2.0.4" in app.dependencies)
        assertFalse(project.modules.first { it.path == ":domain" }.isApplication)

        assertEquals("com.squareup.retrofit2:retrofit", project.versionCatalog["libs.retrofit.android"])
        assertEquals("androidx.compose.material3:material3", project.versionCatalog["libs.androidx.compose.material3"])
        assertEquals("com.google.dagger.hilt.android", project.versionCatalog["libs.plugins.hilt.android"])
        assertEquals("org.jetbrains.kotlinx:kotlinx-serialization-json", project.versionCatalog["libs.kotlinx.serialization.json"])
    }

    @Test
    fun `umc-like destinations keep source order, arguments and comments`() = runTest {
        val project = scanner.scan(Fixtures.project("umc-like"))
        val names = project.destinationNames

        assertEquals(35, names.size, names.toString())
        assertEquals("Splash", names.first())
        assertEquals("CommunityChatting", names.last())
        assertEquals(listOf("Splash", "Login", "EmailLogin", "FindPassword", "SignUp", "SocialSignUp", "EmailSignUp"), names.take(7))
        for (expected in listOf("EmailSignUp", "NoticeDetail", "Community", "ReceivedCard", "Act", "Home", "Mycard")) {
            assertTrue(expected in names, "$expected 가 없어요")
        }
        assertEquals(names.size, names.toSet().size, "중복된 목적지가 있어요")

        val byName = project.destinations.associateBy { it.name }
        assertTrue(byName.getValue("NoticeDetail").hasArguments)
        assertTrue(byName.getValue("SignUp").hasArguments)
        assertFalse(byName.getValue("Splash").hasArguments)
        assertFalse(byName.getValue("EmailSignUp").hasArguments)

        assertEquals("공지 상세", byName.getValue("NoticeDetail").comment)
        assertEquals("비밀번호 찾기 (이메일 인증 후 새 비밀번호 설정)", byName.getValue("FindPassword").comment)
        assertEquals("검색은 목록과 같은 필터 조건으로 조회해야 하므로 현재 탭·필터를 함께 넘긴다", byName.getValue("NoticeSearch").comment)
        assertNull(byName.getValue("Splash").comment)
        assertNull(byName.getValue("Act").comment, "섹션 제목은 주석으로 잡지 않아요")
        assertTrue(byName.getValue("NoticeDetail").file.endsWith("MainDestination.kt"))
        assertTrue(project.notes.any { "MainDestination" in it && "35" in it })
    }

    @Test
    fun `integrated fixture reports the existing remote config code`() = runTest {
        val project = scanner.scan(Fixtures.project("umc-like-integrated"))
        assertTrue(project.hasRemoteConfigIntegration)
        assertTrue(project.notes.any { "이미 있어요" in it })
        assertEquals(35, project.destinations.size)
    }

    @Test
    fun `single module koin + ktor + compose project`() = runTest {
        val project = scanner.scan(Fixtures.project("single-koin-ktor"))

        assertEquals("demo", project.name)
        assertEquals(DiFramework.KOIN, project.di)
        assertEquals(HttpStack.KTOR, project.http)
        assertEquals(NavigationStyle.NAVIGATION_COMPOSE_TYPESAFE, project.navigation)
        assertEquals(listOf("Home", "Detail", "Settings"), project.destinationNames)
        assertTrue(project.destinations[1].hasArguments)
        assertEquals("시작 화면", project.destinations[0].comment)
        assertEquals("상세. id 로 연다", project.destinations[1].comment)
        assertEquals("com.example.demo", project.appModule?.namespace)
        assertTrue(project.versionCatalog.isEmpty())
        assertTrue(project.notes.any { "libs.versions.toml" in it })
        assertFalse(project.hasRemoteConfigIntegration)

        val facts = ProjectFacts.of(project)
        assertTrue(facts.usesCompose)
        assertEquals(ComposeMaterial.MATERIAL3, facts.material)
        assertFalse(facts.hasInternetPermission)
        assertNotNull(facts.manifestPath)
    }

    @Test
    fun `groovy settings and build scripts are parsed`() = runTest {
        val root = Fixtures.tempDir()
        try {
            root.writeFile(
                "settings.gradle",
                """
                // include 'not-a-module' in a comment
                rootProject.name = 'groovy-app'
                include ':app', ':lib',
                        ':feature'
                include(':tools')
                includeBuild 'build-logic'
                """,
            )
            root.writeFile(
                "app/build.gradle",
                """
                apply plugin: 'com.android.application'
                apply plugin: 'kotlin-android'

                android {
                    namespace 'com.g.app'
                    defaultConfig {
                        applicationId "com.g.app"
                        buildConfigField "String", "BASE_URL", '"https://example.com/api/"'
                    }
                }

                dependencies {
                    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
                    implementation "com.squareup.retrofit2:converter-moshi:2.9.0"
                    implementation project(':lib')
                    testImplementation 'junit:junit:4.13.2'
                }
                """,
            )
            root.writeFile("lib/build.gradle", "apply plugin: 'java-library'\n")
            root.writeFile(
                "app/src/main/java/com/g/app/Nav.kt",
                """
                package com.g.app

                fun graph() {
                    composable("home") {}
                    composable(route = "detail/{id}") {}
                    composable("settings?tab={tab}") {}
                }
                """,
            )

            val project = scanner.scan(root)
            assertEquals("groovy-app", project.name)
            assertEquals(listOf(":app", ":lib", ":feature", ":tools"), project.modules.map { it.path })
            val app = project.appModule!!
            assertEquals(":app", app.path)
            assertEquals("com.g.app", app.namespace)
            assertTrue("com.android.application" in app.plugins)
            assertTrue("project(:lib)" in app.dependencies)
            assertFalse(app.dependencies.any { "https" in it })
            assertEquals(HttpStack.RETROFIT_MOSHI, project.http)
            assertEquals(DiFramework.NONE, project.di)
            assertEquals(NavigationStyle.STRING_ROUTES, project.navigation)
            assertEquals(listOf("home", "detail", "settings"), project.destinationNames)
            assertTrue(project.destinations[1].hasArguments)
            assertFalse(project.destinations[0].hasArguments)
            assertTrue(project.notes.any { "DI 프레임워크" in it })
        } finally {
            Fixtures.deleteRecursively(root)
        }
    }

    @Test
    fun `navigation 3 keys and projectDir overrides are recognised`() = runTest {
        val root = Fixtures.tempDir()
        try {
            root.writeFile(
                "settings.gradle.kts",
                """
                rootProject.name = "nav3"
                include(":app", ":shared")
                project(":shared").projectDir = file("libs/shared-code")
                """,
            )
            root.writeFile(
                "app/build.gradle.kts",
                """
                plugins { id("com.android.application") }
                android { namespace = "com.n3" }
                dependencies {
                    implementation("androidx.navigation3:navigation3-runtime:1.0.0")
                    implementation("androidx.compose.material3:material3:1.3.0")
                }
                """,
            )
            root.writeFile("libs/shared-code/build.gradle.kts", "plugins { id(\"org.jetbrains.kotlin.jvm\") }\n")
            root.writeFile(
                "app/src/main/kotlin/com/n3/Keys.kt",
                """
                package com.n3

                import androidx.navigation3.runtime.NavKey
                import kotlinx.serialization.Serializable

                @Serializable data object Home : NavKey
                // 상품 상세
                @Serializable data class Product(val id: Long) : NavKey
                """,
            )

            val project = scanner.scan(root)
            assertEquals(NavigationStyle.NAVIGATION3, project.navigation)
            assertEquals(listOf("Home", "Product"), project.destinationNames)
            assertEquals("상품 상세", project.destinations[1].comment)
            assertTrue(project.destinations[1].hasArguments)
            assertEquals(root.resolve("libs/shared-code"), project.modules.first { it.path == ":shared" }.dir)
        } finally {
            Fixtures.deleteRecursively(root)
        }
    }

    @Test
    fun `a folder without settings is rejected with a korean message`() = runTest {
        val root = Fixtures.tempDir()
        try {
            val error = assertFailsWith<ProjectScanException> { scanner.scan(root) }
            assertTrue("settings.gradle" in error.message.orEmpty())
        } finally {
            Fixtures.deleteRecursively(root)
        }
    }
}
