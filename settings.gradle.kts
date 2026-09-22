pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    // JDK 를 자동으로 내려받아 빌드 환경을 맞춘다 (jvmToolchain)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "switchboard"

include(":core:config")
include(":core:github")
include(":core:ai")
include(":core:scanner")
include(":app")
