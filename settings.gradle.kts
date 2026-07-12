pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "flai"

include(":core")
include(":cli")
