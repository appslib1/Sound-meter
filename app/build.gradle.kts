plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.soundmeter.decibel.noisedetector"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.soundmeter.decibel.noisedetector"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Real AdMob IDs — release only.
            resValue("string", "app_id", "ca-app-pub-5592679724436623~5455293576")
            resValue("string", "banner", "ca-app-pub-5592679724436623/8931504156")
        }
        debug {
            // Google official test IDs — never bill the real account in dev.
            resValue("string", "app_id", "ca-app-pub-3940256099942544~3347511713")
            resValue("string", "banner", "ca-app-pub-3940256099942544/6300978111")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.mpandroidchart)
    implementation(libs.play.services.ads)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}