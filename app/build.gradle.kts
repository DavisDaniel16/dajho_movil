import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        FileInputStream(localPropertiesFile).use { load(it) }
    }
}
val supabaseUrl = localProperties.getProperty("SUPABASE_URL") ?: ""
val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""

check(supabaseUrl.startsWith("https://")) { "Configura SUPABASE_URL en local.properties" }
check(supabaseAnonKey.isNotBlank()) { "Configura SUPABASE_ANON_KEY en local.properties" }

android {
    namespace = "com.example.dajho"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.dajho"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "TERMS_URL", "\"${localProperties.getProperty("TERMS_URL", "")}\"")
        buildConfigField("String", "PRIVACY_URL", "\"${localProperties.getProperty("PRIVACY_URL", "")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        // AndroidX b/501504696: cyclic UI callbacks make this detector expand recursively.
        // There are no LiveData.observe, OnBackPressedDispatcher.addCallback or addMenuProvider calls.
        // View lifecycle ownership is used explicitly and recreation is covered by instrumentation.
        disable += setOf("FragmentLiveDataObserve", "FragmentBackPressedCallback", "FragmentAddMenuProvider")
    }
}

dependencies {
    // Base XML & UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.activity.ktx)
    implementation("androidx.biometric:biometric:1.1.0")

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Corrutinas
    implementation(libs.kotlinx.coroutines.android)

    // Supabase y Ktor
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.gotrue)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
