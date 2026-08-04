# Identify SDK — UI Özelleştirme Kılavuzu (SdkUiProvider)

## İçindekiler

1. [Genel Bakış](#genel-bakış)
2. [Entegrasyon Seçenekleri](#entegrasyon-seçenekleri)
3. [Tema ve Renk Özelleştirme](#tema-ve-renk-özelleştirme)
4. [Senaryo 1 — Hazır UI (StandardUiProvider)](#senaryo-1--hazır-ui)
5. [Senaryo 2 — Kısmi Override (Önerilen)](#senaryo-2--kısmi-override)
6. [Senaryo 3 — Tamamen Özel UI](#senaryo-3--tamamen-özel-ui)
7. [Modül Seçimi ve Ekranların Tetiklenmesi](#modül-seçimi-ve-ekranların-tetiklenmesi)
8. [ViewModel Erişimi](#viewmodel-erişimi)
9. [Ekran Başına ViewModel Referansı](#ekran-başına-viewmodel-referansı)
10. [ViewModel State Detayları](#viewmodel-state-detayları)
11. [Sıkça Sorulan Sorular](#sıkça-sorulan-sorular)

---

## Genel Bakış

SDK kendi `IdentifyActivity`'si içinde çalışır. Her modüle gelindiğinde SDK, config'de verilen `SdkUiProvider` üzerinden ilgili Composable'ı çağırır:

```
IdentifyActivity
    │
    ├─ uiProvider.HandshakeScreen(identificationId, onSuccess, onFailure)
    ├─ uiProvider.IntroScreen(onNext)
    ├─ uiProvider.SelfieScreen(onNext, onBack)
    ├─ uiProvider.LivenessScreen(onNext, onBack)
    └─ uiProvider.ResultSuccessScreen(onFinish)
```

`onNext` → SDK bir sonraki modüle geçer  
`onBack` → SDK bir önceki ekrana döner  

Tüm backend iletişimi, upload, socket, navigasyon SDK içinde kalır. Müşteri sadece UI katmanını kontrol eder.

---

## Entegrasyon Seçenekleri

| Seçenek | Ne Zaman | Kod Miktarı |
|---------|----------|-------------|
| `StandardUiProvider()` | Tasarım önemli değil, hızlı entegrasyon | Sıfır |
| `SdkUiProvider`'ı implement edip `StandardUiProvider`'a delege et | 1-3 ekranı değiştirmek yeterli | Az |
| `SdkUiProvider`'ı implement et | Tüm ekranlar marka tasarımına uymalı | Çok |

---

## Tema ve Renk Özelleştirme

`SdkUiProvider` ekranların *hangi Composable* ile çizileceğini kontrol eder; `SdkColors` ise hiçbir ekranı override etmeden **marka renklerinizi** (buton, arka plan, metin, hata rengi vb.) SDK'nın tüm ekranlarına uygular — hem `StandardUiProvider` hem de Senaryo 2'de delege edilen ekranlar için geçerlidir.

```kotlin
import com.identify.sdk.core.theme.SdkColors

val myLightColors = SdkColors(
    primary = Color(0xFF446EF7),       // Ana buton / vurgu rengi
    primaryDark = Color(0xFF2C5BF6),   // primaryContainer üzerindeki metin/ikon rengi
    primaryLight = Color(0xFFF0F5FF),  // Vurgulu container arka planı (chip, seçili state vb.)
    onPrimary = Color.White,           // primary arka plan üzerindeki metin/ikon rengi
    background = Color(0xFFF9FAFB),    // Ekran arka planı
    onBackground = Color(0xFF111827),  // background üzerindeki metin
    surface = Color.White,             // Kart / bottom sheet / dialog arka planı
    onSurface = Color(0xFF1A1A1A),     // surface üzerindeki metin
    success = Color(0xFF41D97F),       // Başarı ikonu/rozeti rengi (NFC, belge tarama vb.)
    error = Color(0xFFFF453A),         // Hata metni, buton ve ikon rengi
    textSecondary = Color(0xFF9CA3AF), // İkincil/muted metin (alt başlık, placeholder, border)
    border = Color(0xFFE5E7EB)         // Kenarlık / ayırıcı çizgi rengi
)

val myDarkColors = myLightColors.copy(
    background = Color(0xFF111827),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF1F2533),
    onSurface = Color(0xFFF0F5FF),
    textSecondary = Color(0xFF9CA3AF),
    border = Color(0xFF313033)
)

IdentifySdk.init(
    application = this,
    config = SdkConfig.Builder(baseUrl, turnKey)
        .setUiProvider(StandardUiProvider())
        .setThemeMode(SdkConfig.ThemeMode.SYSTEM) // LIGHT | DARK | SYSTEM (varsayılan)
        .setLightColors(myLightColors)
        .setDarkColors(myDarkColors)
        .build()
)
```

- `setThemeMode` — SDK'nın açılacağı Activity'nin görsel modunu belirler. `SYSTEM` (varsayılan) cihazın o anki karanlık/aydınlık modunu takip eder; `LIGHT`/`DARK` sistemden bağımsız olarak sabitler ve host uygulamanın genel gece modunu etkilemeden yalnızca SDK'nın kendi ekranını değiştirir.
- `setLightColors` / `setDarkColors` — verilmezse SDK'nın kendi varsayılan paleti (`SdkColors.DefaultLight` / `SdkColors.DefaultDark`) kullanılır. **İkisini de vermeniz önerilir**: yalnızca `setLightColors` verip `setDarkColors`'ı boş bırakırsanız karanlık modda SDK'nın varsayılan renkleri kullanılır, marka renkleriniz o modda görünmez.
- Verdiğiniz `SdkColors`, SDK içindeki tüm Material3 bileşenlerine (buton, checkbox, radio, text field, divider, chip vb.) otomatik olarak dağıtılır — ekran ekran renk ayarlamanıza gerek yoktur.
- Senaryo 3'te (tamamen özel ekranlar) bu palet otomatik uygulanmaz; kendi Composable'larınızda `MaterialTheme.colorScheme` üzerinden okuyarak aynı tutarlılığı sağlayabilirsiniz.

---

## Senaryo 1 — Hazır UI

Hiçbir şey yazmadan SDK'nın kendi ekranlarını kullan:

```kotlin
IdentifySdk.init(
    application = this,
    config = SdkConfig.Builder(baseUrl, turnKey)
        .setUiProvider(StandardUiProvider())
        .build()
)
```

---

## Senaryo 2 — Kısmi Override

`StandardUiProvider` sınıfı `final`'dır, yani doğrudan miras alınamaz (`class MyUiProvider : StandardUiProvider()` derlenmez). Kısmi override için `SdkUiProvider` interface'ini doğrudan implement edin, içeride bir `StandardUiProvider()` instance'ı tutun ve yalnızca değiştirmek istediğiniz ekranı özelleştirip geri kalan tüm ekranları bu instance'a delege edin:

```kotlin
class MyUiProvider : SdkUiProvider {

    private val standard = StandardUiProvider()

    @Composable
    override fun SelfieScreen(onNext: () -> Unit, onBack: () -> Unit) {
        val viewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
        val state by viewModel.uiState

        // Kendi UI'ınızı buraya yazın
        MyCustomSelfieScreen(
            state = state,
            onCapture = { bitmap -> viewModel.onPhotoCaptured(bitmap) },
            onConfirm = { viewModel.onConfirm() },
            onRetry  = { viewModel.onRetry() },
            onBack   = onBack
        )
    }

    // Değiştirmek istemediğiniz tüm diğer ekranlar — hazır UI'a tek satır delege
    @Composable
    override fun HandshakeScreen(identificationId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) =
        standard.HandshakeScreen(identificationId, onSuccess, onFailure)

    @Composable
    override fun IntroScreen(onNext: () -> Unit) = standard.IntroScreen(onNext)

    @Composable
    override fun PreparationScreen(onBack: () -> Unit) = standard.PreparationScreen(onBack)

    @Composable
    override fun DocumentSelectionScreen(onBack: () -> Unit) = standard.DocumentSelectionScreen(onBack)

    @Composable
    override fun DocumentCombinedScreen(onNext: () -> Unit, onBack: () -> Unit) =
        standard.DocumentCombinedScreen(onNext, onBack)

    @Composable
    override fun OvdScreen(onBack: () -> Unit) = standard.OvdScreen(onBack)

    @Composable
    override fun PassportScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.PassportScreen(onNext, onBack)

    @Composable
    override fun NfcScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.NfcScreen(onNext, onBack)

    @Composable
    override fun AgentCallScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.AgentCallScreen(onNext, onBack)

    @Composable
    override fun AddressScreen(onNext: () -> Unit, onBack: () -> Unit, initialAddress: String?) =
        standard.AddressScreen(onNext, onBack, initialAddress)

    @Composable
    override fun LivenessScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.LivenessScreen(onNext, onBack)

    @Composable
    override fun SignatureScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.SignatureScreen(onNext, onBack)

    @Composable
    override fun SpeechScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.SpeechScreen(onNext, onBack)

    @Composable
    override fun OtherDocumentScreen(onNext: () -> Unit, onBack: () -> Unit) =
        standard.OtherDocumentScreen(onNext, onBack)

    @Composable
    override fun VideoRecordScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.VideoRecordScreen(onNext, onBack)

    @Composable
    override fun ResultSuccessScreen(onFinish: () -> Unit) = standard.ResultSuccessScreen(onFinish)
}
```

```kotlin
.setUiProvider(MyUiProvider())
```

> **Not:** Az sayıda modül kullanıp yalnızca o ekranları özelleştirmek istiyorsanız (örn. sadece kimlik kartı + Speech), kullanmayacağınız modüllerin ekranlarını `standard.X(...)` yerine `error("...")` ile de bırakabilirsiniz — bu ekranlar `SdkConfig.setCustomModules(...)` listenizde yer almadığı sürece hiçbir zaman çağrılmaz, `error()` ise ileride yanlışlıkla modülü aktif ederseniz hatayı hemen fark etmenizi sağlar.

---

## Senaryo 3 — Tamamen Özel UI

`SdkUiProvider` interface'ini implement et. 17 ekranın tamamını yazmak zorundasın:

```kotlin
class FullCustomUiProvider : SdkUiProvider {

    @Composable
    override fun HandshakeScreen(
        identificationId: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        val viewModel: HandshakeViewModel = viewModel(factory = SdkViewModelFactory)
        // SDK handshake'i tetikle — bu çağrı olmadan akış başlamaz
        LaunchedEffect(identificationId) {
            viewModel.startHandshake(identificationId)
        }
        val state by viewModel.uiState
        // state: HandshakeUiState.Loading | Success | Error
        MyHandshakeScreen(state, onSuccess, onFailure)
    }

    @Composable
    override fun IntroScreen(onNext: () -> Unit) { /* ... */ }

    @Composable
    override fun PreparationScreen(onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun DocumentSelectionScreen(onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun DocumentCombinedScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun OvdScreen(onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun SelfieScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun PassportScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun NfcScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun AgentCallScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun AddressScreen(onNext: () -> Unit, onBack: () -> Unit, initialAddress: String?) { /* ... */ }

    @Composable
    override fun LivenessScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun SignatureScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun SpeechScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun OtherDocumentScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun VideoRecordScreen(onNext: () -> Unit, onBack: () -> Unit) { /* ... */ }

    @Composable
    override fun ResultSuccessScreen(onFinish: () -> Unit) { /* ... */ }
}
```

---

## Modül Seçimi ve Ekranların Tetiklenmesi

`SdkUiProvider` implementasyonunuzda tanımlı 17 metod **sabittir** — hangi modülleri kullandığınızdan bağımsız olarak hepsini yazmak zorundasınız (derleme şartı). Ancak akış sırasında **gerçekte hangi ekranın çağrılacağı**, `SdkConfig.Builder().setCustomModules(List<SdkModule>)` ile verdiğiniz modül listesine bağlıdır. Bu listeye dahil etmediğiniz modüllerin ekranları hiçbir koşulda tetiklenmez.

### Her zaman çalışan ekranlar (modülden bağımsız)

Aşağıdaki 3 ekran, `setCustomModules` listenizde ne olursa olsun akışın standart parçasıdır:

| Ekran | Ne zaman çalışır |
|---|---|
| `HandshakeScreen` | Akışın giriş noktası — her zaman |
| `IntroScreen` | `SdkConfig.isIntroEnabled = true` ise |
| `ResultSuccessScreen` | `SdkConfig.isThankYouEnabled = true` ise |

### Modüle bağlı ekranlar

| `SdkModule` | Tetiklenen ekran(lar) |
|---|---|
| `SdkModule.ID_CARD` | `DocumentSelectionScreen` + kullanıcının seçimine göre `DocumentCombinedScreen` / `PassportScreen` / `OtherDocumentScreen` |
| `SdkModule.ID_CARD_OVD` | Yalnızca `OvdScreen` (ön → hologram → arka, sabit akış) |
| `SdkModule.SELFIE` | `SelfieScreen` |
| `SdkModule.NFC` | `NfcScreen` |
| `SdkModule.LIVENESS` | `LivenessScreen` |
| `SdkModule.SPEECH` | `SpeechScreen` |
| `SdkModule.ADDRESS` | `AddressScreen` |
| `SdkModule.SIGNATURE` | `SignatureScreen` |
| `SdkModule.VIDEO_RECORD` | `VideoRecordScreen` |
| `SdkModule.AGENT_CALL` | `AgentCallScreen` |
| `SdkModule.PREPARE` | `PreparationScreen` |

**`ID_CARD` vs `ID_CARD_OVD` farkı:** `ID_CARD` kullanıcıya belge seçim ekranını gösterir (ID Kartı / Pasaport / Diğer arasında seçer). `ID_CARD_OVD` bu seçim ekranını tamamen atlar ve doğrudan sabit bir "kimlik kartı ön yüz → hologram → arka yüz" tarama akışı başlatır. Yalnızca kimlik kartıyla çalışacaksanız ve seçim ekranını göstermek istemiyorsanız `ID_CARD_OVD` kullanın.

> Şu an yalnızca kimlik kartı için seçim ekranını atlama desteklenmektedir. "Sadece pasaport" veya "sadece diğer belge" ile başlatıp seçim ekranını atlamak için ayrı bir config alanı planlanmaktadır (bkz. proje TODO listesi).

### Örnek: Sadece 2 modül kullanma

`setCustomModules(listOf(SdkModule.ID_CARD_OVD, SdkModule.SPEECH))` verildiğinde gerçekte tetiklenen ekranlar: `HandshakeScreen`, (varsa) `IntroScreen`, `OvdScreen`, `SpeechScreen`, (varsa) `ResultSuccessScreen` — yani 4-5 ekran. Kullanmadığınız diğer ~12 ekranı (`NfcScreen`, `SelfieScreen`, `SignatureScreen` vb.) `error("...")` ile stub bırakmak güvenlidir; bkz. Senaryo 2'deki not.

---

## ViewModel Erişimi

### Altın Kural

ViewModel constructor'ları `internal`'dır — `new SelfieViewModel()` **derlenmez**. Her zaman Compose'un `viewModel()` fonksiyonunu `SdkViewModelFactory` ile kullan:

```kotlin
val viewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
```

Factory, SDK'nın repository/session/navigator/socket bağımlılıklarını otomatik inject eder. Bu çağrı SDK'nın `IdentifyActivity` kapsamında çalıştığı için scope otomatik doğrudur — host uygulamanın Activity/Fragment'ından çağrılmamalıdır.

---

## Ekran Başına ViewModel Referansı

| `SdkUiProvider` Metodu | ViewModel | Kamera Analyzer |
|------------------------|-----------|-----------------|
| `HandshakeScreen` | `HandshakeViewModel` | — |
| `IntroScreen` | — | — |
| `PreparationScreen` | `PreparationViewModel` | — |
| `DocumentSelectionScreen` | `DocumentSelectionViewModel` | — |
| `DocumentCombinedScreen` | `DocumentScanViewModel` | `viewModel.cameraAnalyzer` |
| `OvdScreen` | `OvdViewModel` | `viewModel.docCameraAnalyzer` / `viewModel.ovdCameraAnalyzer` |
| `SelfieScreen` | `SelfieViewModel` | `viewModel.cameraAnalyzer` |
| `PassportScreen` | `PassportScanViewModel` | `viewModel.cameraAnalyzer` |
| `NfcScreen` | `NfcViewModel` | — (NFC tag listener) |
| `AgentCallScreen` | `AgentCallViewModel` | — |
| `AddressScreen` | `AddressViewModel` | — |
| `LivenessScreen` | `LivenessViewModel` | `viewModel.cameraAnalyzer` |
| `SignatureScreen` | `SignatureViewModel` | — |
| `SpeechScreen` | `SpeechViewModel` | — |
| `OtherDocumentScreen` | `OtherDocumentViewModel` | — |
| `VideoRecordScreen` | `VideoRecordViewModel` | — |
| `ResultSuccessScreen` | — | — |

Kamera içeren ekranlarda `viewModel.cameraAnalyzer`'ı CameraX `ImageAnalysis`'e bağla:

```kotlin
ImageAnalysis.Builder().build().also {
    it.setAnalyzer(cameraExecutor, viewModel.cameraAnalyzer)
}
```

---

## ViewModel State Detayları

### SelfieViewModel

```kotlin
val viewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // SelfieViewModel.SelfieUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `selfieState` | `SelfieState` | `SCANNING` / `PREVIEW` / `UPLOADING` / `COMPLETED` |
| `readyToCapture` | `Boolean` | Yüz stabil, fotoğraf tetiklenebilir |
| `capturedBitmap` | `Bitmap?` | Çekilen ham fotoğraf |
| `croppedBitmap` | `Bitmap?` | Yüz kırpılmış fotoğraf |
| `isFaceInsideGuide` | `Boolean` | Yüz rehber çerçevesinde mi |
| `isWellPositioned` | `Boolean` | Yüz stabil ve düzgün konumda mı |
| `guideInstruction` | `FaceGuideInstruction` | `NO_FACE` / `MOVE_CLOSER` / `HOLD_STILL` vb. |
| `meshPoints` | `List<FaceMeshPoint>` | Yüz ağı noktaları (overlay için) |
| `errorMessage` | `String?` | Upload veya ağ hatası mesajı |
| `comparisonWarning` | `String?` | Yüz eşleşme uyarısı (1-2/3) |

**Public Metodlar:**

| Metod | Ne Zaman Çağrılır |
|-------|-------------------|
| `onPhotoCaptured(bitmap)` | `ImageCapture.takePicture()` başarıyla tamamlandığında |
| `onPhotoCaptureFailed()` | Kamera hatası aldığında |
| `onConfirm()` | Kullanıcı önizlemede "Onayla"ya tıkladığında |
| `onRetry()` | Kullanıcı yeniden çekmek istediğinde |
| `clearError()` | Hata dialogu kapatıldığında |
| `clearComparisonWarning()` | Uyarı dialogu kapatıldığında |
| `onBackPress()` | Geri tuşuna basıldığında |

**Örnek akış:**

```kotlin
@Composable
override fun SelfieScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val viewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
    val state by viewModel.uiState

    when (state.selfieState) {
        SelfieState.SCANNING -> CameraPreviewWithGuide(
            analyzer = viewModel.cameraAnalyzer,
            isReady  = state.readyToCapture,
            onCapture = { bitmap -> viewModel.onPhotoCaptured(bitmap) },
            onFail    = { viewModel.onPhotoCaptureFailed() }
        )
        SelfieState.PREVIEW -> SelfiePreview(
            bitmap     = state.capturedBitmap,
            onConfirm  = { viewModel.onConfirm() },
            onRetry    = { viewModel.onRetry() }
        )
        SelfieState.UPLOADING -> LoadingOverlay()
        SelfieState.COMPLETED -> { /* SDK otomatik ilerler */ }
    }

    state.errorMessage?.let {
        ErrorDialog(message = it, onDismiss = { viewModel.clearError() })
    }
    state.comparisonWarning?.let {
        WarningDialog(message = it, onDismiss = { viewModel.clearComparisonWarning() })
    }
}
```

---

### DocumentScanViewModel (Kimlik Kartı Tarama)

```kotlin
val viewModel: DocumentScanViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // DocumentScanViewModel.DocumentScanUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `frontState` | `CaptureState` | `IDLE` / `UPLOADING` / `SUCCESS` / `ERROR` / `COMPARISON_WARNING` |
| `backState` | `CaptureState` | Aynı |
| `currentCaptureMode` | `CaptureMode` | `FRONT` / `BACK` — hangi yüz bekleniyor |
| `frontImage` | `Bitmap?` | Çekilen ön yüz |
| `backImage` | `Bitmap?` | Çekilen arka yüz |
| `guidanceMessage` | `String` | Anlık kılavuz mesajı |
| `scanErrorMessage` | `String?` | Hata açıklaması (`id_not_detected` vb.) |
| `comparisonWarning` | `String?` | Form eşleşme uyarısı |
| `nonIdWarning` | `Boolean` | Kimlik dışı nesne algılandı uyarısı |
| `manualFallbackEnabled` | `Boolean` | Manuel çek butonu aktif mi |
| `isUploading` | `Boolean` | Upload sürüyor |
| `canProceed` | `Boolean` | Her iki yüz hazır ve akış ilerleyebilir |

**Public Metodlar:**

| Metod | Ne Zaman Çağrılır |
|-------|-------------------|
| `startScanning()` | Ekran ilk açıldığında |
| `triggerManualCapture()` | Manuel çek butonuna tıklandığında |
| `onNextClicked()` | `canProceed == true` iken "İlerle"ye tıklandığında |
| `clearErrors()` | Hata temizlendiğinde |
| `clearComparisonWarning()` | Uyarı dialogu kapatıldığında |
| `clearNonIdWarning()` | Kimlik değil uyarısı kapatıldığında |
| `setViewport(screenW, screenH, guideRect)` | Kamera önizleme alanı ölçüldüğünde |

---

### OvdViewModel (Hologram Tarama)

```kotlin
val viewModel: OvdViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // OvdViewModel.OvdUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `currentStep` | `OvdStep` | `FRONT` / `OVD` / `BACK` |
| `showPreview` | `Boolean` | Önizleme ekranı gösterilmeli mi |
| `frontImage` | `Bitmap?` | Ön yüz (onay öncesi erişilebilir) |
| `ovdImage` | `Bitmap?` | Holografik yüzey fotoğrafı |
| `backImage` | `Bitmap?` | Arka yüz |
| `guidanceMessage` | `String` | Kılavuz mesajı |
| `scanErrorMessage` | `String?` | Hata mesajı |
| `isUploading` | `Boolean` | Upload sürüyor |
| `failedStep` | `OvdStep?` | Başarısız olan adım |
| `manualFallbackEnabled` | `Boolean` | Manuel çek butonu |

**Public Metodlar:**

| Metod | Ne Zaman Çağrılır |
|-------|-------------------|
| `startFlow()` | Ekran açıldığında |
| `triggerManualCapture()` | Manuel çek butonunda |
| `onConfirmImages()` | Kullanıcı 3 fotoğrafı onayladığında — upload başlar |
| `onRetake()` | Baştan başla — tüm fotoğrafları sıfırlar |
| `retryFailedStep()` | Başarısız adımı tekrarla |
| `setViewport(screenW, screenH, guideRect)` | Viewport değiştiğinde |

**Akış:**  
FRONT → OVD → BACK → `showPreview = true` → kullanıcı onaylar → `onConfirmImages()` → upload

---

### LivenessViewModel

```kotlin
val viewModel: LivenessViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // LivenessViewModel.LivenessUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `currentStep` | `LivenessStep` | `TURN_RIGHT` / `TURN_LEFT` / `SMILE` / `BLINK` |
| `isFaceDetected` | `Boolean` | Yüz algılandı mı |
| `yaw` | `Float` | Baş sol/sağ dönüş açısı |
| `isUploading` | `Boolean` | Adım yükleniyor |
| `errorMessage` | `String?` | Hata metni |
| `comparisonWarning` | `String?` | Adım uyarısı |

**Public Metodlar:** `clearError()`, `clearComparisonWarning()`, `onBackPress()`  
Kamera bağlantısı: `viewModel.cameraAnalyzer`

**Not:** Liveness akışı artık oval/çerçeve konum hizalaması istemez — yüz herhangi bir konumda
algılandığı sürece adım talimatı (`sağa dönün` vb.) geçerlidir ve gerçek kural (yaw/gülümseme/göz
kırpma eşiği) sağlandığında adım tamamlanır. Konum bazlı `FACE_MOVE_*` guide mesajları bu akışta
tetiklenmez (yalnızca Selfie'de kullanılır, bkz. `docs/guidance-messages.md`).

---

### NfcViewModel

```kotlin
val viewModel: NfcViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // NfcUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `status` | `NfcStatus` | `IDLE` / `WAITING_FOR_TAG` / `READING` / `VERIFYING` / `SUCCESS` / `READ_ERROR` / `NFC_DISABLED` vb. |
| `progress` | `Int` | Okuma ilerlemesi (0-100) |
| `attemptCount` | `Int` | Deneme sayısı — çip okuma limiti (`SdkConfig.nfcExceptionCount`) ve comparison limiti (sunucudan gelen `nfcComparisonCount`) ayrı ayrı, birbirinden bağımsız kontrol edilir; birleşik bir "maksimum deneme" alanı yoktur |
| `error` | `NfcError?` | Hata detayı |
| `outcome` | `NfcOutcome?` | `null` = devam ediyor / `Success` / `SkipModule` / `AbortToFailureScreen` |

**Public Metodlar:**

| Metod | Ne Zaman Çağrılır |
|-------|-------------------|
| `onStartScanClicked()` | Kullanıcı NFC taramayı başlattığında |
| `onNfcTagDiscovered(tag, context)` | `NfcAdapter` callback'inden |
| `onNfcEnabled()` | NFC sistem ayarlarından açıldığında |
| `onNfcDisabled()` | NFC kapalı olduğunda |
| `onNfcNotAvailable()` | Cihazda NFC yoksa |
| `onManualMrzSubmitted(docNo, dob, expiry)` | Kullanıcı MRZ'yi manuel girdiyse |
| `onRetryClicked()` | Yeniden dene butonunda |
| `onNextClicked()` | `outcome != null` iken SDK'ya ilerle sinyali ver |
| `onBackClicked()` | Geri butonunda |
| `hasMrzData()` | MRZ verisi session'da mevcut mu (NFC başlamadan önce kontrol) |

**`outcome` alanını işle:**

```kotlin
LaunchedEffect(state.outcome) {
    when (state.outcome) {
        is NfcOutcome.Success      -> viewModel.onNextClicked()
        is NfcOutcome.SkipModule   -> viewModel.onNextClicked() // modülü atla
        is NfcOutcome.AbortToFailureScreen -> { /* SDK otomatik yönetir */ }
        null -> { /* Devam ediyor */ }
    }
}
```

---

### AgentCallViewModel

```kotlin
val viewModel: AgentCallViewModel = viewModel(factory = SdkViewModelFactory)
val callState by viewModel.callState.collectAsState()  // AgentCallState (sealed)
```

| State | Açıklama |
|-------|----------|
| `AgentCallState.Waiting(queuePosition, estimatedWaitTimeMinutes)` | Sırada bekliyor |
| `AgentCallState.IncomingCall(sdpType, sdpDescription)` | Temsilci arıyor |
| `AgentCallState.InCall(localTrack, remoteTrack, isMicEnabled, currentAudioDevice)` | Görüşme aktif |
| `AgentCallState.ConnectionLost` | Bağlantı kesildi |
| `AgentCallState.MissedCall` | Temsilci cevap vermedi |

---

### AddressViewModel

```kotlin
val viewModel: AddressViewModel = viewModel(factory = SdkViewModelFactory)
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `address` | `State<String>` | Girilen adres metni |
| `selectedFileUri` | `State<Uri?>` | Seçilen belge URI'ı |
| `selectedFileType` | `State<String?>` | `"pdf"` veya `"image"` |
| `isLoading` | `State<Boolean>` | Upload sürüyor |
| `error` | `State<String?>` | Hata mesajı |
| `canContinue` | `State<Boolean>` | Adres + dosya seçildi ve yükleme yok |

**Public Metodlar:** `onAddressChanged(text)`, `onFileSelected(uri, type)`, `onContinueClicked()`

---

### SignatureViewModel

```kotlin
val viewModel: SignatureViewModel = viewModel(factory = SdkViewModelFactory)
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `paths` | `SnapshotStateList<PathState>` | Çizilen yollar (Compose Canvas için) |
| `currentPath` | `State<Path>` | Aktif çizim yolu |
| `isSigned` | `State<Boolean>` | En az bir çizgi var mı |
| `isLoading` | `State<Boolean>` | Upload sürüyor |
| `error` | `State<String?>` | Hata mesajı |

---

### SpeechViewModel

```kotlin
val viewModel: SpeechViewModel = viewModel(factory = SdkViewModelFactory)
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `targetWord` | `State<String>` | Kullanıcının söylemesi gereken kelime (socket'tan gelir) |
| `recognizedText` | `State<String>` | Tanınan metin |
| `isListening` | `State<Boolean>` | Mikrofon aktif mi |
| `isMatch` | `State<Boolean>` | Söylenen kelime eşleşti mi |
| `error` | `State<String?>` | Hata mesajı |

---

### VideoRecordViewModel

```kotlin
val viewModel: VideoRecordViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // VideoRecordUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `screenState` | `VideoRecordScreenState` | `READY` / `RECORDING` / `PREVIEW` / `UPLOADING` |
| `sentence` | `String?` | Okunacak cümle (varsa) |
| `remainingTimeMs` | `Long` | Kalan kayıt süresi (ms) |
| `videoUri` | `Uri?` | Kaydedilen video URI'ı |
| `isPlaying` | `Boolean` | Önizleme oynatılıyor mu |
| `error` | `String?` | Hata mesajı |

**Public Metodlar:** `onRecordingStarted()`, `onRecordingFinished(uri, file)`, `onRecordingError(msg)`, `togglePlayback()`, `retryRecording()`, `confirmVideo()`

---

### PassportScanViewModel

```kotlin
val viewModel: PassportScanViewModel = viewModel(factory = SdkViewModelFactory)
val state by viewModel.uiState  // PassportScanUiState
```

| Alan | Tip | Açıklama |
|------|-----|----------|
| `isScanning` | `Boolean` | Kamera analizi aktif |
| `isUploading` | `Boolean` | Upload sürüyor |
| `capturedImage` | `Bitmap?` | Çekilen pasaport fotoğrafı |
| `isSuccess` | `Boolean` | MRZ başarıyla okundu |
| `error` | `String?` | Hata mesajı |
| `guidanceMessage` | `String` | Kılavuz mesajı |
| `manualFallbackEnabled` | `Boolean` | Manuel çek butonu |

Kamera bağlantısı: `viewModel.cameraAnalyzer`

---

## Sıkça Sorulan Sorular

**S: ViewModel'i `viewModel(factory = SdkViewModelFactory)` yerine farklı nasıl alabilirim?**  
A: Alamazsınız. Constructor'lar `internal`'dır ve SDK dışından erişilemez. Yalnızca `SdkViewModelFactory` ile alınabilir. Bu, bağımlılıkların doğru inject edilmesini ve scope'un doğru olmasını garanti eder.

**S: Kendi ViewModel'imi SDK ekranıyla yan yana kullanabilir miyim?**  
A: Evet. Composable içinde hem SDK'nın ViewModel'ini hem de kendi ViewModel'ini alabilirsin:

```kotlin
@Composable
override fun SelfieScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val sdkViewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
    val myViewModel: MyAnalyticsViewModel = viewModel()  // kendi factory'n
    // ...
}
```

**S: `onNext` callback'ini kendim tetikleyebilir miyim?**  
A: Evet, ancak dikkatli ol. `onNext`'i yalnızca işlem gerçekten tamamlandığında çağır. Erken çağırmak SDK'nın eksik veriyle bir sonraki modüle geçmesine yol açar. Genellikle ViewModel'in state'i (`isSuccess`, `COMPLETED` vb.) `true` olunca `LaunchedEffect` ile tetikle:

```kotlin
LaunchedEffect(state.selfieState) {
    if (state.selfieState == SelfieState.COMPLETED) onNext()
}
```

**S: `IntroScreen` veya `ResultSuccessScreen` için ViewModel gerekiyor mu?**  
A: Hayır. Bu ekranlar bilgilendirme/yönlendirme içeriklidir. `onNext()` veya `onFinish()` callback'ini doğrudan buton tıklamasına bağlayabilirsin.

**S: Hangi ekranlarda kamera gerekiyor?**  
A: `DocumentCombinedScreen`, `OvdScreen`, `SelfieScreen`, `PassportScreen`, `LivenessScreen`. Bu ekranların ViewModel'lerinde `cameraAnalyzer` property'si mevcuttur. `OvdScreen`'de iki ayrı analyzer var: `docCameraAnalyzer` (FRONT/BACK adımları) ve `ovdCameraAnalyzer` (OVD adımı).

**S: SDK ekranları dışında SDK'nın navigasyonuna müdahale edebilir miyim?**  
A: Hayır. Navigasyon `SdkNavigator` üzerinden yönetilir ve `internal`'dır. Akışı yalnızca `onNext` / `onBack` callback'leriyle yönlendirebilirsin. Modül sırasını yalnızca `SdkConfig.Builder.setCustomModules()` ile başlangıçta ayarlayabilirsin.
