# IdentifySDK v3 — OCR / Kimlik Tarama Entegrasyon Kılavuzu

## İçindekiler
1. [Genel Mimari](#genel-mimari)
2. [Tarama Modları](#tarama-modları)
3. [Upload Sırası ve Zamanlaması](#upload-sırası-ve-zamanlaması)
4. [UiState — Erişilebilir Veriler](#uistate--erişilebilir-veriler)
5. [Tarama Ekranı Parametreleri](#tarama-ekranı-parametreleri)
6. [Sesli Yönlendirme Mesajları](#sesli-yönlendirme-mesajları)
7. [Sıkça Sorulan Sorular](#sıkça-sorulan-sorular)

---

## Genel Mimari

```
IdentifySdk.init(config)
        │
        └─▶ IdentifyActivity (ayrı Activity)
                │
                ├─▶ NavHost (identifyGraph)
                │       ├─ Handshake (ident id doğrulama)
                │       ├─ Intro
                │       ├─ Prepare
                │       ├─ DocumentSelection / IdCard / Ovd / Passport ...
                │       ├─ Selfie
                │       ├─ NFC / AgentCall / Liveness ...
                │       └─ ResultSuccess
                │
                └─▶ Hooks & Events → host uygulamaya bildirim
```

SDK, host uygulamadan **ayrı bir Activity** olarak çalışır. Tüm veri akışı `IdentityRepository` üzerinden backend'e yapılan HTTP çağrılarıyla gerçekleşir.

---

## Tarama Modları

### 1. Standard OCR (`DocumentCombinedScreen` / `IdCard`)

Anlık (instant) upload modelidir. Her yüz çekildiği anda backend'e gönderilir; sonuç beklenir, sonra bir sonraki adıma geçilir.

**Akış:**

```
Kamera açılır
    │
    ├─▶ DocumentAnalyzer (frame başına)
    │       ├─ Geçersiz döküman (ehliyet/pasaport keyword) → DocumentInvalid → bloke
    │       ├─ DocumentLocated / TooFar / TooClose / BlurDetected → rehberlik mesajı
    │       ├─ ContentMissing → scan error
    │       └─ ScanningDone → ÖN YÜZÜ YAKALA
    │
    ├─▶ [ÖN YÜZÜ YAKALA]
    │       ├─ Bitmap + OCR verisi alınır
    │       ├─ INSTANT UPLOAD #1 → idFront (OCR verileriyle)
    │       ├─ INSTANT UPLOAD #2 → idPortrait (yüz kırpması, ayrı istek)
    │       └─ Başarılıysa: arka yüz taraması başlar
    │
    └─▶ [ARKA YÜZÜ YAKALA]
            ├─ MRZ parse edilir
            ├─ INSTANT UPLOAD #3 → scanner_mrz_data (MRZ anahtar verisi)
            └─ INSTANT UPLOAD #4 → idBack (MRZ ile birlikte)
```

**Toplam istek sayısı:** 4 ayrı HTTP isteği (ön + portre + MRZ verisi + arka)

---

### 2. OVD Taraması (`OvdScreen`)

Toplu (batch) upload modelidir. Üç adım tamamlanınca önizleme ekranı çıkar, kullanıcı "Onayla" dedikten sonra tüm fotoğraflar sırayla yüklenir.

**Akış:**

```
FRONT adımı
    └─▶ Fotoğraf çekilir → BELLEKTE SAKLANIR (upload yok)

OVD adımı (holografik gökkuşağı)
    └─▶ Fotoğraf çekilir → BELLEKTE SAKLANIR (upload yok)

BACK adımı
    └─▶ Fotoğraf çekilir → BELLEKTE SAKLANIR (upload yok)

Önizleme Ekranı
    └─▶ Kullanıcı 3 fotoğrafı görür → "Onayla" tıklar

onConfirmImages() — TOPLU UPLOAD BAŞLAR
    ├─▶ UPLOAD #1 → idFront     (ön yüz + OCR)
    ├─▶ UPLOAD #2 → idPortrait  (yüz kırpması)
    ├─▶ UPLOAD #3 → idFrontOvd  (holografik)
    └─▶ UPLOAD #4 → idBack      (arka + MRZ)
```

**Toplam istek sayısı:** 4 ayrı HTTP isteği, sıralı olarak gönderilir.  
**Fark:** OVD'de fotoğraflar anında değil, kullanıcı onayından sonra yüklenir.

---

## Upload Sırası ve Zamanlaması

| # | Tip | Endpoint Tipi | Ne Zaman |
|---|-----|--------------|----------|
| 1 | `idFront` | `uploadImageBase64` | Standard: ön yakalanır yakalanmaz / OVD: onay sonrası |
| 2 | `idPortrait` | `uploadImageBase64` | Standard: ön upload başarılı olduktan hemen sonra / OVD: onay sonrası |
| 3 | `idFrontOvd` | `uploadImageBase64` | Sadece OVD akışı — onay sonrası |
| 4 | `idBack` | `uploadImageBase64` | Standard: arka yakalanır yakalanmaz / OVD: onay sonrası |

### Karşılaştırma Hatası (Comparison Warning)

Ön yüz upload'ında backend form verileriyle eşleşme bulamazsa:
- 1. ve 2. denemede uyarı gösterilir, yeniden çekme istenir
- 3. denemede hata göz ardı edilir, portre yine de yüklenir ve akış devam eder

```
Standard OCR comparison akışı:
    Backend → comparison error
        ├─ deneme < 3 → uyarı göster, yeniden tara
        └─ deneme == 3 → zorla kabul et, devam et
```

---

## UiState — Erişilebilir Veriler

### DocumentScanUiState (Standard OCR)

```kotlin
data class DocumentScanUiState(
    val frontImage: Bitmap?,           // Çekilen ön yüz fotoğrafı
    val backImage: Bitmap?,            // Çekilen arka yüz fotoğrafı
    val frontState: CaptureState,      // IDLE | UPLOADING | SUCCESS | ERROR | COMPARISON_WARNING
    val backState: CaptureState,       // IDLE | UPLOADING | SUCCESS | ERROR
    val guidanceMessage: String,       // Kameraya gösterilecek rehberlik mesajı
    val scanErrorMessage: String?,     // Hata mesajı (id_not_detected, alan okunamadı vb.)
    val comparisonWarning: String?,    // Form eşleşme uyarısı (1/3, 2/3 ...)
    val isScanning: Boolean,
    val isUploading: Boolean,
    val manualFallbackEnabled: Boolean // Manuel çek butonu görünür mü
)
```

**Müşteri bu verileri şu şekilde kullanabilir:**
- `frontImage` / `backImage` → kendi UI'ında önizleme göstermek
- `frontState == CaptureState.SUCCESS` → ön yüzün başarıyla işlendiğini göstermek
- `scanErrorMessage` → özel hata UI'ı
- `comparisonWarning` → kaç deneme kaldığını göstermek

### OvdUiState

```kotlin
data class OvdUiState(
    val frontImage: Bitmap?,           // Ön yüz fotoğrafı (onay öncesi erişilebilir)
    val ovdImage: Bitmap?,             // Holografik fotoğraf
    val backImage: Bitmap?,            // Arka yüz fotoğrafı
    val currentStep: OvdStep,         // FRONT | OVD | BACK
    val showPreview: Boolean,          // Önizleme ekranı aktif mi
    val guidanceMessage: String,
    val scanErrorMessage: String?,
    val isUploading: Boolean,
    val manualFallbackEnabled: Boolean
)
```

> **Not:** OVD'de `frontImage`, `ovdImage`, `backImage` kullanıcı onayından önce bellekte hazırdır.  
> Müşteri önizleme ekranında bu bitmapleri kendi UI'ında gösterebilir.

---

## Tarama Ekranı Parametreleri

`StandardUiProvider`'ı miras alıp `DocumentCombinedScreen`'i override etmeden, sadece tarama ekranının görsel ve ses davranışını özelleştirebilirsin.

### Çerçeve Renkleri

Tarama sırasında kimlik etrafındaki çerçeve, anlık duruma göre otomatik renk değiştirir:

| Durum | Varsayılan Renk | Parametre |
|-------|----------------|-----------|
| Aktif tarama | `Color.Cyan` | `scanColor` |
| Başarılı çekim | `Color(0xFF41D97F)` (yeşil) | `successColor` |
| Hata / geçersiz belge | `Color(0xFFFF453A)` (kırmızı) | `errorColor` |

Tarama animasyonunun (ışıyan çizgi) rengi otomatik olarak çerçeve rengiyle senkronize edilir.

### Sesli Rehberlik Zamanlaması

| Parametre | Varsayılan | Açıklama |
|-----------|-----------|----------|
| `speechMinGapMs` | `1500` | İki farklı mesaj arasındaki minimum bekleme (ms) |
| `speechRepeatCooldownMs` | `3500` | Aynı mesajın tekrar edilmeme süresi (ms) |
| `speechPostPrioritySilenceMs` | `2500` | Öncelikli mesajdan sonra normal mesajların bekletilme süresi (ms) |

### Özel Ses / Kendi TTS Engine'in

`onSpeak` callback'ini sağlarsan SDK'nın dahili TTS'i hiç başlatılmaz. Tüm mesajlar callback'e iletilir; nasıl seslendirildiğine sen karar verirsin:

```kotlin
class MyUiProvider : StandardUiProvider() {

    @Composable
    override fun DocumentCombinedScreen(onNext: () -> Unit, onBack: () -> Unit) {
        var showCamera by remember { mutableStateOf(false) }
        val viewModel: DocumentScanViewModel = viewModel(factory = SdkViewModelFactory)

        if (showCamera) {
            DocumentScanScreen(
                viewModel = viewModel,
                onNext = { showCamera = false },
                // Çerçeve renkleri
                scanColor = Color(0xFF446EF7),
                successColor = Color(0xFF41D97F),
                errorColor = Color(0xFFFF453A),
                // Ses cooldown
                speechMinGapMs = 1000L,
                speechRepeatCooldownMs = 4000L,
                // Kendi ses engine'in
                onSpeak = { text, priority ->
                    myAudioPlayer.play(audioResFor(text))
                }
            )
        } else {
            // Kendi dashboard UI'ın — kamera butonuna tıklandığında showCamera = true yap
            Button(onClick = { showCamera = true }) {
                Text("Kimliği Tara")
            }
        }
    }
}
```

> **Not:** `onSpeak` sağlandığında cooldown ve deduplication mantığı **hâlâ çalışır** — aynı mesaj kısa sürede iki kez tetiklenmez. Sadece TTS yerine kendi callback'in çağrılır.

---

## Sesli Yönlendirme Mesajları

Standard OCR (`DocumentScanViewModel`) ve OVD (`OvdViewModel`) akışları, kamera açıkken kullanıcıyı hem ekranda (`uiState.guidanceMessage`) hem sesli olarak (TTS) yönlendirir. Her mesajın sabit bir kimliği (`GuidanceMessageKey`) vardır; **partnerler `SdkHooks.provideGuidanceMessage` ile bu mesajların metnini kendi dilleri/üslubuyla değiştirebilir** — aşağıya bakınız.

### Ortak Mesajlar (hem OCR hem OVD)

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `ID_NOT_DETECTED` | Kamerada belge bulunamadı | "Kimlik tespit edilemedi. Lütfen geçerli bir kimlik kartı kullanın." |
| `DOCUMENT_ROTATE_HORIZONTAL` | Belge dikey/yan konumda | "Kimliği yatay tutun" |
| `DOCUMENT_DETECTED_CHECKING` | Belge bulundu, içerik kontrol ediliyor | "Kimliği netleştirin ve çerçevede tutun" |
| `MOVE_CLOSER` | Belge kameradan çok uzak | "Biraz Yakınlaşın" |
| `MOVE_FURTHER` | Belge kameraya çok yakın | "Biraz Uzaklaştırın" |
| `KEEP_FLAT` | Belge fazla eğik | "Düz Tutun" |
| `FOCUS_AND_STEADY` | Görüntü bulanık | "Netleştirin ve Sabit Tutun" |
| `CAPTURE_SUCCESS` | Fotoğraf başarıyla çekildi | "BAŞARILI!" |

### Yalnızca OCR (`DocumentCombinedScreen` / `IdCard`)

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `OCR_PREPARING` | Tarama başlarken | "Hazırlanıyor..." |
| `OCR_ALIGN_FRONT_SIDE` | Arka yüz beklenirken ön yüz gösterildi | "Belgenin Ön Yüzünü Hizalayın" |
| `OCR_ALIGN_BACK_SIDE` | Ön yüz beklenirken arka yüz gösterildi | "Belgenin Arka Yüzünü Hizalayın" |
| `OCR_ALIGN_ID_FIRST` | Manuel çekim denendi, tamponda kullanılabilir kare yok | "Lütfen önce kimliği çerçeveye hizalayın" |

### Yalnızca OVD (`OvdScreen`)

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `OVD_WAIT_FOR_CAPTURE` | Belge bulundu (OVD dışındaki adımlarda), sabit tutulması isteniyor | "Harika, Öylece Bekleyin..." |
| `OVD_SCAN_FRONT_SIDE` | Akış başlıyor / FRONT adımına dönülüyor | "Lütfen kimliğinizin ön yüzünü taratın." |
| `OVD_SCAN_BACK_SIDE` | BACK adımına geçildi | "Şimdi lütfen kimliğinizin arka yüzünü taratın." |
| `OVD_FLASH_MOVE_CARD` | OVD adımı: hologram efekti için belgeyi hafifçe oynatma talimatı | "Gökkuşağı efektini yakalamak için kimliği hafifçe oynatın." |
| `OVD_TOO_WHITE_TILT` | Aşırı parlama/ışık yansıması algılandı | "Çok fazla parlama var, kimliği hafifçe eğin." |
| `OVD_GLARE_CAPTURED` | Hologram (gökkuşağı) efekti yakalandı | "Efekt yakalandı, bekleyin..." |
| `OVD_WRONG_SIDE_FRONT` | FRONT adımında yanlış taraf gösterildi | "Lütfen kimliğinizin ön yüzünü çevirin." |
| `OVD_WRONG_SIDE_BACK` | BACK adımında yanlış taraf gösterildi | "Lütfen kimliğinizin arka yüzünü çevirin." |
| `OVD_VERIFICATION_COMPLETED` | FRONT + OVD + BACK adımlarının üçü de tamamlandı | "Kimlik doğrulaması başarıyla tamamlandı." |

> Yukarıdaki tablolar yalnızca OCR/OVD'yi kapsar. SDK'daki **tüm** ekranların (Hazırlık, Selfie,
> Liveness, Pasaport, Diğer Belge, NFC, Temsilci Görüşmesi, Adres, İmza, Konuşma Testi, Video
> Kayıt, Intro, Sonuç) yönlendirme mesajları, bu mesajları nasıl özelleştireceğiniz ve sesi
> tamamen kapatan `setTtsEnabled` config'i için bkz. **[Yönlendirme Mesajları Rehberi](guidance-messages.md)**.

### Mesajları Özelleştirme (`provideGuidanceMessage`)

Varsayılan metinleri kendi kelimelerinizle veya markanıza uygun bir üslupla değiştirmek için `SdkHooks.provideGuidanceMessage`'ı atayın. `defaultMessage` parametresi SDK'nın o an aktif dile göre çözdüğü metindir; `null` dönerseniz SDK varsayılanı kullanılır.

```kotlin
import com.identify.sdk.core.model.GuidanceMessageKey

val hooks = SdkHooks().apply {
    provideGuidanceMessage = { key, defaultMessage ->
        when (key) {
            GuidanceMessageKey.MOVE_CLOSER -> "Kimliği biraz daha kameraya yaklaştırır mısınız?"
            GuidanceMessageKey.OVD_FLASH_MOVE_CARD -> "Kimliği hafifçe eğerek gökkuşağı deseninin çıkmasını sağlayın."
            else -> null // diğer tüm mesajlar için SDK varsayılanı kullanılsın
        }
    }
}

IdentifySdk.init(application, config, hooks)
```

Bu, hem ekranda gösterilen `uiState.guidanceMessage` metnini hem seslendirilen (TTS) metni değiştirir — ikisi de aynı kaynaktan (bu hook) beslenir, ayrı ayrı senkronize etmenize gerek yoktur. Yalnızca **sesin nasıl çalınacağını** (ör. kendi ses kayıtlarınız) değiştirmek isterseniz bunun yerine (veya bununla birlikte) OCR ekranındaki [`onSpeak` parametresini](#özel-ses--kendi-tts-enginein) kullanın — `onSpeak` bu hook'un ürettiği nihai metni alır.

> OVD ekranında (`OvdScreen`) `onSpeak` benzeri bir ses-katmanı override'ı **yoktur** — OVD'nin sesi doğrudan dahili `SpeechManager` üzerinden çalınır. `provideGuidanceMessage` her iki akışta da (OCR + OVD) çalışır; yalnızca OCR'daki ses *çalma* mekanizması (`onSpeak`) ayrıca özelleştirilebilir.

---

## Sıkça Sorulan Sorular

**S: Fotoğrafları kendi backend'ime de gönderebilir miyim?**  
A: `uiState.frontImage` / `backImage` bitmapları önizleme sırasında okunabilir. SDK upload'ları bloke etmek mümkün değildir; yalnızca paralel olarak kendi isteğini gönderebilirsin.

**S: OVD'de kullanıcı fotoğrafı onaylamadan önce görebilir mi?**  
A: Evet. `uiState.showPreview == true` olduğunda `frontImage`, `ovdImage`, `backImage` bitmapları bellektedir, `viewModel.onConfirmImages()` çağrılana kadar upload yapılmaz.

**S: Standard OCR'da ön yüz upload'undan sonra arka yüze otomatik mi geçiliyor?**  
A: Evet. `frontState == CaptureState.SUCCESS` olduktan sonra `checkCanProceed()` arka yüz taramasını otomatik başlatır. Host uygulamanın müdahalesine gerek yoktur.

**S: Kamera izni yoksa ne olur?**  
A: `camera_permission_required` string'i gösterilir. İzin alınana kadar kamera açılmaz; izin verildiğinde akış devam eder.

**S: 10 saniyede otomatik "Manuel Çek" butonu çıkıyor, bunu kapatabilir miyim?**  
A: `manualFallbackEnabled` alanını UI'dan görmezden gelerek butonu kaldırabilirsin.
