plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // 앱 모듈 테스트가 UMC 저장소 픽스처(Fixtures)를 재사용한다
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    implementation(libs.json.schema.validator)
    implementation(libs.kotlin.logging)

    testFixturesImplementation(kotlin("test"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
