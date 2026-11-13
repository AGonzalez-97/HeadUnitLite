pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libsu (ayudante root del motor de Android Auto) solo está en JitPack
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.topjohnwu.libsu") }
        }
    }
}
rootProject.name = "onix-launcher"
include(":app")

// v0.56.0 (ADR-0012): Android Auto y CarPlay con el motor de CarLink (AGPL-3.0), copiado de
// F-e-n-y-x/CarLink 1.0.9 (código del 2026-11-04; antes 1.0.7, commit cfba714). Ver vendor/CARLINK_INTEGRATION.md.
include(":carlink-core")
include(":carplay-shared");       project(":carplay-shared").projectDir = file("vendor/diplay/shared")
include(":carplay-common");       project(":carplay-common").projectDir = file("vendor/diplay/common")
include(":androidauto");          project(":androidauto").projectDir = file("vendor/diauto/app")
include(":androidauto-contract"); project(":androidauto-contract").projectDir = file("vendor/diauto/contract")
