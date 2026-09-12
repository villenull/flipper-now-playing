plugins { id("com.android.application"); kotlin("android") }
android {
 namespace = "io.github.flippernowplaying.bridge"
 compileSdk = 36
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "io.github.flippernowplaying.bridge"; minSdk = 26; targetSdk = 36; versionCode = 3; versionName = "0.3"; testInstrumentationRunner = "io.github.flippernowplaying.bridge.ArtworkInstrumentation" }
 // Supply the existing development keystore for upgrade-compatible distribution; never commit it.
 System.getenv("NP_DEBUG_KEYSTORE")?.let { path ->
  signingConfigs.getByName("debug").storeFile = file(path)
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core")); testImplementation(kotlin("test")) }
