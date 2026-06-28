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
        // 1.0.0 (versionCode 3) — all three human gates passed on-device (Gate 3 signed off
        // 2026-06-23); feature-complete, parity-verified release build. 1.0.1 (versionCode 4) is a
        // packaging-only bump so the release tag carries the fastlane/ metadata F-Droid reads from
        // the built commit (the 1.0.0 tag predated it); no app behaviour changed.
        // 1.0.2 (tag v1.0.2) was cut WITHOUT bumping these — the in-app version drifted behind the tag.
        // 1.0.3 (versionCode 5) realigns the version ahead of the latest tag and ships the D-098
        // rule-editor Save/Cancel fix. 1.0.4 (versionCode 6) ships the D-100 main-window nav-bar inset
        // fix (DraftApplyBar Apply/Discard + Menu "Recheck Permissions" clipped under a button/3-key
        // nav bar). 1.1.0 (versionCode 7) bumps targetSdk to 36 (Android 16). 1.1.1 (versionCode 8) is a
        // security-hardening patch: notification/widget PendingIntents are made un-missably explicit
        // (D-107, CodeQL java/android/implicit-pendingintents). 1.2.0 (versionCode 9) ships runtime
        // bug fixes + UX: D-108 service-start battery-saver flash, D-109 PWM-sensitive read-out tracks
        // perceived brightness, D-110 circadian stale-location fallback + staleness hints, D-111 golden
        // "resume context automation" banner + Tasker-style sticky Load/Save/Contexts action bar;
        // plus the GitHub Actions Node-24 migration (CI only).
        //   SEMVER — why MINOR (1.1.1 → 1.2.0), not a patch (would have been 1.1.2): the three core
        //   defect fixes (D-108/109/110a) are patch-grade on their own, BUT the same release ADDS new
        //   user-facing surfaces — the circadian staleness hints on the Circadian screen + dashboard
        //   (D-110) and the redesigned Profiles & Contexts screen (sticky action bar + load/save/contexts
        //   modals, D-111). RUNBOOK §6: a new user-facing feature/surface is a MINOR, and when a release
        //   spans categories you pick the HIGHEST that applies — so new-surface (minor) wins over
        //   bug-fix (patch). No settings-schema break (round-trips), so it is not a major. RULE: build
        //   version must be ≥ the latest `v*` tag on main and versionCode strictly greater than every
        //   released code — see RUNBOOK "Cutting a release".
        // 1.2.1 (versionCode 10) is a PATCH re-cut: the v1.2.0 release workflow was silently skipped (a
        // stray `[skip ci]` token in the squash-merge commit body suppressed every workflow for that
        // commit + tag — D-115), so v1.2.0 never published its signed APK. v1.2.1 carries the release.yml
        // fix (now triggers on `release: published`, immune to skip-ci) and re-cuts the SAME app — no
        // app/runtime code changed since 1.2.0, so it is a patch.
        versionCode = 10
        versionName = "1.2.1"
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
