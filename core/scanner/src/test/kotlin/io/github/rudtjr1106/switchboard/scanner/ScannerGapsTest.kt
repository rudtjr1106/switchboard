package io.github.rudtjr1106.switchboard.scanner

import io.github.rudtjr1106.switchboard.config.ScreenInfo
import io.github.rudtjr1106.switchboard.scanner.Fixtures.writeFile
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Hilt·Retrofit 가 모듈 스크립트에 바로 보이지 않는 프로젝트들 */
class ScannerGapsTest {

    private val scanner = GradleAndroidProjectScanner()
    private val target = IntegrationTarget(
        pagesBaseUrl = "https://acme.github.io/",
        configPath = "acme-config/app-config.json",
        repoFullName = "acme/acme-config",
        screens = listOf(ScreenInfo("ALL")),
    )

    /** nowinandroid 식: 모듈에는 컨벤션 플러그인 alias 만, 실제 의존성은 build-logic 안에 */
    private fun conventionProject(): Path = Fixtures.tempDir().apply {
        writeFile(
            "settings.gradle.kts",
            """
            pluginManagement {
                includeBuild("build-logic")
                repositories { google(); mavenCentral() }
            }
            rootProject.name = "acme"
            include(":app", ":core:data")
            """.trimIndent(),
        )
        writeFile(
            "gradle/libs.versions.toml",
            """
            [libraries]
            hilt-android = { group = "com.google.dagger", name = "hilt-android", version = "2.57" }
            androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version = "2025.09.00" }
            androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
            retrofit-core = { module = "com.squareup.retrofit2:retrofit", version = "3.0.0" }
            retrofit-gson = { module = "com.squareup.retrofit2:converter-gson", version = "3.0.0" }

            [plugins]
            hilt = { id = "com.google.dagger.hilt.android", version = "2.57" }
            acme-android-application = { id = "acme.android.application" }
            acme-android-compose = { id = "acme.android.compose" }
            acme-hilt = { id = "acme.hilt" }
            """.trimIndent(),
        )
        writeFile(
            "build-logic/convention/build.gradle.kts",
            """
            plugins { `kotlin-dsl` }
            gradlePlugin {
                plugins {
                    register("androidApplication") {
                        id = "acme.android.application"
                        implementationClass = "AndroidApplicationConventionPlugin"
                    }
                    register("androidCompose") {
                        id = "acme.android.compose"
                        implementationClass = "AndroidComposeConventionPlugin"
                    }
                    register("hilt") {
                        id = "acme.hilt"
                        implementationClass = "HiltConventionPlugin"
                    }
                }
            }
            """.trimIndent(),
        )
        writeFile(
            "build-logic/convention/src/main/kotlin/AndroidApplicationConventionPlugin.kt",
            """
            class AndroidApplicationConventionPlugin : Plugin<Project> {
                override fun apply(target: Project) = with(target) {
                    with(pluginManager) {
                        apply("com.android.application")
                        apply("acme.android.compose")
                    }
                }
            }
            """.trimIndent(),
        )
        writeFile(
            "build-logic/convention/src/main/kotlin/AndroidComposeConventionPlugin.kt",
            """
            class AndroidComposeConventionPlugin : Plugin<Project> {
                override fun apply(target: Project) = with(target) {
                    apply(plugin = "org.jetbrains.kotlin.plugin.compose")
                    dependencies {
                        "implementation"(platform(libs.findLibrary("androidx-compose-bom").get()))
                        "implementation"(libs.findLibrary("androidx.compose.material3").get())
                    }
                }
            }
            """.trimIndent(),
        )
        writeFile(
            "build-logic/convention/src/main/kotlin/HiltConventionPlugin.kt",
            """
            class HiltConventionPlugin : Plugin<Project> {
                override fun apply(target: Project) = with(target) {
                    pluginManager.apply("com.google.dagger.hilt.android")
                    dependencies { "implementation"(libs.findLibrary("hilt.android").get()) }
                }
            }
            """.trimIndent(),
        )
        writeFile(
            "app/build.gradle.kts",
            """
            plugins {
                alias(libs.plugins.acme.android.application)
                alias(libs.plugins.acme.hilt)
            }
            android { namespace = "com.acme.app" }
            dependencies { implementation(projects.core.data) }
            """.trimIndent(),
        )
        writeFile(
            "core/data/build.gradle.kts",
            """
            plugins { alias(libs.plugins.acme.hilt) }
            android { namespace = "com.acme.core.data" }
            dependencies {
                implementation(libs.retrofit.core)
                implementation(libs.retrofit.gson)
            }
            """.trimIndent(),
        )
        writeFile(
            "app/src/main/java/com/acme/app/Route.kt",
            """
            package com.acme.app
            import kotlinx.serialization.Serializable
            sealed interface Route {
                @Serializable data object Home : Route
                @Serializable data class Detail(val id: Long) : Route
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `convention plugins in build-logic are followed into the modules that apply them`() = runTest {
        val project = scanner.scan(conventionProject())
        assertEquals(DiFramework.HILT, project.di)
        assertEquals(HttpStack.RETROFIT_GSON, project.http)
        // com.android.application 도 컨벤션 플러그인이 넣는다
        assertEquals(":app", project.appModule?.path)
        val facts = ProjectFacts.of(project)
        assertTrue(facts.usesCompose, "앱 컨벤션 → Compose 컨벤션을 따라가야 한다")
        assertEquals(ComposeMaterial.MATERIAL3, facts.material)
        assertTrue(project.notes.any { "컨벤션 플러그인" in it }, project.notes.toString())
        assertEquals(listOf("Home", "Detail"), project.destinationNames)
    }

    /** Hilt 없이 Dagger, Fragment + XML 내비게이션, 한국어 strings */
    private fun daggerXmlProject(): Path = Fixtures.tempDir().apply {
        writeFile("settings.gradle.kts", "rootProject.name = \"legacy\"\ninclude(\":app\")\n")
        writeFile(
            "app/build.gradle.kts",
            """
            plugins { id("com.android.application"); id("kotlin-kapt") }
            android { namespace = "com.legacy.app" }
            dependencies {
                implementation("com.google.dagger:dagger:2.57")
                kapt("com.google.dagger:dagger-compiler:2.57")
                implementation("com.squareup.retrofit2:retrofit:3.0.0")
                implementation("com.squareup.retrofit2:converter-gson:3.0.0")
                implementation("androidx.navigation:navigation-fragment-ktx:2.9.0")
            }
            """.trimIndent(),
        )
        writeFile(
            "app/src/main/res/navigation/nav_main.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                android:id="@+id/nav_main" app:startDestination="@id/homeFragment">
                <!-- <fragment android:id="@+id/commentedOut" /> -->
                <fragment android:id="@+id/homeFragment" android:name="com.legacy.HomeFragment" android:label="@string/title_home" />
                <fragment android:id="@+id/orderDetailFragment" android:name="com.legacy.OrderDetailFragment" android:label="주문 상세">
                    <argument android:name="orderId" app:argType="long" />
                </fragment>
                <navigation android:id="@+id/nav_my" android:label="@string/section_my">
                    <fragment android:id="@+id/profileFragment" android:name="com.legacy.ProfileFragment" android:label="@string/title_profile" />
                </navigation>
                <dialog android:id="@+id/logoutDialog" android:name="com.legacy.LogoutDialog" />
            </navigation>
            """.trimIndent(),
        )
        writeFile(
            "app/src/main/res/values/strings.xml",
            """<resources><string name="title_home">Home</string><string name="title_profile">Profile</string><string name="section_my">My</string></resources>""",
        )
        writeFile(
            "app/src/main/res/values-ko/strings.xml",
            """<resources><string name="title_home">홈</string><string name="title_profile">내 프로필</string><string name="section_my">마이</string></resources>""",
        )
        writeFile("app/src/main/java/com/legacy/app/App.kt", "package com.legacy.app\nclass App\n")
    }

    @Test
    fun `dagger without hilt and xml navigation graphs are recognized`() = runTest {
        val project = scanner.scan(daggerXmlProject())
        assertEquals(DiFramework.DAGGER, project.di)
        assertEquals(NavigationStyle.XML_GRAPH, project.navigation)
        val byName = project.destinations.associateBy { it.name }
        assertEquals(listOf("homeFragment", "orderDetailFragment", "profileFragment", "logoutDialog"), project.destinationNames)
        assertEquals("홈", byName.getValue("homeFragment").comment, "values-ko 가 values 를 덮어야 한다")
        assertEquals("주문 상세", byName.getValue("orderDetailFragment").comment)
        assertTrue(byName.getValue("orderDetailFragment").hasArguments)
        assertFalse(byName.getValue("homeFragment").hasArguments)
        assertEquals("마이", byName.getValue("profileFragment").section)
        assertEquals(null, byName.getValue("homeFragment").section)
    }

    @Test
    fun `dagger projects get modules without InstallIn and a component wiring step`() = runTest {
        val project = scanner.scan(daggerXmlProject())
        val plan = TemplateIntegrationGenerator().plan(project, target)
        val module = plan.files.single { it.path.toString().endsWith("RemoteConfigModule.kt") }.content
        assertTrue("@Module" in module && "@Provides" in module)
        assertFalse("InstallIn" in module || "dagger.hilt" in module || "@ApplicationContext" in module, module)
        val viewModel = plan.files.single { it.path.toString().endsWith("RemoteNoticeViewModel.kt") }.content
        assertTrue("class RemoteNoticeViewModel @Inject constructor(" in viewModel, viewModel)
        assertFalse("HiltViewModel" in viewModel)
        assertTrue(plan.manualSteps.any { "@Component" in it }, plan.manualSteps.toString())
        assertTrue(plan.manualSteps.any { "getResourceEntryName" in it }, plan.manualSteps.toString())
    }
}
