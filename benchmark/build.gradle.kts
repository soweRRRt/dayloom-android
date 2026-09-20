plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sowerrrt.dayloom.benchmark"
    compileSdk = 36
    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Emulator runs are useful for QA only; release metrics must come from physical hardware.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.espresso.core)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

tasks.matching { it.name == "connectedBenchmarkAndroidTest" }.configureEach {
    doFirst {
        check(providers.environmentVariable("DAYLOOM_ALLOW_DESTRUCTIVE_BENCHMARK").orNull == "true") {
            "Macrobenchmark replaces the target APK and may clear its app data. " +
                "Run it only on a disposable emulator/device with DAYLOOM_ALLOW_DESTRUCTIVE_BENCHMARK=true."
        }
    }
}
