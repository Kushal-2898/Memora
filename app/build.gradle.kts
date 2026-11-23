import java.util.Properties
import java.io.FileInputStream

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.google.services)
  alias(libs.plugins.google.firebase.crashlytics)
  alias(libs.plugins.secrets)
}

android {
  namespace = "app.memora"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "app.memora"
    minSdk = 24
    targetSdk = 36
    versionCode = 24
    versionName = "1.0.23"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val localProps = Properties().apply {
        val propFile = rootProject.file("local.properties")
        if (propFile.exists()) {
          FileInputStream(propFile).use { load(it) }
        }
      }
      val keystorePath = System.getenv("KEYSTORE_PATH")
        ?: localProps.getProperty("KEYSTORE_PATH")
        ?: "${rootDir}/memora-release-key.jks"
      val resolvedFile = file(keystorePath).let { if (it.isAbsolute) it else file("${rootDir}/$keystorePath") }
      storeFile = resolvedFile
      storePassword = System.getenv("STORE_PASSWORD")
        ?: localProps.getProperty("STORE_PASSWORD")
        ?: ""
      keyAlias = System.getenv("KEY_ALIAS")
        ?: localProps.getProperty("KEY_ALIAS")
        ?: "memora_upload"
      keyPassword = System.getenv("KEY_PASSWORD")
        ?: localProps.getProperty("KEY_PASSWORD")
        ?: ""
    }
  }

  buildTypes {
    release {
      buildConfigField("boolean", "MEMORA_DIAGNOSTICS", "false")
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      buildConfigField("boolean", "MEMORA_DIAGNOSTICS", "true")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      all {
        it.jvmArgs("-XX:+UseSerialGC", "-Xms32m", "-Xmx192m")
      }
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

ksp {
  // Directs the Room KSP compiler to write the versioned schema JSON to app/schemas/.
  // This directory MUST be committed to version control. It is required for Room AutoMigration
  // support and safe manual migrations in future schema versions.
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {

  implementation(libs.generativeai)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.analytics)
  implementation(libs.firebase.crashlytics)
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation("com.google.mlkit:text-recognition:16.0.0")
  implementation("com.google.mlkit:barcode-scanning:17.3.0")
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.functions)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.timber)
  implementation(libs.billing.ktx)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation("androidx.work:work-testing:2.9.0")
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // "ksp"(libs.moshi.kotlin.codegen)
}



