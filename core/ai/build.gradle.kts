plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:config"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    implementation(libs.llama)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlin.logging)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    // 통합 테스트가 남기는 로드 시간·tok/s 를 보려고. 없으면 kotlin-logging 이 NOP 로 떨어진다
    testRuntimeOnly(libs.logback.classic)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// 받아 둔 모델로 하네스 평가를 돌려 build/reports/ai-eval/ 에 점수를 남긴다: ./gradlew :core:ai:aiEval
val aiEval by tasks.registering(Test::class) {
    group = "verification"
    description = "온디바이스 AI 하네스 평가 (실제 모델 필요)"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("*HarnessEvalTest*") }
    environment("SWITCHBOARD_AI_EVAL", "1")
    outputs.upToDateWhen { false }
    testLogging { showStandardStreams = true }
    maxHeapSize = "2g"
}
