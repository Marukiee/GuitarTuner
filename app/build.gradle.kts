import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing.
 *
 * The keystore never lives in this repository. It is resolved, in order, from
 *  1. environment variables (what CI uses, after decoding ANDROID_KEYSTORE_B64 to a file), or
 *  2. keystore.properties in the project root (git-ignored, what a local release build uses).
 *
 * Both paths must end up using the *same* key, because Android identifies an app by its signing
 * certificate: change the key and every install has to be uninstalled before it can update.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName) ?: keystoreProperties.getProperty(propName)

val keystorePath = signingValue("GT_KEYSTORE_FILE", "storeFile")
val hasSigningConfig = keystorePath != null && file(keystorePath).exists()

android {
    namespace = "nl.markmaaktmedia.guitartuner"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.markmaaktmedia.guitartuner"
        minSdk = 26
        targetSdk = 36
        // CI sets this to the workflow run number, which is also the release tag, which is what
        // the in-app update check compares against. Local builds fall back to 1.
        versionCode = (System.getenv("GT_VERSION_CODE") ?: "1").toInt()
        versionName = "1.0.$versionCode"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = signingValue("GT_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("GT_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("GT_KEY_PASSWORD", "keyPassword")
                // minSdk 26 means v1 JAR signing is dead weight. v3 is worth having on top of
                // v2 because it is the scheme that supports signing key rotation later on.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            // Strong skipping + stability inference make the 20 Hz pitch flow cheap to render.
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            )
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.VERSION_NAME is shown on the Settings page.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
