import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ksp)
}

val localProperties = Properties()
val localPropertiesFile = project.file("../local.properties")
if (localPropertiesFile.exists()) {
  localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun getLocalProperty(
  key: String,
  defaultValue: String,
): String = localProperties.getProperty(key, defaultValue)

android {
  namespace = "de.curlybracket.grocery"
  compileSdk = 37

  defaultConfig {
    applicationId = "de.curlybracket.grocery"
    minSdk = 35
    targetSdk = 37
    versionCode = 1
    versionName = "0.1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "SUPABASE_URL", "\"${getLocalProperty("supabase.url", "")}\"")
    buildConfigField("String", "SUPABASE_ANON_KEY", "\"${getLocalProperty("supabase.anon.key", "")}\"")
    buildConfigField("String", "POWERSYNC_URL", "\"${getLocalProperty("powersync.url", "")}\"")
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  // Compose
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.bundles.compose)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling)
  implementation(libs.androidx.compose.material.icons.extended)

  // Navigation
  implementation(libs.androidx.navigation.compose)

  // Hilt
  implementation(libs.hilt.android)
  implementation(libs.hilt.navigation.compose)
  ksp(libs.hilt.compiler)

  // PowerSync
  implementation(libs.powersync.core)
  implementation(libs.powersync.connector.supabase)
  implementation(libs.powersync.compose)

  // Supabase
  implementation(libs.supabase.kt)
  implementation(libs.supabase.auth)
  implementation(libs.supabase.postgrest)

  // CameraX
  implementation(libs.bundles.camerax)

  // ML Kit
  implementation(libs.mlkit.barcode.scanning)

  // Coroutines & Kotlin
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)

  // Ktor Client
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.json)
  implementation(libs.ktor.client.android)

  // AndroidX Core
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.service)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)

  // Logging & utils
  implementation(libs.kermit)
  implementation(libs.uuid)

  // Coil
  implementation(libs.coil.compose)

  // Permissions
  implementation(libs.accompanist.permissions)

  // Testing
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}
