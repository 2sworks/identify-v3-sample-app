# Kimlik Kartı — Tek Ekranda Ön + Arka Yüz Çekimi

Bu döküman, `DocumentCombinedScreen`'i override eden müşterilerin şu akışı nasıl kuracağını
gösterir:

> Kamera açılır → **ön yüz** çekilir ve **aynı ekranda** gösterilir → panele dönmeden
> **arka yüz** çekilir ve yine **aynı ekranda** gösterilir → **Devam Et**.

SDK'nın hazır ekranı (`StandardUiProvider` → `DocumentCombinedScreen`) ve sample'daki
[`MyIdScreen`](../app/src/main/kotlin/com/identify/sample/ui/MyIdScreen.kt) iki parçalı çalışır:
bir **panel** (ön/arka kartları) ve bir **kamera** ekranı. Her yüz yakalandıktan sonra kullanıcı
panele döner ve diğer kartı elle seçer. Bu dökümandaki yaklaşımda panel yoktur.

**Rehber çerçeveyi ve ROI'yi siz çizmezsiniz.** Tarama adımında SDK'nın public
`DocumentScanScreen` bileşeni kullanılır. Kamera, çerçeve, `setViewport` senkronu, tarama efekti,
sesli yönlendirme, hata mesajı ve manuel çekim butonu SDK'dadır. Sizin yazdığınız kısım yalnızca
yakalanan karenin gösterildiği önizleme görünümü ve akış mantığıdır. OCR, otomatik yakalama,
anlık upload ve ön/arka çapraz doğrulama da `DocumentScanViewModel`'de çalışmaya devam eder.

> Genel override mekanizması (`SdkUiProvider`, `StandardUiProvider`'a delege etme) için:
> [UI Özelleştirme](ui-customization.md).

---

## İçindekiler

1. [Akış](#akış)
2. [Kullanacağınız API yüzeyi](#kullanacağınız-api-yüzeyi)
3. [Dikkat edilmesi gereken 4 kural](#dikkat-edilmesi-gereken-4-kural)
4. [Tam örnek](#tam-örnek)
5. [Provider'a bağlama](#providera-bağlama)
6. [Varyasyonlar](#varyasyonlar)
7. [Hata ve uyarı durumları](#hata-ve-uyarı-durumları)
8. [Sıkça sorulanlar](#sıkça-sorulanlar)

---

## Akış

Ekran tek bir composable'dır. İçinde, o anki yüzün görseli olup olmamasına göre iki görünüm
arasında geçiş yapılır:

```
 currentImage == null  →  DocumentScanScreen (SDK: kamera + çerçeve + ROI)
 currentImage != null  →  CapturedReview     (sizin: çekilen kare + durum + butonlar)
```

```
 Ekran açılır
   │  prepareForCapture(FRONT) → setCaptureMode(FRONT) → startScanning()
   ▼
 [ÖN YÜZ TARANIYOR]           DocumentScanScreen
   │  SDK otomatik yakalar → frontImage dolar, frontState = UPLOADING
   ▼
 [ÖN YÜZ ÖNİZLEME]            CapturedReview (kamera kapanır)
   │  spinner → frontState = SUCCESS → ✓ "Ön yüz alındı, kartı çevirin" (~1,5 sn)
   │  hata/uyarı → dialog → tekrar çek
   │  prepareForCapture(BACK) → setCaptureMode(BACK) → startScanning()
   ▼
 [ARKA YÜZ TARANIYOR]         DocumentScanScreen (ön yüz köşede küçük önizleme)
   │  SDK otomatik yakalar → backImage dolar, backState = UPLOADING
   ▼
 [ARKA YÜZ ÖNİZLEME]          CapturedReview
   │  backState = SUCCESS, canProceed = true
   ▼
 [Devam Et]  viewModel.onNextClicked()  → SDK bir sonraki modüle geçer
```

---

## Kullanacağınız API yüzeyi

### `DocumentScanScreen` (`com.identify.sdk.ui.standard.scan`, `sdk-ui-default`)

```kotlin
DocumentScanScreen(
    viewModel = viewModel,        // aynı DocumentScanViewModel instance'ı
    onNext = {},                  // yakalama sinyali; bu akışta state'e bakıldığı için boş
    scanColor = Color(0xFF60A5FA),  // çerçeve rengi (tarama)
    successColor = Color(0xFF41D97F),
    errorColor = Color(0xFFFF453A)
)
```

Composition'a girdiğinde `startScanning()` çağırır. Mevcut yüzün görseli dolunca `onNext`'i
çağırır. Composition'dan çıkınca kamerayı unbind eder ve analyzer'ı temizler.

### `DocumentScanViewModel` fonksiyonları (SDK 3.5.5)

ViewModel'i yalnızca `viewModel(factory = SdkViewModelFactory)` ile alabilirsiniz.

| Fonksiyon | Ne yapar | Bu akışta ne zaman |
|---|---|---|
| `prepareForCapture(mode)` | O yüzün state'ini `IDLE`'a çeker, "geçersiz belge" kilidini sıfırlar | Her yüzün çekimine başlamadan ve **her tekrar çekimde** |
| `setCaptureMode(mode)` | Analyzer'ı FRONT/BACK moduna alıp sıfırlar, `currentCaptureMode`'u günceller. **BACK'e geçiş yalnızca `frontState` `SUCCESS` veya `COMPARISON_WARNING` ise kabul edilir**, aksi halde sessizce yok sayılır | Yüz değişiminde |
| `startScanning()` | `isScanning = true`, frame buffer'ı temizler, **o anki moda ait görseli siler** (FRONT → `frontImage`, BACK → `backImage`) | `setCaptureMode`'dan hemen sonra |
| `onNextClicked()` | `canProceed` ise modülü COMPLETED işaretler ve **SDK navigator ile ilerler** | "Devam Et" |
| `clearComparisonWarning()` / `clearNonIdWarning()` / `clearErrors()` | İlgili dialog alanını temizler | Dialog kapatılırken (ardından tekrar çekimi başlatın) |

`triggerManualCapture()` ve `setViewport()` bu akışta sizin tarafınızdan çağrılmaz. İkisini de
`DocumentScanScreen` kendisi kullanır.

> Composable'a verilen `onNext` lambdası bu akışta **çağrılmaz**; navigasyonu
> `onNextClicked()` kendisi yapar (bkz. `MyIdScreen.kt` KDoc'u).

### `uiState` alanları

| Alan | Anlamı |
|---|---|
| `currentCaptureMode` | `FRONT` / `BACK` — şu an hangi yüz taranıyor |
| `frontImage`, `backImage` | Yakalanan kart görselleri. **Yakalandığı anda** (upload başlamadan) dolar. Görünüm geçişinin anahtarı budur |
| `portraitImage` | Ön yüzden kırpılan vesikalık |
| `frontState`, `backState` | `IDLE` → `UPLOADING` → `SUCCESS` (veya `COMPARISON_WARNING` / `ERROR`) |
| `isScanning` | Analyzer frame'leri işliyor mu |
| `isUploading` | Upload sürüyor mu |
| `comparisonWarning` | Backend OCR karşılaştırma uyarısı (3. uyarıda SDK otomatik kabul eder) |
| `comparisonErrors` | Upload/doğrulama hataları listesi |
| `nonIdWarning` | Karede kimlik kartı bulunamadı |
| `canProceed` | İki yüz de tamam → "Devam Et" aktif edilebilir |

`guidanceMessage`, `scanErrorMessage` ve `manualFallbackEnabled` alanlarını `DocumentScanScreen`
zaten gösterir; ayrıca göstermeniz gerekmez.

---

## Dikkat edilmesi gereken 4 kural

### 1. Yakalamadan sonra `DocumentScanScreen`'i composition'dan çıkarın

`DocumentScanViewModel` yakalamadan sonra `isScanning`'i `true` bırakır. Kamera açık kalırsa,
`frontState` `SUCCESS` olduktan sonra gelen frame'ler **aynı yüzü tekrar yakalayıp tekrar
yükleyebilir** (tekrar yakalama yalnızca `UPLOADING` sırasında engellenir). `DocumentScanScreen`
analyzer'ı dışarı açık değildir, yani onu duraklatamazsınız. Composition'dan çıkarmak ise kamerayı
kapatır. Bu yüzden `DocumentScanScreen`'i önizleme görünümünün üstüne **bindirmeyin**; `if/else`
ile değiştirin:

```kotlin
if (currentImage == null) {
    DocumentScanScreen(viewModel = viewModel, onNext = {})
} else {
    CapturedReview(bitmap = currentImage, state = currentState, message = …)
}
```

Arka yüze geçerken kamera yeniden bağlandığı için kısa bir siyah an olabilir. Beklenen davranış
budur.

### 2. Arka yüze geçişi `frontState == SUCCESS` ile tetikleyin, `frontImage != null` ile değil

`frontImage` upload **başlamadan** dolar. Bu sırada `setCaptureMode(BACK)` çağrılırsa SDK
geçişi reddeder (ön yüz henüz `SUCCESS` değil). Doğru tetik:

```kotlin
LaunchedEffect(mode, uiState.frontState) {
    if (mode == CaptureMode.FRONT && uiState.frontState == CaptureState.SUCCESS) {
        delay(1500)                 // kullanıcı ön yüzü görsün
        startSide(CaptureMode.BACK)
    }
}
```

### 3. Her yüz başlangıcında üç çağrıyı birlikte ve bu sırayla yapın

```kotlin
fun startSide(side: CaptureMode) {
    viewModel.prepareForCapture(side)   // state → IDLE, kilitleri sıfırla
    viewModel.setCaptureMode(side)      // analyzer modu + currentCaptureMode
    viewModel.startScanning()           // isScanning = true, o yüzün eski görselini sil
}
```

Aynı fonksiyon **tekrar çek** için de kullanılır. `startScanning()` görseli sildiği için ekran
kendiliğinden `DocumentScanScreen`'e döner. Sadece `startScanning()` çağırmak yetmez: ör.
doğrulama hatasından sonra o yüzün state'i `UPLOADING`'de kalabilir. Bu durumda
`prepareForCapture` çağrılmazsa yeni yakalama engellenir.

### 4. "Devam Et"i yalnızca `canProceed`'e bağlamayın

`canProceed` sadece upload bittiğinde yeniden hesaplanır. Kullanıcı iki yüzü tamamladıktan sonra
"tekrar çek" derse, yeni upload bitene kadar eski `true` değeri kalır. Kendi koşulunuzu ekleyin:

```kotlin
val canFinish = mode == CaptureMode.BACK &&
    uiState.backImage != null &&
    currentState != CaptureState.UPLOADING &&
    !uiState.isUploading &&
    uiState.canProceed
```

---

## Tam örnek

Aşağıdaki kod SDK **3.5.5** ile derlenmiştir ve sample app'te
[`MySingleScreenIdScreen.kt`](../app/src/main/kotlin/com/identify/sample/ui/MySingleScreenIdScreen.kt)
olarak bulunur. Numaralı yorumlar (1)–(6) akıştaki adımlara karşılık gelir.

Ekran düzeni:

```
   Tarama (SDK)                        Önizleme (sizin)
┌──────────────────────────────┐   ┌──────────────────────────────┐
│ ←     Kimlik Ön Yüzü  [ön]*  │   │ ←                            │
│   Kartı çerçeveye hizalayın  │   │                              │
│  ┌────────────────────────┐  │   │  ┌────────────────────────┐  │
│  │  canlı kamera          │  │   │  │  çekilen kare          │  │
│  │  (SDK çerçevesi)       │  │   │  │  spinner → ✓           │  │
│  └────────────────────────┘  │   │  └────────────────────────┘  │
│                              │   │  Ön yüz alındı, kartı çevirin│
│   [Manuel Çek] (gerekirse)   │   │  ┌────────┐  ┌────────┐      │
│                              │   │  │ Ön Yüz │  │Arka Yüz│      │
└──────────────────────────────┘   │  [ Devam Et ]                │
  * arka yüz taranırken            └──────────────────────────────┘
```

```kotlin
package com.identify.sample.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.scan.DocumentScanViewModel
import com.identify.sdk.presentation.scan.DocumentScanViewModel.CaptureMode
import com.identify.sdk.presentation.scan.DocumentScanViewModel.CaptureState
import com.identify.sdk.ui.standard.scan.DocumentScanScreen
import kotlinx.coroutines.delay

// SDK'nın DocumentScanScreen rehber çerçevesiyle aynı oran/genişlik — önizleme kartı, kullanıcının
// az önce kartı hizaladığı yerde görünsün diye.
private const val SDK_FRAME_WIDTH = 0.93f
private const val SDK_FRAME_RATIO = 1.58f
private const val FLIP_DELAY_MS = 1500L
private const val STALLED_RETRY_MS = 2000L

private val Accent = Color(0xFF60A5FA)
private val Success = Color(0xFF41D97F)
private val Danger = Color(0xFFFF453A)

/**
 * [MyIdScreen]'in panelsiz alternatifi — `docs/id-card-single-screen-capture.md`'nin referans
 * implementasyonu. Kullanıcı panele dönmeden, tek ekranda: ön yüzü tarar → çekilen kare aynı
 * ekranda gösterilir → arka yüzü tarar → o da gösterilir → "Devam Et".
 *
 * ## Çerçeveyi SDK çiziyor
 *
 * Tarama adımında SDK'nın public `DocumentScanScreen`'i kullanılıyor ([IdCameraOverlaySdkReused] ile
 * aynı yaklaşım): kamera, rehber çerçeve, `setViewport` ROI senkronu, `ScanningEffect`, TTS, hata
 * mesajı ve manuel çekim butonu SDK'da. Bizde `Canvas`/`RectF` hesabı yok.
 *
 * ## Neden yakalamadan sonra `DocumentScanScreen` composition'dan çıkarılıyor
 *
 * `DocumentScanViewModel` yakalamadan sonra `isScanning`'i `true` bırakıyor; kamera açık kalırsa
 * `SUCCESS` sonrası gelen frame'ler aynı yüzü tekrar yakalayıp tekrar yükleyebilir. `DocumentScanScreen`
 * analyzer'ı dışarı açmadığı için onu susturamayız — ama composition'dan çıkınca SDK'nın `CameraPreview`'i
 * analyzer'ı temizleyip kamerayı unbind ediyor. Bu yüzden ekran iki görünüm arasında geçiş yapıyor:
 * mevcut yüzün görseli yoksa [DocumentScanScreen], varsa kendi [CapturedReview]'ımız. Kullanıcı için
 * aynı ekran; arka yüze geçerken kamera yeniden bağlanıyor (kısa bir siyah an olabilir).
 *
 * `DocumentScanScreen`'in `onNext`'i (yakalama sinyali) boş bırakılıyor — geçişi zaten
 * `frontImage`/`backImage` state'ine bakarak yapıyoruz.
 *
 * ## Diğer notlar
 * - Arka yüze geçiş `frontState == SUCCESS`'e bağlı: `frontImage` upload başlamadan dolar, ama
 *   `setCaptureMode(BACK)` ön yüz `SUCCESS` (veya `COMPARISON_WARNING`) değilse sessizce yok sayılır.
 * - `canProceed` yalnızca upload bitince yeniden hesaplanıyor → "tekrar çek" sonrası bayat `true`
 *   kalmasın diye `canFinish` kendi ek koşullarımızı içeriyor.
 * - `onNext` — [MyIdScreen]'de olduğu gibi — hiç çağrılmıyor; navigasyonu `onNextClicked()` yapıyor.
 *
 * [SampleUiProvider]'daki `DocumentCombinedScreen(...)` override'ı bu ekranı kullanıyor — ana ekranda
 * "Custom UI Provider Demo" switch'ini açıp kimlik modülüyle akışı başlatın.
 */
@Composable
fun MySingleScreenIdScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val viewModel: DocumentScanViewModel = viewModel(factory = SdkViewModelFactory)
    val uiState by viewModel.uiState

    val mode = uiState.currentCaptureMode
    val currentImage = if (mode == CaptureMode.FRONT) uiState.frontImage else uiState.backImage
    val currentState = if (mode == CaptureMode.FRONT) uiState.frontState else uiState.backState

    fun startSide(side: CaptureMode) {
        viewModel.prepareForCapture(side)
        viewModel.setCaptureMode(side)
        viewModel.startScanning()
    }

    // (1) Ekran ilk açıldığında ön yüz taramasını başlat (rotation'da tekrar sıfırlama).
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            startSide(CaptureMode.FRONT)
            started = true
        }
    }

    // (2) Ön yüz backend'den SUCCESS aldı → kısa süre göster, aynı ekranda arka yüze geç.
    LaunchedEffect(mode, uiState.frontState) {
        if (mode == CaptureMode.FRONT && uiState.frontState == CaptureState.SUCCESS) {
            delay(FLIP_DELAY_MS)
            startSide(CaptureMode.BACK)
        }
    }

    // (3) Ehliyet/pasaport gibi geçersiz belge: SDK taramayı durdurur ve mesajını kendi ekranında
    // gösterir ama kendiliğinden devam etmez → mesaj okunduktan sonra taramayı yeniden başlat.
    val hasDialog = uiState.comparisonWarning != null || uiState.nonIdWarning || uiState.comparisonErrors != null
    val stalled = started && !uiState.isScanning && currentImage == null && !hasDialog
    LaunchedEffect(stalled) {
        if (stalled) {
            delay(STALLED_RETRY_MS)
            startSide(mode)
        }
    }

    val canFinish = mode == CaptureMode.BACK &&
        uiState.backImage != null &&
        currentState != CaptureState.UPLOADING &&
        !uiState.isUploading &&
        uiState.canProceed

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1120))) {
        if (currentImage == null) {
            // (4) Tarama: kamera + çerçeve + ROI tamamen SDK'da.
            DocumentScanScreen(
                viewModel = viewModel,
                onNext = {},
                scanColor = Accent,
                successColor = Success,
                errorColor = Danger
            )
            // Tarama sırasında daha önce çekilen ön yüz köşede görünmeye devam eder.
            if (mode == CaptureMode.BACK && uiState.frontImage != null) {
                SideThumbnail(
                    label = "Ön Yüz",
                    bitmap = uiState.frontImage,
                    state = uiState.frontState,
                    active = false,
                    enabled = !uiState.isUploading,
                    onRetake = { startSide(CaptureMode.FRONT) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).width(96.dp)
                )
            }
        } else {
            // (5) Yakalandı: kamera kapalı, çekilen kare aynı ekranda.
            CapturedReview(
                bitmap = currentImage,
                state = currentState,
                message = when {
                    mode == CaptureMode.FRONT && uiState.frontState == CaptureState.SUCCESS ->
                        "Ön yüz alındı. Şimdi kartı çevirin."
                    canFinish -> "Her iki yüz de alındı."
                    currentState == CaptureState.UPLOADING -> "Kontrol ediliyor…"
                    else -> ""
                }
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // (6) Ön/arka küçük önizlemeler — dokununca o yüz tekrar çekilir.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SideThumbnail(
                        label = "Ön Yüz",
                        bitmap = uiState.frontImage,
                        state = uiState.frontState,
                        active = mode == CaptureMode.FRONT,
                        enabled = !uiState.isUploading && uiState.frontImage != null,
                        onRetake = { startSide(CaptureMode.FRONT) },
                        modifier = Modifier.weight(1f)
                    )
                    SideThumbnail(
                        label = "Arka Yüz",
                        bitmap = uiState.backImage,
                        state = uiState.backState,
                        active = mode == CaptureMode.BACK,
                        enabled = !uiState.isUploading && mode == CaptureMode.BACK && uiState.backImage != null,
                        onRetake = { startSide(CaptureMode.BACK) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (canFinish) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.onNextClicked() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) { Text("Devam Et", color = Color.Black, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { startSide(CaptureMode.BACK) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text("Arka Yüzü Tekrar Çek", color = Color.White) }
                }
            }
        }

        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
        }
    }

    uiState.comparisonWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bilgi Uyuşmazlığı") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearComparisonWarning()
                    startSide(mode)
                }) { Text("Tekrar Çek") }
            }
        )
    }

    if (uiState.nonIdWarning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Hata") },
            text = { Text("Kimlik kartı algılanamadı, lütfen tekrar deneyin.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearNonIdWarning()
                    startSide(mode)
                }) { Text("Tamam") }
            }
        )
    }

    uiState.comparisonErrors?.let { errors ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Doğrulama Hatası") },
            text = { Column { errors.forEach { Text("• $it") } } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearErrors()
                    startSide(mode)
                }) { Text("Tekrar Çek") }
            }
        )
    }
}

@Composable
private fun CapturedReview(bitmap: Bitmap, state: CaptureState, message: String) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.Center).width(maxWidth * SDK_FRAME_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(SDK_FRAME_RATIO)
                    .border(
                        2.5.dp,
                        if (state == CaptureState.SUCCESS) Success else Accent,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                when (state) {
                    CaptureState.UPLOADING -> CircularProgressIndicator(color = Color.White)
                    CaptureState.SUCCESS -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(56.dp)
                    )
                    else -> Unit
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                color = Color.White,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun SideThumbnail(
    label: String,
    bitmap: Bitmap?,
    state: CaptureState,
    active: Boolean,
    enabled: Boolean,
    onRetake: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        state == CaptureState.SUCCESS -> Success
        state == CaptureState.ERROR -> Danger
        state == CaptureState.COMPARISON_WARNING -> Color(0xFFF57C00)
        active -> Accent
        else -> Color(0xFF334155)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(SDK_FRAME_RATIO)
                .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                .border(2.dp, borderColor, RoundedCornerShape(10.dp))
                .clickable(enabled = enabled, onClick = onRetake),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (state == CaptureState.UPLOADING) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (enabled) "$label · Tekrar çek" else label,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
```

---

## Provider'a bağlama

```kotlin
class SampleUiProvider : SdkUiProvider {
    private val standard = StandardUiProvider()

    @Composable
    override fun DocumentCombinedScreen(onNext: () -> Unit, onBack: () -> Unit) {
        MySingleScreenIdScreen(onNext = onNext, onBack = onBack)
    }

    // … diğer ekranlar standard.xxx(...)'e delege
}
```

---

## Varyasyonlar

### A) Otomatik geçiş yerine kullanıcı onayı ("Arka yüze geç" butonu)

(2) numaralı `LaunchedEffect`'i kaldırın ve önizleme görünümündeki alt `Column`'a bir buton ekleyin:

```kotlin
if (mode == CaptureMode.FRONT && uiState.frontState == CaptureState.SUCCESS) {
    Button(onClick = { startSide(CaptureMode.BACK) }) { Text("Arka Yüze Geç") }
}
```

Bu durumda kullanıcı ön yüzü inceleyip küçük önizlemeye dokunarak tekrar çekebilir.

### B) Arka yüzü de otomatik bitir ("Devam Et" butonu olmadan)

```kotlin
LaunchedEffect(canFinish) {
    if (canFinish) {
        delay(1500)                 // arka yüzü de kısa süre göster
        viewModel.onNextClicked()
    }
}
```

### C) Çerçeveyi SDK değil siz çizmek isterseniz

Çerçevenin şeklini veya konumunu değiştirmeniz gerekiyorsa `DocumentScanScreen` yerine
`CameraPreview` + kendi `Canvas` çerçevenizi kullanın (bkz. `MyIdScreen.kt` → `IdCameraOverlay`).
Bu durumda iki ek sorumluluk size geçer:

- `viewModel.setViewport(w, h, RectF)` ile çizdiğiniz çerçeveyi analyzer'a bildirmek
- Kamerayı açık tutacaksanız, yakalamadan sonra analyzer'ı susturmak:

```kotlin
val analyzerEnabled = remember { AtomicBoolean(true) }
val gatedAnalyzer = remember(viewModel) {
    ImageAnalysis.Analyzer { image ->
        if (analyzerEnabled.get()) viewModel.cameraAnalyzer.analyze(image) else image.close()
    }
}
SideEffect { analyzerEnabled.set(currentImage == null) }
```

Yalnızca çerçeve **rengini** değiştirmek istiyorsanız buna gerek yok. `DocumentScanScreen`'in
`scanColor` / `successColor` / `errorColor` parametreleri yeterlidir.

---

## Hata ve uyarı durumları

| Durum | Nasıl anlarsınız | SDK ne yapmış olur | Sizin yapacağınız |
|---|---|---|---|
| Karede kimlik yok | `nonIdWarning == true` | `isScanning = false`, görsel kaydedilmez | `clearNonIdWarning()` → `startSide(mode)` |
| Ehliyet / pasaport tutuldu | `isScanning == false`, görsel `null`, dialog alanı yok | Tarama durdurulur, hata mesajı `DocumentScanScreen`'de gösterilir, kendiliğinden devam etmez | ~2 sn sonra `startSide(mode)` (örnekteki (3) `stalled` efekti) |
| OCR uyuşmazlığı | `comparisonWarning != null`, state `COMPARISON_WARNING` | 3. uyarıda otomatik `SUCCESS` sayar | `clearComparisonWarning()` → `startSide(mode)` |
| Upload / doğrulama hatası | `comparisonErrors != null` | Upload biter, yüzün state'i güncellenmeyebilir | `clearErrors()` → `startSide(mode)` (**`prepareForCapture` şart**, 3. kural) |
| Otomatik yakalama zorlanıyor | `manualFallbackEnabled == true` | `DocumentScanScreen` "Manuel Çek" butonunu kendisi gösterir | — |
| İçerik eksik (TCKN, MRZ vb.) | `scanErrorMessage` dolu, `isScanning == true` | `DocumentScanScreen` mesajı gösterir, taramaya devam eder | — |

---

## Sıkça sorulanlar

**Arka yüz taranırken ön yüzü tekrar çekmek istenirse?**
Köşedeki ön yüz önizlemesine dokunulunca `startSide(CaptureMode.FRONT)` çağrılır. Ön yüz yeniden
`SUCCESS` olunca (2) numaralı efekt yine arka yüze geçer. `startScanning()` BACK modunda
`backImage`'ı temizlediği için arka yüz de yeniden çekilir. Ön/arka çapraz doğrulamanın tutarlı
kalması için bu davranış doğrudur.

**Ekran döndürülünce akış baştan mı başlıyor?**
Hayır. `DocumentScanViewModel` navigation entry'ye bağlıdır, `started` bayrağı da
`rememberSaveable` ile saklanır. Bu yüzden başlangıç çağrıları tekrar yapılmaz, kaldığınız
yüzden devam edilir.

**`DocumentScanScreen` de `startScanning()` çağırıyor, `startSide` ile çift çağrı sorun olur mu?**
Hayır. `startScanning()` yalnızca tarama durumunu ve frame buffer'ı sıfırlar. Görsel zaten `null`
olduğu için tekrar çağrılması bir şey kaybettirmez.

**`onBack` basıldığında bir şey temizlemem gerekir mi?**
Hayır. `onBack` SDK navigator'ı ile önceki ekrana döner. Kamera `DocumentScanScreen` ile birlikte
composition'dan çıkarken bırakılır. Analyzer, ViewModel temizlendiğinde (`onCleared`) kapatılır.

**Yönlendirme metinlerini nasıl değiştiririm?**
`DocumentScanScreen`'deki metinler SDK'nın `provideGuidanceMessage` mekanizmasından gelir:
[Yönlendirme Mesajları Rehberi](guidance-messages.md). "Ön yüz alındı. Şimdi kartı çevirin."
gibi önizleme metinleri sizin UI'nızdadır, doğrudan değiştirebilirsiniz.
