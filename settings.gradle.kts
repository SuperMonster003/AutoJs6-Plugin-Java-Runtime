enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "autojs6-plugin-java-runtime"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    plugins {
        id("org.autojs.build.platform-versions") version "1.4.1"
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }
}

plugins {
    id("org.autojs.build.platform-versions")
    // Enable JDK auto-resolution/download capability for build modules.
    id("org.gradle.toolchains.foojay-resolver-convention")
}

includeBuild("build-logic")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")
