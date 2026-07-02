plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tideo.autobrightness"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.tideo.autobrightness"
        minSdk = 31
        targetSdk = 36
        // INVARIANT when bumping these: versionName ≥ the latest `v*` tag, versionCode strictly greater
        // than every released code (F-Droid rejects a re-used code), bumped by semver per the *nature* of
        // the change — RUNBOOK §6 "Cutting a release". `release-preflight.yml` (D-124) enforces this on PRs.
        // Per-version history is NOT kept here — see the STATE.md Changelog, DEVIATIONS_LEDGER, and
        // fastlane/.../changelogs/<versionCode>.txt.
        versionCode = 16
        versionName = "1.6.2"
        manifestPlaceholders["appLabel"] = "Tideo Auto Brightness"
    }

    // Release signing is driven entirely by environment variables so that no
    // keystore material ever lives in the repo. In CI (release-signing.yml) the
    // keystore is base64-decoded from the ANDROID_KEYSTORE secret to the path in
    // ANDROID_KEYSTORE_FILE. Locally, with the env unset, the release build is
    // left unsigned (debug builds are unaffected).
    val keystoreFile = System.getenv("ANDROID_KEYSTORE_FILE")
    val hasReleaseSigning = !keystoreFile.isNullOrBlank() && file(keystoreFile).exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        getByName("debug") {
            // Distinct applicationId so a debug build installs alongside the stable signed
            // release without clobbering its data (profiles, context rules). The Shizuku
            // provider authority is ${applicationId}.shizuku in the manifest, so it follows
            // the suffix and the two packages don't conflict on install.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Tideo AB (Debug)"
        }
    }

    // F-Droid reproducibility (D-137): AGP by default embeds a Google-Play-encrypted dependency
    // metadata blob in the APK signing block — unreadable by anyone but Play (useless for an
    // F-Droid app) and the standard obstacle to byte-identical rebuilds. Behavior-neutral.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // S12.9a: lint is a build gate now — no baseline; targeted suppressions live in app/lint.xml.
        abortOnError = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

// S14: unit tests run on the DEBUG variant only. The acceptance ladder is `:app:testDebugUnitTest`; the
// Compose UI-test harness (`compose-ui-test-manifest`) is `debugImplementation` (its test ComponentActivity
// is absent in release → "Unable to resolve activity"); and release unit tests re-run identical sources
// with no added coverage. Disabling them keeps `./gradlew build` green + meaningful (it still assembles +
// lints the release variant).
androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = false
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":platform"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose UI smoke test runs under Robolectric (S11): createComposeRule + node assertions.
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
