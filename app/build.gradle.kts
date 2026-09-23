import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

/**
 * macOS 서명 신원. `Developer ID Application: <이 값>` 인증서가 키체인에 있어야 한다 (예: `홍길동 (ABCDE12345)`)
 * 환경 변수 SWITCHBOARD_SIGNING_IDENTITY 나 ~/.gradle/gradle.properties 의 switchboard.signingIdentity 로 준다. 없으면 서명하지 않는다
 */
val signingIdentity: String? = providers.environmentVariable("SWITCHBOARD_SIGNING_IDENTITY")
    .orElse(providers.gradleProperty("switchboard.signingIdentity")).orNull?.takeIf { it.isNotBlank() }

/** CI 처럼 기본 키체인을 쓸 수 없을 때, 공증 프로필이 저장된 키체인 파일 경로 */
val notaryKeychain: String? = providers.environmentVariable("SWITCHBOARD_NOTARY_KEYCHAIN").orNull?.takeIf { it.isNotBlank() }

/** `xcrun notarytool store-credentials <이름>` 으로 저장한 공증 프로필 이름 */
val notaryProfile: String = providers.environmentVariable("SWITCHBOARD_NOTARY_PROFILE")
    .orElse(providers.gradleProperty("switchboard.notaryProfile")).orElse("SwitchboardNotary").get()
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
            copyright = "© 2026 rudtjr1106. PolyForm Strict License 1.0.0."
            licenseFile.set(rootProject.file("LICENSE"))

            // JNA(키체인)·logback(JNDI)·HTTP 가 쓰는 JDK 모듈
            modules("java.naming", "java.net.http", "jdk.unsupported", "jdk.crypto.ec", "java.management", "java.sql")

            macOS {
                bundleID = "io.github.rudtjr1106.switchboard"
                // 메뉴 막대·앱 전환기(CFBundleName)에 보이는 이름
                dockName = displayName
                // 서명하면 Compose 가 jar 안의 네이티브 라이브러리(llama.cpp, Skia)까지 서명하고 Hardened Runtime 을 켠다
                if (signingIdentity != null) {
                    signing {
                        sign.set(true)
                        identity.set(signingIdentity)
                    }
                }
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
 * 다른 OS·CPU 용 네이티브 라이브러리를 jar 에서 뺀다
 *
 * java-llama.cpp 는 Mac·Windows·Linux·Android 용 네이티브를 한 jar 에 담아 두어 32MB 가 되고, JNA 도 20여 개 플랫폼을
 * 담고 있다. 설치 파일은 어차피 만든 OS 에서만 돌아가므로(번들된 JDK 도 그 OS·CPU 용이다) 나머지는 용량 낭비다.
 *
 * jar 를 고치면 macOS 앱 번들의 서명이 깨지므로, [packageMacDmg] 가 이 작업 뒤에 앱을 다시 서명한다.
 */
val trimNativeLibraries by tasks.registering {
    group = "compose desktop"
    description = "설치 파일에 필요 없는 다른 OS 용 네이티브를 jar 에서 뺀다"
    dependsOn("createDistributable")
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app")
    // 제자리에서 고치는 작업이라 건너뛰지 않는다 (두 번 돌려도 같은 결과다)
    outputs.upToDateWhen { false }
    doLast {
        val osName = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val is64 = arch in setOf("x86_64", "amd64")
        val llamaKeep = when {
            osName.contains("mac") -> "de/kherud/llama/Mac/"
            osName.contains("win") -> "de/kherud/llama/Windows/"
            else -> "de/kherud/llama/Linux/"
        } + if (is64) "x86_64/" else "aarch64/"
        val jnaKeep = "com/sun/jna/" + when {
            osName.contains("mac") -> "darwin-"
            osName.contains("win") -> "win32-"
            else -> "linux-"
        } + (if (is64) "x86-64" else "aarch64") + "/"
        val jnaPlatformDir = Regex("""^com/sun/jna/(darwin|win32|linux|aix|freebsd|openbsd|sunos|dragonflybsd)-[^/]*/""")
        val llamaPlatformDir = Regex("""^de/kherud/llama/[^/]+/[^/]+/""")

        /** 이 항목을 빼도 되는가 (다른 플랫폼의 네이티브인가) */
        fun isForeign(name: String): Boolean = when {
            llamaPlatformDir.containsMatchIn(name) -> !name.startsWith(llamaKeep)
            jnaPlatformDir.containsMatchIn(name) -> !name.startsWith(jnaKeep)
            else -> false
        }

        val jarDir = appDir.get().asFile.walkTopDown().maxDepth(4)
            .firstOrNull { it.isDirectory && it.name == "app" && it.listFiles { f -> f.name.endsWith(".jar") }?.isNotEmpty() == true }
            ?: error("앱 이미지에서 jar 폴더를 찾지 못했어요: ${appDir.get().asFile}")
        var savedBytes = 0L
        for (jar in jarDir.listFiles { f -> f.name.endsWith(".jar") }?.sorted().orEmpty()) {
            val foreign = ZipFile(jar).use { zip -> zip.entries().toList().filter { isForeign(it.name) } }
            if (foreign.isEmpty()) continue
            val before = jar.length()
            val trimmed = File(jar.parentFile, jar.name + ".trimmed")
            ZipFile(jar).use { zip ->
                ZipOutputStream(trimmed.outputStream().buffered()).use { out ->
                    for (entry in zip.entries()) {
                        if (isForeign(entry.name)) continue
                        out.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
            check(trimmed.renameTo(jar) || (jar.delete() && trimmed.renameTo(jar))) { "jar 를 바꾸지 못했어요: $jar" }
            savedBytes += before - jar.length()
            logger.lifecycle("네이티브 정리: ${jar.name} (${foreign.size} 개, ${(before - jar.length()) / 1024 / 1024}MB 절약)")
        }
        logger.lifecycle("네이티브 정리 합계: ${savedBytes / 1024 / 1024}MB 절약 (남긴 것: $llamaKeep, $jnaKeep)")

        // jar 를 고치면 앱 번들의 서명 봉인(CodeResources)이 깨진다. macOS 는 서명 없이 만들 때도 Compose 가 ad-hoc 서명을
        // 붙이므로, 정리한 뒤에는 늘 다시 서명한다. 신원이 있으면 packageMacDmg 가 entitlements 까지 붙여 한 번 더 서명한다
        if (savedBytes > 0 && osName.contains("mac")) {
            val bundle = appDir.get().asFile.resolve("Switchboard.app")
            val identity = signingIdentity?.let { "Developer ID Application: $it" } ?: "-"
            val process = ProcessBuilder("codesign", "--force", "--deep", "--sign", identity, bundle.absolutePath)
                .inheritIO().start()
            check(process.waitFor() == 0) { "정리 뒤 앱을 다시 서명하지 못했어요: $bundle" }
            logger.lifecycle("정리 뒤 앱 다시 서명: ${if (signingIdentity != null) "Developer ID" else "ad-hoc"}")
        }
    }
}

/**
 * Windows 배포본. 설치 프로그램 없이 풀어서 바로 쓰는 zip
 *
 * MSI 는 Windows Installer 를 거쳐 설치가 오래 걸리고, 제자리 업데이트도 msiexec 를 다시 태워야 한다.
 * zip 은 압축만 풀면 끝이고, 앱이 스스로 폴더를 바꿔 끼우는 업데이트(UpdateInstaller)도 그대로 된다.
 */
val packageWindowsZip by tasks.registering(Zip::class) {
    group = "compose desktop"
    description = "Windows 무설치 zip"
    dependsOn(trimNativeLibraries)
    onlyIf { System.getProperty("os.name").lowercase().contains("win") }
    from(layout.buildDirectory.dir("compose/binaries/main/app/Switchboard"))
    // zip 을 풀면 Switchboard 폴더 하나가 나오게 한다
    into("Switchboard")
    archiveFileName.set("Switchboard-windows.zip")
    destinationDirectory.set(layout.buildDirectory.dir("compose/binaries/main/zip"))
}

// 설치 파일을 만들기 전에 네이티브를 정리한다 (macOS 는 packageMacDmg 가 직접 순서를 잡는다)
tasks.matching { it.name in setOf("packageMsi", "packageExe", "packageDeb") }.configureEach {
    dependsOn(trimNativeLibraries)
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
    dependsOn("createDistributable", trimNativeLibraries)
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app")
    val stageDir = layout.buildDirectory.dir("mac-dmg/stage")
    val dmgFile = layout.buildDirectory.file("compose/binaries/main/dmg/$displayName-$appVersion.dmg")
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    outputs.file(dmgFile)
    // 서명 신원·공증 여부가 바뀌어도 결과 파일 이름은 같아서, 건너뛰지 않고 매번 새로 만든다
    inputs.property("signingIdentity", signingIdentity ?: "")
    outputs.upToDateWhen { false }
    doLast {
        fun run(vararg command: String) {
            val process = ProcessBuilder(*command).inheritIO().start()
            check(process.waitFor() == 0) { "${command.first()} 실패: ${command.joinToString(" ")}" }
        }
        /**
         * 심볼릭 링크를 따라가지 않고 지운다. 스테이지에는 /Applications 를 가리키는 링크가 있어서
         * File.deleteRecursively() 로 지우면 링크를 따라 들어가 실제 /Applications 의 앱을 지운다 (실제로 일어난 사고)
         */
        fun deleteTree(root: File) {
            val path = root.toPath()
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
            if (Files.isSymbolicLink(path)) {
                Files.delete(path)
                return
            }
            // Files.walk 는 FOLLOW_LINKS 를 주지 않으면 링크 안으로 들어가지 않고 링크 자체만 돌려준다
            Files.walk(path).use { stream ->
                stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) }
            }
        }
        val stage = stageDir.get().asFile.also { deleteTree(it); it.mkdirs() }
        val dmg = dmgFile.get().asFile.apply { parentFile.mkdirs(); delete() }
        val bundle = stage.resolve("$displayName.app")
        // ditto 는 확장 속성과 서명을 그대로 옮긴다
        fun exitCode(vararg command: String): Int = ProcessBuilder(*command).redirectErrorStream(true).start().waitFor()
        fun runIn(dir: File, vararg command: String) {
            val process = ProcessBuilder(*command).directory(dir).inheritIO().start()
            check(process.waitFor() == 0) { "${command.first()} 실패: ${command.joinToString(" ")}" }
        }

        /** Mach-O(맥 실행 파일)인지. 리눅스 .so 는 서명할 필요도 없고 되지도 않는다 */
        fun isMachO(file: File): Boolean = file.inputStream().use { stream ->
            val head = ByteArray(4)
            if (stream.read(head) != 4) return@use false
            val magic = ((head[0].toInt() and 0xFF) shl 24) or ((head[1].toInt() and 0xFF) shl 16) or
                ((head[2].toInt() and 0xFF) shl 8) or (head[3].toInt() and 0xFF)
            magic in setOf(0xFEEDFACE.toInt(), 0xFEEDFACF.toInt(), 0xCAFEBABE.toInt(), 0xCEFAEDFE.toInt(), 0xCFFAEDFE.toInt())
        }

        /**
         * jar 안의 맥 네이티브 라이브러리를 서명한다
         *
         * Compose 는 .dylib 과 .jnilib 만 서명해서 java-keyring 의 osxkeychain.so 같은 파일이 빠진다.
         * 서명되지 않은 Mach-O 가 하나라도 있으면 Apple 공증이 통째로 거절된다.
         */
        fun signNativeLibrariesInJars(appRoot: File, identity: String) {
            val jars = appRoot.resolve("Contents/app").listFiles { file -> file.name.endsWith(".jar") }?.sorted().orEmpty()
            val work = layout.buildDirectory.dir("mac-dmg/jar-sign").get().asFile.also { deleteTree(it); it.mkdirs() }
            var signed = 0
            for (jar in jars) {
                val entries = ZipFile(jar).use { zip ->
                    zip.entries().toList().map { it.name }.filter { it.endsWith(".so") }
                }
                for (entry in entries) {
                    runIn(work, "unzip", "-o", "-q", jar.absolutePath, entry)
                    val extracted = work.resolve(entry)
                    if (!isMachO(extracted)) continue
                    run("codesign", "--force", "--timestamp", "--sign", "Developer ID Application: $identity", extracted.absolutePath)
                    runIn(work, "zip", "-q", jar.absolutePath, entry)
                    signed++
                    logger.lifecycle("jar 안 네이티브 서명: ${jar.name} :: $entry")
                }
            }
            run {
                // trimNativeLibraries 와 위 서명으로 jar 가 바뀌었으니 앱 번들 서명을 다시 한다
                val entitlements = layout.buildDirectory.dir("compose/default-resources").get().asFile
                    .walkTopDown().firstOrNull { it.name == "default-entitlements.plist" }
                val command = mutableListOf("codesign", "--force", "--options", "runtime", "--timestamp")
                entitlements?.let { command += listOf("--entitlements", it.absolutePath) }
                command += listOf("--sign", "Developer ID Application: $identity", appRoot.absolutePath)
                run(*command.toTypedArray())
            }
        }
        val app = appDir.get().asFile.resolve("Switchboard.app")
        if (signingIdentity != null) signNativeLibrariesInJars(app, signingIdentity)
        // 서명했으면 공증 프로필이 있을 때 앱을 먼저 공증하고 티켓을 붙인다. 그래야 오프라인에서도 Gatekeeper 가 통과시킨다
        fun notaryCommand(vararg args: String): Array<String> =
            (listOf("xcrun", "notarytool") + args + listOf("--keychain-profile", notaryProfile) +
                (notaryKeychain?.let { listOf("--keychain", it) } ?: emptyList())).toTypedArray()
        val notarize = signingIdentity != null && exitCode(*notaryCommand("history")) == 0
        if (signingIdentity != null && !notarize) {
            logger.warn("공증 프로필 '$notaryProfile' 이 없어 서명만 해요. xcrun notarytool store-credentials $notaryProfile … 으로 만들면 공증까지 해요")
        }
        if (notarize) {
            val zip = stage.parentFile.resolve("Switchboard-notarize.zip").apply { delete() }
            run("ditto", "-c", "-k", "--keepParent", app.absolutePath, zip.absolutePath)
            logger.lifecycle("앱 공증 중… (몇 분 걸려요)")
            run(*notaryCommand("submit", zip.absolutePath, "--wait"))
            run("xcrun", "stapler", "staple", app.absolutePath)
        }
        run("ditto", app.absolutePath, bundle.absolutePath)
        run("codesign", "--verify", "--strict", "--deep", bundle.absolutePath)
        val applicationsLink = stage.resolve("Applications")
        run("ln", "-s", "/Applications", applicationsLink.absolutePath)
        try {
            run("hdiutil", "create", "-volname", displayName, "-srcfolder", stage.absolutePath, "-format", "UDZO", "-ov", dmg.absolutePath)
        } finally {
            // DMG 에 담았으면 링크는 바로 지운다. 빌드 폴더에 /Applications 링크를 남겨 두지 않는다
            Files.deleteIfExists(applicationsLink.toPath())
        }
        if (signingIdentity != null) {
            run("codesign", "--force", "--timestamp", "--sign", "Developer ID Application: $signingIdentity", dmg.absolutePath)
        }
        if (notarize) {
            logger.lifecycle("DMG 공증 중…")
            run(*notaryCommand("submit", dmg.absolutePath, "--wait"))
            run("xcrun", "stapler", "staple", dmg.absolutePath)
            run("spctl", "--assess", "--type", "open", "--context", "context:primary-signature", "--verbose", dmg.absolutePath)
        }
        logger.lifecycle(
            "완료: ${dmg.absolutePath} (" + when {
                notarize -> "서명·공증됨"
                signingIdentity != null -> "서명됨, 공증 안 됨"
                else -> "서명 안 됨"
            } + ")",
        )
    }
}
