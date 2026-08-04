# Identify SDK V3 — Bağımlılıklar ve Uyumluluk

## Build Gereksinimleri

| Araç | Minimum | SDK'da Kullanılan |
|------|---------|-------------------|
| Android Gradle Plugin (AGP) | **8.0+** | 9.0.0 |
| Kotlin | **2.0+** | 2.3.0 |
| Kotlin Compose Compiler Plugin | **2.0+** | kotlin.plugin.compose 2.3.0 |
| Java / JVM Hedef | **11** | sourceCompatibility = VERSION_11 |
| `compileSdk` | **36** | 36 |
| `minSdk` | **24** | 24 |

> **Önemli:** SDK, Kotlin 2.x ve AGP 8+ gerektiren API'ler kullanmaktadır (type-safe Navigation, Compose Compiler Plugin ayrımı vb.). Projeniz Kotlin 1.x ile derlenemez.

---

## Transitive Bağımlılıklar

SDK içe aktarıldığında aşağıdaki kütüphaneler **otomatik olarak** projenize eklenir. Kendi projenizde bu kütüphanelerin farklı bir sürümünü kullanıyorsanız versiyon çakışmasına dikkat edin.

### AndroidX & Compose

| Kütüphane | Sürüm |
|-----------|-------|
| `androidx.compose:compose-bom` | `2026.01.00` |
| `androidx.compose.ui:ui` | BOM ile belirlenir |
| `androidx.compose.material3:material3` | BOM ile belirlenir |
| `androidx.navigation:navigation-compose` | `2.9.6` |
| `androidx.lifecycle:lifecycle-runtime-compose` | `2.10.0` |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.10.0` |
| `androidx.activity:activity-compose` | `1.12.2` |
| `androidx.appcompat:appcompat` | `1.7.0` |
| `androidx.core:core-ktx` | `1.17.0` |

### Kotlin

| Kütüphane | Sürüm |
|-----------|-------|
| `kotlinx-coroutines-android` | `1.10.2` |
| `kotlinx-serialization-json` | `1.7.3` |

### Ağ (Network)

| Kütüphane | Sürüm |
|-----------|-------|
| `com.squareup.retrofit2:retrofit` | `2.11.0` |
| `com.squareup.retrofit2:converter-kotlinx-serialization` | `2.11.0` |
| `com.squareup.okhttp3:okhttp` | `5.3.2` |
| `com.squareup.okhttp3:logging-interceptor` | `5.3.2` |

> OkHttp **5.x** kullanılmaktadır. Projenizde OkHttp 4.x bağımlılığı varsa Gradle en yüksek sürümü (5.x) seçecektir — geriye uyumlu olmakla birlikte test edilmesi önerilir.

### Kamera & Görüntü İşleme

| Kütüphane | Sürüm |
|-----------|-------|
| `androidx.camera:camera-core` | `1.5.2` |
| `androidx.camera:camera-camera2` | `1.5.2` |
| `androidx.camera:camera-lifecycle` | `1.5.2` |
| `androidx.camera:camera-view` | `1.5.2` |
| `androidx.camera:camera-extensions` | `1.5.2` |
| `androidx.camera:camera-video` | `1.5.2` |
| `com.google.mlkit:face-detection` | `16.1.7` |
| `com.google.mlkit:text-recognition` | `16.0.1` |
| `com.google.mlkit:object-detection` | `17.0.2` |
| `com.google.mediapipe:tasks-vision` | `0.10.33` |

### NFC & Güvenlik

| Kütüphane | Sürüm |
|-----------|-------|
| `org.jmrtd:jmrtd` | `0.8.3` |
| `net.sf.scuba:scuba-sc-android` | `0.0.26` |
| `org.bouncycastle:bcprov-jdk18on` | `1.79` |

### WebRTC & Diğer

| Kütüphane | Sürüm |
|-----------|-------|
| `io.github.webrtc-sdk:android` | `137.7151.05` |
| `com.jakewharton.timber:timber` | `5.0.1` |

---

## Sık Karşılaşılan Versiyon Çakışmaları

### Kotlin — `kotlin-stdlib` sürüm yükseltmesi

**Senaryo:** Projeniz Kotlin 2.1 kullanıyor, SDK Kotlin 2.3.0 ile derlenmiş.

Gradle kütüphane bağımlılıklarını çözerken her zaman en yüksek sürümü seçer; `kotlin-stdlib` 2.3.0'a yükselebilir. Bu normaldir ve sorun çıkarmaz — iki şey birbirinden bağımsızdır:

- **Kotlin compiler sürümünüz değişmez.** Gradle compiler versiyonuna dokunmaz, projeniz 2.1 ile derlenmeye devam eder.
- **`kotlin-stdlib` runtime'da 2.3.0 olabilir.** Kotlin stdlib geriye dönük uyumludur.

> Kotlin 1.x kullanan projeler SDK ile derlenemez. Kotlin 2.0+ gereklidir.

---

### Kotlin — `compileSdk` uyarısı

**Senaryo:** Projeniz `compileSdk = 35`, SDK `compileSdk = 36` ile derlenmiş.

`compileSdk` müşteri tarafında bağımsız ayarlanabilir; SDK'nın 36 ile derlenmesi müşteriyi zorlamaz. Ancak Android Studio bazı API'leri çözümleyemeyebilir ve lint uyarısı verebilir. Sorun yaşarsanız `compileSdk = 36`'ya geçin.

---

### Kotlin — `minSdk` çakışması

**Senaryo:** Projenizin `minSdk = 21`, SDK `minSdk = 24` gerektiriyor.

SDK'nın `minSdk = 24` sınırı CameraX ve NFC bağımlılıklarından gelir. Uygulamanızın minSdk'sını 24'e yükseltmeniz gerekir:

```kotlin
android {
    defaultConfig {
        minSdk = 24
    }
}
```

---

### OkHttp 4.x → 5.x

**Senaryo:** Projeniz OkHttp 4.x kullanıyor, SDK OkHttp 5.x çekiyor.

Gradle en yüksek sürümü (5.x) seçer. OkHttp 5.x büyük ölçüde geriye uyumludur ancak bazı deprecated API'ler kaldırılmıştır. Kendi kodunuzda bu API'leri kullanıyorsanız güncellemeniz gerekebilir.

Zorunlu kalırsanız 4.x'te kalmak için:

```kotlin
configurations.all {
    resolutionStrategy.force("com.squareup.okhttp3:okhttp:4.12.0")
}
```

> Bu durumda SDK'nın ağ katmanı 4.x üzerinde çalışır; resmi olarak desteklenmez ve test edilmemiştir.

---

### BouncyCastle — çift sürüm

**Senaryo:** Başka bir kütüphane `bcprov-jdk15on` veya `bcprov-jdk16on` çekiyor.

SDK, BouncyCastle'ı yalnızca **NFC pasaport okuma** için kullanır (`jmrtd` kütüphanesinin zorunlu bağımlılığı). `bcprov-jdk18on` en güncel varyanttır ve eski varyantların yerini alır. Projenizde eski varyant varsa `exclude` ile temizleyin:

```kotlin
configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk16on")
}
```

---

### ML Kit — Play Services vs. Standalone

**Senaryo:** Projeniz ML Kit'in Play Services varyantını kullanıyor, SDK standalone varyantını çekiyor.

SDK, ML Kit'in **standalone** (Google Play Services gerektirmeyen) varyantını kullanır. İki varyant aynı anda projede bulunursa çakışma oluşur. Play Services varyantlarını dışlayın:

```kotlin
configurations.all {
    exclude(group = "com.google.android.gms", module = "play-services-mlkit-text-recognition")
    exclude(group = "com.google.android.gms", module = "play-services-mlkit-face-detection")
    exclude(group = "com.google.android.gms", module = "play-services-mlkit-object-detection")
}
```
