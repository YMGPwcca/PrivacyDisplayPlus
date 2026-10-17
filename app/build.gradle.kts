plugins { id("com.android.application") }

android {
    namespace = "ymg.pwcca.privacydisplayplus"
    compileSdk = 36
    defaultConfig {
        applicationId = "ymg.pwcca.privacydisplayplus"
        minSdk = 36
        targetSdk = 36
        versionCode = 19
        versionName = "1.0.0"
        testInstrumentationRunner = "ymg.pwcca.privacydisplayplus.DeviceControlProbe"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
    compileOnly("de.robv.android.xposed:api:82")
    testImplementation("junit:junit:4.13.2")
}
