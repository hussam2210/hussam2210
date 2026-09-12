plugins { id("com.android.application") }

android {
    namespace = "com.drpixel.haneen"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.drpixel.haneen"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "3.2-field"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
}
