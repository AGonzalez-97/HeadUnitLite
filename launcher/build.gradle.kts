// v0.55.0: herramientas al día con las de CarLink (ADR-0012). AGP 9 compila Kotlin por su cuenta
// ("Kotlin integrado"): ya no se aplica el plugin org.jetbrains.kotlin.android. El plugin de Compose
// fija la versión de Kotlin (2.2.10).
plugins {
    id("com.android.application") version "9.3.0" apply false
    // v0.56.0: los módulos del motor de CarLink son librerías (y usan gradle/libs.versions.toml)
    id("com.android.library") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}


// ADR-0013 (v0.68.0): los motores de CarLink compilan su código nativo (CMake / ndk-build) solo para
// las arquitecturas del APK (por defecto armeabi-v7a, la de la radio). Antes se compilaba para 3–4
// y la app descartaba el resto al empaquetar: minutos de compilación sin uso. Se hace desde acá
// para no tocar el código de terceros (vendor/).
val onixAbis = (providers.gradleProperty("onix.abis").orNull ?: "armeabi-v7a").split(",").map { it.trim() }.toSet()
subprojects {
    plugins.withId("com.android.library") {
        extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
            finalizeDsl { android ->
                val abis = android.defaultConfig.ndk.abiFilters
                if (abis.isNotEmpty()) abis.retainAll(onixAbis)
            }
        }
    }
}

// AUD-059 (v0.64.1): el lint completo (lint.yml) no terminaba en 90 min analizando también el motor
// de CarLink (código de terceros, que no se modifica). Solo en ese workflow (-Ponix.lintSoloApp=true)
// se apaga el análisis de lint de los módulos del motor. La compilación del APK no cambia.
if (providers.gradleProperty("onix.lintSoloApp").orNull == "true") {
    subprojects {
        if (path != ":app") {
            tasks.matching { it.name.startsWith("lintAnalyze") }.configureEach { enabled = false }
        }
    }
}
