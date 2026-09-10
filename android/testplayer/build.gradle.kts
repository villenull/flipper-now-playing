plugins { id("com.android.application"); kotlin("android") }
android {
 namespace = "io.github.flippernowplaying.testplayer"; compileSdk = 36
 defaultConfig { applicationId = "io.github.flippernowplaying.testplayer"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
}
