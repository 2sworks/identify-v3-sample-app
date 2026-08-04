# Identify SDK V3 — Örnek Entegrasyon

Bu repo, SDK'nın kendisi değil, bir **referans entegrasyon**dur. Bir host uygulamanın
Identify SDK'nın public API'sini nasıl çağırması gerektiğini gösterir — config kurulumu,
initialization, doğrulama akışını başlatma, modül seçimi, hook'lar — böylece entegratörler
tahmin etmek yerine gerçek, çalışan çağrı desenlerini kopyalayabilir.

SDK'nın implementasyonu (`sdk-core`, `sdk-ui-default`, `sdk-ui-colendi`) bu repoda **yer
almaz**. Tıpkı herhangi bir partner uygulamanın tüketeceği şekilde, GitHub Packages
üzerinden derlenmiş bir Maven bağımlılığı olarak tüketilir.

---

## Bu repoda neler var

| Yol | Neyi gösteriyor |
|---|---|
| `app/src/main/kotlin/com/identify/sample/MainViewModel.kt` | Tüm çağrı sırası: `SdkConfig.Builder(...)`, `IdentifySdk.init(...)`, `IdentifySdk.startAuthentication(...)`, modül seçimi, SSL pinning, NFC bağımlılık enjeksiyonu |
| `app/src/main/kotlin/com/identify/sample/MainActivity.kt` | SDK'nın Compose UI'ını host eden minimal bir Activity |
| `app/src/main/kotlin/com/identify/sample/ui/MainScreen.kt` | Örnek ayarlar/config ekranı (sunucu switch'i, modül toggle'ları, dil) |
| `docs/` | Entegrasyon kılavuzları: SDK config referansı, UI özelleştirme, hook'lar, OCR, yönlendirme mesajları |

---

## 1. Ön Koşullar

`read:packages` yetkisine sahip bir GitHub Personal Access Token'a ve runtime secret
değerlerine (turn server key, secret key'ler) ihtiyacınız olacak — **bunlar Identify
entegrasyon ekibi tarafından ayrı, güvenli bir kanaldan size sağlanır.** Bunların hiçbiri bu
repoda saklanmaz.

## 2. Kimlik Bilgilerinizi Yapılandırın

```bash
cp local.properties.example local.properties
```

`local.properties` dosyasını açıp size sağlanan değerleri girin:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=ghp_YOUR_PERSONAL_ACCESS_TOKEN

identify.turnKey=...
identify.secretKeyBase64=...
identify.loggerSecretKey=...
identify.socketSecretKey=...
```

`local.properties` gitignore'a eklidir — makinenizden hiç çıkmaz. CI için bunun yerine
karşılık gelen environment değişkenlerini ayarlayın: `GPR_USER`, `GPR_TOKEN`,
`IDENTIFY_TURN_KEY`, `IDENTIFY_SECRET_KEY_BASE64`, `IDENTIFY_LOGGER_SECRET_KEY`,
`IDENTIFY_SOCKET_SECRET_KEY` (env değişkenleri `local.properties`'e göre önceliklidir, bkz.
`settings.gradle.kts` / `app/build.gradle.kts`).

## 3. Derleme ve Çalıştırma

```bash
./gradlew :app:installDebug
```

## 4. Demo switch'ler (giriş ekranı → "Seçenekleri Göster")

Giriş ekranında **"Seçenekleri Göster"**'e dokunmak, "Seçenekleri Yönet" bottom sheet'ini
açar (`MainScreen.kt` içindeki `OptionsBottomSheet`). Buradaki switch'lerden ikisi gerçek
`MainViewModel` state'ine bağlıdır ve `startProcess(...)`'in ürettiği config'i değiştirir;
geri kalanı `onCheckedChange` bağlanmamış, sadece görsel placeholder'lardır.

| Switch | State (`MainViewModel`) | `startProcess(...)` üzerindeki etkisi |
|---|---|---|
| **Hook Demo** (*before/after/finished/cancelled/mesaj override*) | `useHookDemo` / `toggleHookDemo()` | Açıkken, `setBeforeHook`, `setAfterHook`, `onIdentifyFinished/Failed/Cancelled` ve mesaj override hook'larının tamamının bağlı olduğu bir `SdkHooks` örneği oluşturulur — tüm hook lifecycle noktalarını çalıştırır. Kapalıyken hiçbir hook geçilmez, SDK varsayılan akışıyla çalışır. Bkz. `docs/hooks.md`. |
| **Custom UI Provider Demo** (*kendi Hazırlık + Selfie ekranımız*) | `useCustomUiProvider` / `toggleCustomUiProvider()` | Açıkken, `StandardUiProvider()` yerine `SampleUiProvider()` geçilir — bu, yalnızca Hazırlık (Preparation) ve Selfie ekranlarını uygulamaya ait Composable'larla override edip geri kalan her şeyi standart UI'a devreder. Bkz. `docs/ui-customization.md`. |

Bunlardan birini koddan tetiklemek (veya kendi demo switch'inizi eklemek) için
`MainViewModel.kt` içindeki aynı deseni izleyin: `StateFlow` olarak dışa açılan bir
`MutableStateFlow<Boolean>`, bir `toggle...()` fonksiyonu ve `startProcess()` içinde config
oluşturulurken `.value`'yu okuyan bir dallanma.

Sheet üzerindeki diğer switch'ler ("Temsilci yayını büyük görünsün", "İşaret dili seçeneği
aktif olsun", "Yeni canlılık testi ekranını dene", "SSL Pinning") bu örnekte sadece
görüntülenir, SDK çağrısını etkilemez.

---

## Gerçek servis çağrılarının yapıldığı yer

Gerçek bir entegrasyonun ihtiyaç duyduğu her şey `MainViewModel.startProcess(...)` içindedir:

```kotlin
val builder = SdkConfig.Builder(baseUrl, BuildConfig.IDENTIFY_TURN_KEY)
    .setIntroEnabled(true)
    .setUiProvider(StandardUiProvider())
    .setCustomModules(manualModules)
    .setLanguage(currentLanguage)
    .setApiTimeout(120)
    .setSecretKeyBase64(BuildConfig.IDENTIFY_SECRET_KEY_BASE64)
    .setLoggerSecretKey(BuildConfig.IDENTIFY_LOGGER_SECRET_KEY)
    .setSocketSecretKey(BuildConfig.IDENTIFY_SOCKET_SECRET_KEY)
    .setSslPins(sslPinsForCurrentBaseUrl())

val config = builder.build()
IdentifySdk.init(application = activity.application, config = config)
IdentifySdk.startAuthentication(activity, identIdValue)
```

`SdkConfig.Builder`'daki tüm seçenekler için `docs/sdk-config.md`'yi, lifecycle callback'leri
(`onIdentifyFinished`, `onIdentifyFailed`, `onIdentifyCancelled`) için `docs/hooks.md`'yi
okuyun.

## Destek

SDK'nın kendisi, GitHub Packages erişimi veya ortama özel kimlik bilgileri hakkındaki
sorularınız için Identify entegrasyon ekibiyle iletişime geçin.
