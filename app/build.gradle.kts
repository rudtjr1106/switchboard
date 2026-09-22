import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.foundation.ExperimentalFoundationApi",
            "androidx.compose.ui.ExperimentalComposeUiApi",
        )
    }
}

val appVersion: String = providers.gradleProperty("switchboard.version").get()
val displayName = "스위치보드"
val githubClientId: String = providers.gradleProperty("switchboard.githubClientId").orElse("").get()

// 빌드 시점 상수를 코드로 만든다 (Android 의 BuildConfig 와 같은 역할)
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo/kotlin")
    inputs.property("version", appVersion)
    inputs.property("clientId", githubClientId)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("io/github/rudtjr1106/switchboard/app/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |package io.github.rudtjr1106.switchboard.app
            |
            |object BuildInfo {
            |    const val VERSION = "$appVersion"
            |    const val GITHUB_CLIENT_ID = "$githubClientId"
            |    const val REPOSITORY_OWNER = "rudtjr1106"
            |    const val REPOSITORY_NAME = "switchboard"
            |}
            |""".trimMargin(),
        )
    }
}

kotlin.sourceSets.main {
    kotlin.srcDir(generateBuildInfo)
}

dependencies {
    implementation(project(":core:config"))
    implementation(project(":core:github"))
    implementation(project(":core:ai"))
    implementation(project(":core:scanner"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    // DI 조립에서 GitHub/AI 모듈에 넘길 HttpClient 를 만든다
    implementation(libs.ktor.client.core)
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    // core:config 의 테스트 픽스처(UMC 저장소 파일)를 앱 테스트에서도 쓴다
    testImplementation(testFixtures(project(":core:config")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// run/패키징이 Gradle 을 띄운 JDK 가 아니라 툴체인 JDK(21)를 쓰게 한다. jpackage 도 이 JDK 로 돈다
val toolchainLauncher = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }

compose.desktop {
    application {
        mainClass = "io.github.rudtjr1106.switchboard.app.MainKt"
        javaHome = toolchainLauncher.get().metadata.installationPath.asFile.absolutePath

        jvmArgs += listOf(
            "-Xmx2g",
            "-Dapple.awt.application.appearance=system",
        )

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)
            // 실행 파일·설치 파일 안의 이름은 영문으로 둔다
            // - macOS: codesign 이 실행 파일 이름이 한글이면 서명하지 못한다. 보이는 이름은 번들 폴더(packageMacDmg)와 dockName 으로 준다
            // - Windows: WiX 3 의 MSI 는 기본 코드 페이지(1252)라 한글 제품 이름을 담지 못한다(LGHT0311)
            // 앱 창 제목·메뉴는 코드에서 스위치보드로 보인다
            packageName = "Switchboard"
            packageVersion = appVersion
            // MSI 표에 들어가므로 한글을 쓰지 않는다 (WiX 3 코드 페이지 1252)
            description = "Remote config editor for Android apps"
            vendor = "rudtjr1106"
            copyright = "© 2026 rudtjr1106. MIT License."
            licenseFile.set(rootProject.file("LICENSE"))

            // JNA(키체인)·logback(JNDI)·HTTP 가 쓰는 JDK 모듈
            modules("java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec", "java.management", "java.sql")

            macOS {
                bundleID = "io.github.rudtjr1106.switchboard"
                // 메뉴 막대·앱 전환기(CFBundleName)에 보이는 이름
                dockName = displayName
                iconFile.set(project.file("icons/switchboard.icns"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>LSApplicationCategoryType</key>
                        <string>public.app-category.developer-tools</string>
                        <key>CFBundleDevelopmentRegion</key>
                        <string>ko</string>
                        <key>CFBundleDisplayName</key>
                        <string>$displayName</string>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/switchboard.ico"))
                menuGroup = "Switchboard"
                perUserInstall = true
                shortcut = true
                dirChooser = false
                // 같은 UUID 를 유지해야 새 버전 MSI 가 이전 버전을 덮어쓴다
                upgradeUuid = "4f3d3b7a-6c2e-4d8b-9e1a-2b7f0c9d5e11"
            }
        }
    }
}

/**
 * macOS 설치 파일. 서명된 Switchboard.app 을 `스위치보드.app` 폴더 이름으로 담는다
 *
 * codesign 은 실행 파일 이름이 한글이면 서명하지 못하지만, 번들 폴더 이름은 서명에 들어가지 않아 바꿔도 서명이 유지된다.
 * Finder·Dock·Launchpad 는 폴더 이름을 보여주므로 사용자에게는 '스위치보드' 로 보인다.
 */
val packageMacDmg by tasks.registering {
    group = "compose desktop"
    description = "macOS DMG (앱 이름: $displayName)"
    dependsOn("createDistributable")
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app")
    val stageDir = layout.buildDirectory.dir("mac-dmg/stage")
    val dmgFile = layout.buildDirectory.file("compose/binaries/main/dmg/$displayName-$appVersion.dmg")
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    outputs.file(dmgFile)
    doLast {
        fun run(vararg command: String) {
            val process = ProcessBuilder(*command).inheritIO().start()
            check(process.waitFor() == 0) { "${command.first()} 실패: ${command.joinToString(" ")}" }
        }
        val stage = stageDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        val dmg = dmgFile.get().asFile.apply { parentFile.mkdirs(); delete() }
        val bundle = stage.resolve("$displayName.app")
        // ditto 는 확장 속성과 서명을 그대로 옮긴다
        run("ditto", appDir.get().asFile.resolve("Switchboard.app").absolutePath, bundle.absolutePath)
        run("codesign", "--verify", "--strict", bundle.absolutePath)
        run("ln", "-s", "/Applications", stage.resolve("Applications").absolutePath)
        run("hdiutil", "create", "-volname", displayName, "-srcfolder", stage.absolutePath, "-format", "UDZO", "-ov", dmg.absolutePath)
        logger.lifecycle("완료: ${dmg.absolutePath}")
    }
}
