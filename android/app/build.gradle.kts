plugins { id("com.android.application"); kotlin("android") }
android {
 namespace = "io.github.flippernowplaying.bridge"
 compileSdk = 36
 buildToolsVersion = "35.0.0"
 defaultConfig { applicationId = "io.github.flippernowplaying.bridge"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation(project(":core")); testImplementation(kotlin("test")) }
