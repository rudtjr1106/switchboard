plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    group = "io.github.rudtjr1106.switchboard"
    version = providers.gradleProperty("switchboard.version").get()
}
