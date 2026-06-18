plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.utopiaxc.tts2"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    val verName = System.getenv("VERSION_NAME") ?: "1.0"
    val verCode = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.utopiaxc.tts2"
        minSdk = 24
        targetSdk = 36
        versionCode = verCode
        versionName = verName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH") ?: "upload-keystore.jks"
            val keystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD") ?: ""
            val keyAlias = System.getenv("ANDROID_KEY_ALIAS") ?: ""
            val keyPassword = System.getenv("ANDROID_KEY_PASSWORD").takeIf { !it.isNullOrEmpty() } ?: keystorePassword

            storeFile = file(keystorePath)
            storePassword = keystorePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.azure.speech)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}