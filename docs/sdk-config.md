# Identify SDK V3 — Yapılandırma Referansı (SdkConfig)

`SdkConfig`, SDK'nın tüm davranışsal ayarlarını tutan sınıftır ve `SdkConfig.Builder` üzerinden inşa edilir. Bu doküman `Builder`'daki tüm `set...` metodlarını, varsayılan değerlerini ve etkilerini listeler.

> Tema/renk özelleştirme (`setThemeMode`, `setLightColors`, `setDarkColors`) ve `setUiProvider` ayrı bir konudur — bkz. [UI Özelleştirme](ui-customization.md#tema-ve-renk-özelleştirme). Akış adımları arasına ekran enjekte etme (`SdkHooks`) için bkz. [Hook Sistemi](hooks.md).

---

## İçindekiler

- [Zorunlu Alanlar](#zorunlu-alanlar)
- [Akış / Ekran Davranışı](#akış--ekran-davranışı)
- [Zaman Aşımları](#zaman-aşımları)
- [Tarama Davranışı](#tarama-davranışı)
- [Ses](#ses)
- [Güvenlik](#güvenlik)
- [Diğer](#diğer)
- [Akışı Sonlandırma ve Lifecycle](#akışı-sonlandırma-ve-lifecycle)

---

## Zorunlu Alanlar

```kotlin
SdkConfig.Builder(baseApiUrl, turnKey)
    .setUiProvider(StandardUiProvider())
    .build()
```

| Alan | Tip | Açıklama |
|---|---|---|
| `baseApiUrl` | `String` (constructor) | Backend API kök URL'i |
| `turnKey` | `String` (constructor) | TURN sunucusu erişim anahtarı (AgentCall/WebRTC için) |
| `setUiProvider(provider)` | `SdkUiProvider` | **Zorunlu.** Verilmezse `build()` `IllegalStateException` fırlatır. `StandardUiProvider()` veya özel implementasyon — bkz. [UI Özelleştirme](ui-customization.md). |

---

## Akış / Ekran Davranışı

| Metod | Varsayılan | Açıklama |
|---|---|---|
| `setIntroEnabled(Boolean)` | `true` | Akışın başındaki tanıtım (Intro) ekranını açar/kapatır. |
| `setThankYouEnabled(Boolean)` | `true` | Akış sonundaki teşekkür ekranını açar/kapatır. |
| `setStepperEnabled(Boolean)` | `true` | Ekranların üstündeki adım göstergesini (stepper) açar/kapatır. |
| `setBackButtonEnabled(Boolean)` | `true` | Akış genelinde geri gitmeyi açar/kapatır — hem sistem/donanım geri tuşu hem ekran içi geri butonları. `false` yapıldığında `SdkNavigator.navigateBack()` merkezi olarak no-op olur ve `IdentifyActivity` sistem geri tuşunu tüketir; kullanıcı akışı yalnızca `IdentifySdk.close()` ile veya akışı bitirerek terk edebilir. |
| `setDocumentType(SelectedDocument)` | `null` (seçim ekranı gösterilir) | `ID_CARD` modülü aktifken `DocumentSelectionScreen`'i atlayıp doğrudan belirtilen belge akışına gider. `ID_CARD` → hologramsız `IdCardCombined` akışı, `PASSPORT` → pasaport akışı, `OTHER` → diğer belge akışı. **`SdkModule.ID_CARD_OVD`'den farklıdır** — `ID_CARD_OVD` hologram doğrulamalı (`Ovd`) akışa gider; bu alanla `ID_CARD` seçmek hologramsız akışa gider. `OTHER` seçildiğinde NFC modülü otomatik listeden çıkarılır (OTHER belge tipinde MRZ/NFC yok). Pasaport-only veya diğer-belge-only entegre eden partnerler için kullanılır. |
| `setCustomModules(List<SdkModule>?)` | `null` (backend'in gönderdiği modüller) | Akışta hangi modüllerin çalışacağını client tarafında sabitler; backend'den gelen modül listesini geçersiz kılar. |

```kotlin
// Örnek: sadece pasaport akışı, seçim ekranı olmadan
SdkConfig.Builder(baseUrl, turnKey)
    .setUiProvider(StandardUiProvider())
    .setDocumentType(SelectedDocument.PASSPORT)
    .build()
```

---

## Zaman Aşımları

| Metod | Varsayılan | Açıklama |
|---|---|---|
| `setApiTimeout(Long saniye)` | `60` | HTTP isteklerinin zaman aşımı süresi. |
| `setCallConnectionTimeout(Long saniye)` | `null` (backend'in gönderdiği süre) | Temsilci görüşmesinin (AgentCall) bağlantı kurma aşaması için client-side zaman aşımı. Süre dolduğunda `AgentCallViewModel` kullanıcıyı otomatik olarak "cevap verilemiyor" (`MissedCall`) durumuna geçirir; var olan retry akışı kullanılır. Temsilci bağlandığında (SDP offer alındığında) timer iptal edilir, retry'da yeniden başlar. |
| `setNfcExceptionCount(Int)` | `3` | NFC çip okuma sırasında izin verilen maksimum hata sayısı. Bu sayıya ulaşıldığında kullanıcıya "tekrar dene" imkânı verilmeden modül başarısız sayılır ve akış bir sonraki ekrana geçer (`nfcExceptionCount` > 0 zorunludur, aksi halde `IllegalArgumentException`). Comparison error sayacı ayrıdır. |
| `setVideoRecordDuration(Long ms)` | `null` (backend'in gönderdiği `video_record_duration`) | Video kayıt ekranının süresini client tarafında sabitler; set edilirse backend değerini geçersiz kılar. `HandshakeViewModel`'de `config.videoRecordDurationMs ?: info.videoRecordDuration` olarak uygulanır. |

---

## Tarama Davranışı

| Metod | Varsayılan | Açıklama |
|---|---|---|
| `setAutoCapture(Boolean)` | `true` | OCR (kimlik kartı/pasaport) ve OVD ekranlarında fotoğrafın otomatik olarak (belge/hologram algılandığında) çekilip çekilmeyeceğini belirler. `false` yapılırsa otomatik algılama devre dışı kalır, kullanıcı yalnızca manuel çek butonuyla fotoğraf çekebilir — buton, normalde 8-10 saniyelik fallback beklemesi olmadan **baştan aktif** olur (`DocumentScanViewModel`/`OvdViewModel`). V2'deki otomatik çekim davranışının karşılığıdır. |

---

## Ses

| Metod | Varsayılan | Açıklama |
|---|---|---|
| `setTtsEnabled(Boolean)` | `true` | OCR, OVD ve diğer tüm akışlardaki sesli yönlendirmeyi (TTS) açar/kapatır. `false` yapılırsa ekrandaki yazılı yönlendirme mesajları (`guidanceMessage`) etkilenmez, yalnızca ses kapanır. Mesajların tam listesi için bkz. [OCR Entegrasyonu — Sesli Yönlendirme Mesajları](ocr-integration.md#sesli-yönlendirme-mesajları). |

```kotlin
SdkConfig.Builder(baseUrl, turnKey)
    .setUiProvider(StandardUiProvider())
    .setTtsEnabled(false)
    .build()
```

---

## Güvenlik

| Metod | Varsayılan | Açıklama |
|---|---|---|
| `setSslPins(List<SslPin>)` | `emptyList()` (sistem TLS doğrulaması) | HTTP ve WebSocket bağlantılarına SSL certificate pinning uygular (domain + SHA-256 fingerprint çiftleri). Hem REST katmanına hem `SocketManager`'a `CertificatePinner` ile uygulanır. |
| `setSecretKeyBase64(String)` | `null` (şifreli MRZ desteği kapalı) | Backend'den gelen şifreli MRZ alanlarını (birthday, serial_number, expire_date) çözmek için kullanılan Base64 kodlanmış AES-256-CBC secret key. |
| `setSocketSecretKey(String)` | `null` | WebSocket bağlantısı için HMAC-SHA1 imzalama key'i (`socket_auth=1` ise zorunlu). Legacy SDK'daki `SOCKET_SECRET_KEY` ile aynı. |
| `setLoggerSecretKey(String)` | `null` | Log gönderimi için secret key. |

```kotlin
SdkConfig.Builder(baseUrl, turnKey)
    .setUiProvider(StandardUiProvider())
    .setSslPins(listOf(SslPin(domain = "api.example.com", pins = listOf("sha256/AAAA..."))))
    .build()
```

---

## Diğer

| Metod | Varsayılan | Açıklama |
|---|---|---|
| `setLanguage(SdkLanguage)` | `SdkLanguage.TR` | SDK ekranlarının dili. |
| `setNfcDependency(NfcDependency)` | `null` | `idCard` modülü aktif değilse ve MRZ verisi belge taramasından elde edilemiyorsa NFC okuma için gereken MRZ verisini (belge no, doğum tarihi, son geçerlilik tarihi) client'tan sağlar. |
| `setAddressDocumentListener(AddressDocumentListener)` | `null` | Adres doğrulama modülünde belge olaylarını dinlemek için. |
| `setSnapshotMessage(String)` | `null` (varsayılan "Ekran görüntüsü alındı") | Canlı görüşmede agent ekran görüntüsü aldığında gösterilecek toast mesajı. |
| `setLivenessConfig(LivenessConfig)` | `LivenessConfig()` | Canlılık tespiti (liveness) modülü ayarları. |
| `setLivenessListener(LivenessListener)` | `null` | Liveness olaylarını dinlemek için. |

---

## Akışı Sonlandırma ve Lifecycle

Bunlar `SdkConfig` alanı değildir ama entegrasyon sırasında bilinmesi gereken lifecycle davranışlarıdır:

- **`IdentifySdk.close()`** — Host uygulamanın doğrulama akışını programatik olarak sonlandırmasını sağlar (örn. bir oturum zaman aşımı policy'si gereği). Çalışan `IdentifyActivity`'yi kapatır (`finish()`). v2'deki `closeSdk()` karşılığıdır. Detaylar ve `onIdentifyCancelled` ile ilişkisi için bkz. [Hook Sistemi — onIdentifyCancelled](hooks.md#onidentifycancelled).
- **Socket temizliği** — `IdentifyActivity` herhangi bir şekilde kapanırsa (kullanıcı geri tuşu, `IdentifySdk.close()`, OS'nin Activity'yi/process'i kill etmesi dahil) `onDestroy()` içinde WebSocket bağlantısı garanti kapatılır (`socketManager.disconnect()`). Ayrıca çağırmanız gereken bir temizlik yoktur.
- **FileProvider çakışması** — SDK kendi `FileProvider`'ını (`IdentifySdkFileProvider`, authority: `${applicationId}.identify_file_provider`) tanımlar. Host uygulamanız da kendi `FileProvider`'ınızı tanımlıyorsanız artık manifest merge çakışması **yaşanmaz** (SDK kendi alt sınıfını kullandığı için host'un `FileProvider` tanımıyla `android:name` çakışmaz), herhangi bir ek işlem gerekmez.
