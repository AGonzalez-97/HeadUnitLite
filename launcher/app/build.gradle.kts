import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // AUD-083 (v0.79.5): se aplica acá para que la extensión de JaCoCo ya exista cuando se configuran
    // los tests (más abajo). Si no, findByType daba null y no se medían las clases que carga Robolectric.
    jacoco
}

// ADR-0013 (v0.68.0): arquitecturas del APK (por defecto solo la de la radio)
val onixAbis = (providers.gradleProperty("onix.abis").orNull ?: "armeabi-v7a").split(",").map { it.trim() }

// Firma fija (repo privado): permite actualizar la app sin desinstalar
val keystoreProps = Properties().apply {
    val f = rootProject.file("app/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Versión SemVer: única fuente en version.properties (AUD-020)
val appVersion: String = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}.getProperty("version")

// Claves para compilar en la PC: la CI las recibe de GitHub Secrets como variables de entorno; en
// la PC se leen de secrets.properties (raíz del proyecto, en .gitignore, nunca va al repositorio).
// La variable de entorno gana; sin ninguna de las dos la clave queda vacía, igual que antes.
val localSecrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun buildSecret(name: String): String =
    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() } ?: localSecrets.getProperty(name, "")

// v0.57.0 (ADR-0012): identidad de CarPlay. La CI la arma desde GitHub Secrets en una carpeta con
// offline-mfi/identity.pk8 y offline-mfi/certificate.p7b; nunca está en el repositorio (.gitignore).
// En la PC, sin la variable, se usa carplay-auth/ si trae offline-mfi/identity.pk8 (también ignorada).
val carPlayAuthAssets = providers.environmentVariable("DIPLAY_AUTH_ASSETS_DIR").orNull?.takeIf { it.isNotBlank() }
    ?.let { file(it).canonicalFile }
    ?: rootProject.file("carplay-auth").takeIf { it.resolve("offline-mfi/identity.pk8").isFile }

// Cada compilación de GitHub sube el versionCode (necesario para actualizar encima)
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.axel.onixlauncher"
    // v0.55.0: compileSdk 37 (lo piden las librerías de CarLink y Compose 2026). targetSdk sigue en 34:
    // no cambia nada en la radio (Android 7.1) y evita cambios de comportamiento en celulares nuevos.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // v0.68.0 (ADR-0013): solo 32 bits (armeabi-v7a), lo único que corre la radio (Allwinner T3,
        // Cortex-A7). Las librerías de 64 bits (MapLibre, Conscrypt, FFmpeg, motores) pesaban ~16 MB
        // sin usarse. Para un celular solo de 64 bits: -Ponix.abis=armeabi-v7a,arm64-v8a
        ndk { abiFilters += onixAbis }
        applicationId = "com.axel.onixlauncher"
        // v0.55.0: Android 7.1 (API 25), lo mínimo de CarLink; la radio del autor tiene 7.1.1
        minSdk = 25
        // Motor de Android Auto (DiAuto): usa su variante "github" (ADR-0012)
        missingDimensionStrategy("distribution", "github")
        targetSdk = 34
        versionCode = 100 + buildNumber
        versionName = appVersion
        buildConfigField("int", "BUILD_NUMBER", buildNumber.toString())
        // v0.75.0: clave de TomTom del autor (búsqueda y rutas). Viene del GitHub Secret TOMTOM_API_KEY
        // al compilar; nunca está en el repositorio. Sin el secreto queda vacía y el buscador lo avisa.
        val tomtomKey = buildSecret("TOMTOM_API_KEY").trim().replace("\"", "")
        buildConfigField("String", "TOMTOM_KEY", "\"$tomtomKey\"")
        // v0.76.0: canal privado de ntfy para mandar destinos desde el celular (GitHub Secret NTFY_TOPIC)
        val ntfyTopic = buildSecret("NTFY_TOPIC").trim().replace("\"", "")
        buildConfigField("String", "NTFY_TOPIC", "\"$ntfyTopic\"")
        // v0.79.6: Client ID de Spotify del autor (GitHub Secret SPOTIFY_CLIENT_ID), para no pegarlo a mano
        // en la radio. No es un secreto de verdad (PKCE no usa "client secret" y el Client ID va en el
        // enlace de permiso), pero se carga igual que la clave de TomTom: fuera del repositorio. Solo
        // letras y números. Sin el secreto queda vacío y la app lo pide como siempre.
        val spotifyClientId = buildSecret("SPOTIFY_CLIENT_ID").replace(Regex("[^A-Za-z0-9]"), "")
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")
        val updaterReadToken = buildSecret("UPDATER_READ_TOKEN").trim().replace("\"", "")
        buildConfigField("String", "UPDATER_READ_TOKEN", "\"$updaterReadToken\"")
    }

    carPlayAuthAssets?.let { sourceSets.getByName("main").assets.directories.add(it.path) }

    signingConfigs {
        create("release") {
            // Origen de la firma (v0.38.1): primero variables de entorno (GitHub Secrets, ver
            // docs/SIGNING.md); si no están, app/keystore.properties como hasta ahora. Misma clave
            // en los dos casos, así las instalaciones existentes se actualizan encima.
            fun secret(env: String, prop: String, def: String? = null): String? =
                System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop, def)
            storeFile = file(secret("ONIX_STORE_FILE", "storeFile", "release.jks")!!)
            storePassword = secret("ONIX_STORE_PASSWORD", "storePassword")
            keyAlias = secret("ONIX_KEY_ALIAS", "keyAlias")
            keyPassword = secret("ONIX_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        // v0.63.0: cobertura de los tests de lógica (JaCoCo), informada en tests.txt
        debug {
            enableUnitTestCoverage = true
        }
        release {
            // Optimización completa: código más chico y más rápido en el head unit
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // La app no se publica en Google Play: el aviso de targetSdk viejo (fatal en la compilación
        // release desde AGP 9) no aplica. targetSdk se mantiene en 34 a propósito (ver arriba).
        disable += "ExpiredTargetSdkVersion"
        // v0.60.1: NewApi (usar algo que Android 7.1 no tiene) frena la compilación release: así se
        // revisa en cada build con el lint rápido, aunque el lint completo corra aparte (lint.yml)
        fatal += "NewApi"
        // AUD-047 (v0.59.0): lint revisa solo el código de Onix, no el del motor de CarLink
        // (~368.000 líneas de terceros): con él, el paso pasaba los 15 min y se cortaba sin reporte
        checkDependencies = false
        // Igual que CarLink: el certificado público de Android Auto del motor (ADR-0012)
        disable += "PackagedPrivateKey"
    }
    // AUD-071/AUD-113 (v0.79.5): recursos Java que ningún código del APK lee. R8 ya quita sus
    // clases (0 clases de org.bouncycastle.pqc y .x509 en el DEX), pero nunca quita los recursos:
    // unos 1,27 MB medidos sobre el APK del build 146. No se toca vendor/.
    packaging {
        resources.excludes += listOf(
            "org/bouncycastle/pqc/**",                                  // tablas de Picnic (1,21 MB), de bcprov vía DiPlay
            "org/bouncycastle/x509/CertPathReviewerMessages*.properties",
            "google/protobuf/*.proto",                                  // fuentes .proto; el runtime no las lee
            "DebugProbesKt.bin",                                        // agente de depuración de corrutinas
        )
    }
    // AUD-086 (v0.79.7): resources.arsc traía 88 idiomas de las librerías (AppCompat, Material, Media3, los motores…)
    // y la app solo está en castellano. Se dejan castellano e inglés (el inglés es el respaldo de las pantallas de los
    // motores si el sistema estuviera en otro idioma). Lo sin traducir (values/) siempre queda.
    androidResources {
        localeFilters += listOf("es", "en")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }
}

// Los tests corren en la JVM con Robolectric, que trae su propio Conscrypt; el de Android (código
// nativo de ARM) no carga ahí y rompía los tests con UnsatisfiedLinkError (build 66).
configurations.matching { it.name.endsWith("UnitTestRuntimeClasspath") }.configureEach {
    exclude(group = "org.conscrypt", module = "conscrypt-android")
}

dependencies {
    // v0.55.0: Compose de 2026 (la misma que usa CarLink)
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // v0.53.0: instala los perfiles de arranque que traen Compose y las demás librerías (APK instalado a mano)
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    // Media3 1.11.1 (v0.56.0): la misma que usa el motor de CarPlay; todas las piezas de Media3
    // tienen que ir en la misma versión
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    // Algunas radios publican el audio en HLS (.m3u8)
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-datasource:1.11.1")
    implementation("androidx.annotation:annotation-experimental:1.4.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // TLS 1.3 y curvas modernas en Android 7 (rutas OSRM fallaban con "Handshake failed", v0.43.0)
    implementation("org.conscrypt:conscrypt-android:2.8.0")   // v0.74.0: la misma que usa el motor de CarLink 1.0.9
    // Mapa 3D (v0.47.0): MapLibre 10.x, el último con OpenGL ES 2.0 (la radio no tiene 3.0)
    implementation("org.maplibre.gl:android-sdk:10.3.7")
    // v0.56.0 (ADR-0012): Android Auto y CarPlay (motor de CarLink, AGPL-3.0); trae los dos motores
    implementation(project(":carlink-core"))

    // Capturas reales de la pantalla, renderizadas en la compilación
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.20.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.20.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// v0.63.0: los tests con Robolectric cargan las clases con su propio cargador; sin esto JaCoCo no las cuenta
tasks.withType<Test>().configureEach {
    extensions.findByType(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class.java)?.apply {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}
