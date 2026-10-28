import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    id("com.android.application")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

/**
 * The version is the highest `vX.Y.Z[.N]` tag reachable from HEAD, so a release is just a tag.
 * Every merged tag is compared rather than trusting `git describe`, which picks the nearest tag
 * and can land on an older one. A three-part tag counts as `.0`; a tag that doesn't parse is
 * skipped. The code is derived from the four numbers so it always increases.
 */
val version4: List<Int> = providers.exec {
    commandLine("git", "tag", "--merged", "HEAD", "--list", "v*")
    isIgnoreExitValue = true
}.standardOutput.asText.get().lines()
    .mapNotNull { tag ->
        val parts = tag.trim().removePrefix("v").split('.')
        val numbers = parts.mapNotNull { it.toIntOrNull()?.takeIf { n -> n >= 0 } }
        if (parts.size > 4 || numbers.size != parts.size) null
        else numbers + List(4 - numbers.size) { 0 }
    }
    .maxWithOrNull { a, b -> a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0 }
    ?: listOf(0, 1, 0, 0)

/**
 * Release signing, from environment variables (CI) or an env-style file named by the
 * `couch.signing` Gradle property (local). Without either, release builds fall back to the
 * debug key so a fresh checkout still builds.
 */
val signing: Map<String, String> = run {
    val fromEnv = System.getenv("COUCH_KEYSTORE")?.let {
        mapOf(
            "store" to it,
            "storePassword" to System.getenv("COUCH_KEYSTORE_PASSWORD").orEmpty(),
            "alias" to System.getenv("COUCH_KEY_ALIAS").orEmpty(),
            "keyPassword" to System.getenv("COUCH_KEY_PASSWORD").orEmpty(),
        )
    }
    fromEnv ?: providers.gradleProperty("couch.signing").orNull?.let { path ->
        val props = Properties().apply { File(path).inputStream().use { load(it) } }
        mapOf(
            "store" to props.getProperty("COUCH_KEYSTORE"),
            "storePassword" to props.getProperty("COUCH_KEYSTORE_PASSWORD"),
            "alias" to props.getProperty("COUCH_KEY_ALIAS"),
            "keyPassword" to props.getProperty("COUCH_KEY_PASSWORD"),
        )
    } ?: emptyMap()
}

android {
    namespace = "cloud.samlo.couch"
    compileSdk = 36

    defaultConfig {
        applicationId = "tv.kevbox.home"
        // Fire OS 7 (Android 9) is the oldest target.
        minSdk = 28
        targetSdk = 36
        versionCode = (version4[0] * 1_000_000 + version4[1] * 10_000 + version4[2] * 100 +
            version4[3]).coerceAtLeast(1)
        versionName = version4.joinToString(".")
    }

    // English only: values-fr stays in the source untouched (AC-38) but never ships.
    androidResources {
        localeFilters += "en"
    }

    signingConfigs {
        if (signing.isNotEmpty()) {
            create("release") {
                storeFile = file(signing.getValue("store"))
                storePassword = signing.getValue("storePassword")
                keyAlias = signing.getValue("alias")
                keyPassword = signing.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        // Installs beside the release build, so a dev build never replaces the KevBox Home in use.
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    // Kotlin's builtins metadata only serves kotlin-reflect, which Couch doesn't use.
    packaging {
        resources {
            excludes += "kotlin/**"
        }
    }

    // values-fr is Couch's untouched copy (AC-38) and the locale filter keeps it out of the APK.
    // Without this, strings deleted from values/ fail the build as ExtraTranslation (fatal), and
    // new English strings raise MissingTranslation. Plurals deleted from values/ fail as
    // MissingDefaultResource (an error) instead of ExtraTranslation.
    lint {
        disable += setOf("ExtraTranslation", "MissingTranslation", "MissingDefaultResource")
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // The android.jar used by JVM tests only has stubs for org.json.
    testImplementation("org.json:json:20240303")
}

// Release is the variant that ships, so its unit tests must run too (AGP skips them by default).
androidComponents {
    beforeVariants { variant ->
        variant.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}
