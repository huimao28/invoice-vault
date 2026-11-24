import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.kapt") }

val signingPropertiesFile = sequenceOf(
    System.getenv("INVOICE_VAULT_SIGNING_PROPERTIES")?.let(::file),
    file("/var/minis/shared/invoice-vault-signing/signing.properties"),
    File(System.getProperty("user.home"), ".invoice-vault/signing.properties")
).filterNotNull().firstOrNull { it.isFile }
val releaseSigning = Properties().apply { signingPropertiesFile?.inputStream()?.use(::load) }

android {
 namespace = "com.huimaotech.invoicevault"; compileSdk = 34; buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "com.huimaotech.invoicevault"; minSdk = 26; targetSdk = 37; versionCode = 17; versionName = "1.0.1"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
 signingConfigs {
  if (signingPropertiesFile != null) create("release") {
   storeFile = file(releaseSigning.getProperty("storeFile"))
   storePassword = releaseSigning.getProperty("storePassword")
   keyAlias = releaseSigning.getProperty("keyAlias")
   keyPassword = releaseSigning.getProperty("keyPassword")
  }
 }
 buildTypes {
  getByName("release") {
   isMinifyEnabled = true
   isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
   if (signingPropertiesFile != null) signingConfig = signingConfigs.getByName("release")
  }
 }
 buildFeatures { compose = true; buildConfig = true }
 lint { checkReleaseBuilds = false }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
 kotlinOptions { jvmTarget = "17" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.navigation:navigation-compose:2.8.5")
 implementation("androidx.core:core-ktx:1.13.1")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
 implementation("androidx.room:room-runtime:2.7.2")
 implementation("androidx.room:room-ktx:2.7.2")
 kapt("androidx.room:room-compiler:2.7.2")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("com.google.code.gson:gson:2.11.0")
 testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
 // Bundled Chinese OCR model: works fully offline and supports minSdk 21+.
 implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
 implementation("com.tom-roush:pdfbox-android:2.0.27.0")
 testImplementation("junit:junit:4.13.2")
}
kapt { correctErrorTypes = true }
