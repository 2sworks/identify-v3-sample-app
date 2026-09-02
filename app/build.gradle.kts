import java.util.Properties

plugins {
    // AGP 9.0+ Kotlin desteğini yerleşik olarak sağlıyor — ayrı bir org.jetbrains.kotlin.android
    // plugin'ine gerek yok.
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// settings.gradle.kts'teki repository erişim bilgileriyle aynı secret-yükleme konvansiyonu:
// önce env değişkeni (CI), sonra local.properties (lokal geliştirme, gitignored). Asla hardcode edilmez.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(envKey: String, propKey: String): String =
    System.getenv(envKey) ?: localProperties.getProperty(propKey) ?: ""

val identifySdkVersion = (project.findProperty("identify.sdk.version") as? String) ?: "3.4.5-navfix1"

android {
    namespace = "com.identify.sample"
    compileSdk = 36

    defaultConfig {
        // D:\IdentifySdkV3\app'in applicationId'sinden (o da "com.identify.sample") farklı, ki
        // ikisi de aynı test cihazında bir versiyon/imza çakışması olmadan yan yana kurulabilsin.
        // `namespace` "com.identify.sample" olarak kalıyor — R/BuildConfig codegen paketi
        // etkilenmiyor, kaynak kodda değişiklik gerekmiyor.
        applicationId = "com.identify.samplev3.standalone"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "IDENTIFY_TURN_KEY", "\"${secret("IDENTIFY_TURN_KEY", "identify.turnKey")}\"")
        buildConfigField("String", "IDENTIFY_SECRET_KEY_BASE64", "\"${secret("IDENTIFY_SECRET_KEY_BASE64", "identify.secretKeyBase64")}\"")
        buildConfigField("String", "IDENTIFY_LOGGER_SECRET_KEY", "\"${secret("IDENTIFY_LOGGER_SECRET_KEY", "identify.loggerSecretKey")}\"")
        buildConfigField("String", "IDENTIFY_SOCKET_SECRET_KEY", "\"${secret("IDENTIFY_SOCKET_SECRET_KEY", "identify.socketSecretKey")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
}

dependencies {
    // Identify SDK — yayınlanmış binary olarak tüketilir, bu repo'da SDK kaynağı yok.
    implementation("com.identify.sdk:sdk-core:$identifySdkVersion")
    implementation("com.identify.sdk:sdk-ui-default:$identifySdkVersion")

    // SDK'nın CameraX bağımlılığı runtime classpath'e (implementation) geliyor, compile
    // classpath'e değil — MySelfieScreen.kt gibi androidx.camera.* tiplerine doğrudan
    // referans veren host kodu için burada ayrıca eklenmesi gerekiyor
    // Sürüm sdk-core'un kullandığı 1.5.2 ile eşleştirildi (docs/dependencies.md).
    implementation("androidx.camera:camera-core:1.5.2")
    implementation("androidx.camera:camera-view:1.5.2")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.jakewharton.timber:timber:5.0.1")
}
