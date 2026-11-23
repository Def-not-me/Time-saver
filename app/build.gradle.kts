plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "pl.porasnu"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.porasnu"
        minSdk = 26
        targetSdk = 34
        // Numer kolejnego budowania z GitHub Actions -> każda wersja jest "nowsza"
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.$run"
    }

    // Stały klucz (jawny, tylko do wersji testowych), żeby aktualizacje
    // instalowały się na poprzednią wersję bez konfliktu podpisu.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
