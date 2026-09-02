# Custom Selfie Screen — Uçtan Uca Örnek

Bu döküman, `docs/ui-customization.md` içindeki **Senaryo 2 (kısmi özelleştirme)** veya
**Senaryo 3 (tam özel UI)** ile kendi `SelfieScreen`'inizi yazarken kamerayı **gerçekten**
nasıl bağlayacağınızı, baştan sona çalışan bir kod örneğiyle gösterir.

> `StandardUiProvider` (SDK'nın hazır ekranı) kullanıyorsanız bu dökümana ihtiyacınız yok —
> kamera pipeline'ı zaten devrede. Bu döküman yalnızca kendi `SelfieScreen` override'ınızı
> yazanlar içindir.

Önemli olan tek şey şu: **kamerayı ve yüz tespitini siz yazmıyorsunuz**, SDK'nın size verdiği
`SelfieViewModel`'i kullanıyorsunuz. Sizin yazdığınız kısım sadece görsel katman (renkler,
guide çerçevesi, buton stilleri). Aşağıdaki örnekte placeholder/sabit bitmap yok — gerçek
`ImageCapture.takePicture()` sonucu `viewModel.onPhotoCaptured(bitmap)`'e veriliyor.

---

## Kullanacağınız API yüzeyi

| Ne | Nereden geliyor |
|---|---|
| `SelfieViewModel` | `viewModel(factory = SdkViewModelFactory)` — constructor `internal`, başka türlü alınamaz |
| `viewModel.cameraAnalyzer` | Gerçek MediaPipe `FaceLandmarker` tabanlı `ImageAnalysis.Analyzer` — kendi `ImageAnalysis`'inize bağlarsınız |
| `viewModel.uiState` | `SelfieUiState` — `selfieState`, `readyToCapture`, `capturedBitmap`, `guidanceMessage`, `meshPoints`, `errorMessage`, `comparisonWarning` vb. |
| `viewModel.onPhotoCaptured(bitmap)` | `ImageCapture.takePicture()` başarılı olduğunda çağırın |
| `viewModel.onPhotoCaptureFailed()` | Kamera hatasında çağırın |
| `viewModel.onConfirm()` / `onRetry()` | Preview ekranındaki onay/tekrar çek butonları |
| `viewModel.clearError()` / `clearComparisonWarning()` | Hata/uyarı dialoglarını kapatırken |

`SelfieUiState.selfieState` dört değer alır: `SCANNING` → `PREVIEW` → `UPLOADING` → `COMPLETED`.
`COMPLETED` olduğunda SDK akışı otomatik ilerletir; siz `onNext()`'i bu geçişte çağırırsınız.

---

## Seçenek A (önerilen) — SDK'nın hazır `CameraPreview`'ini kullanın

`sdk-ui-default` modülüne bağımlıysanız, CameraX bind kodunu hiç yazmanıza gerek yok. `CameraPreview` composable'ı izin
isteme, tek seferlik bind, auto-focus ve torch kontrolünü sizin yerinize yapar.

```kotlin
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.selfie.CameraPreview   // sdk-ui-default
import com.identify.sdk.presentation.selfie.SelfieState
import com.identify.sdk.presentation.selfie.SelfieViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix

@Composable
fun MySelfieScreen(
    onNext: () -> Unit,
    onBack: () -> Unit,
    viewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
) {
    val uiState by viewModel.uiState
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val isTakingPicture = remember { mutableStateOf(false) }

    // COMPLETED olduğunda bir sonraki ekrana geç
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.uiState.value.selfieState }
            .filter { it == SelfieState.COMPLETED }
            .first()
        onNext()
    }

    // Yüz stabil olur olmaz gerçek fotoğrafı çek
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.uiState.value.readyToCapture to imageCaptureRef.value }
            .filter { (ready, capture) -> ready && capture != null && !isTakingPicture.value }
            .distinctUntilChanged()
            .collect { (_, capture) ->
                val imageCapture = capture ?: return@collect
                isTakingPicture.value = true
                imageCapture.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(proxy: ImageProxy) {
                        val bitmap = decodeImageProxy(proxy)
                        proxy.close()
                        // Gerçek çekilen fotoğraf — placeholder değil
                        if (bitmap != null) viewModel.onPhotoCaptured(bitmap)
                        else viewModel.onPhotoCaptureFailed()
                        isTakingPicture.value = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        viewModel.onPhotoCaptureFailed()
                        isTakingPicture.value = false
                    }
                })
            }
    }

    Box(Modifier.fillMaxSize()) {
        when (uiState.selfieState) {
            SelfieState.SCANNING, SelfieState.UPLOADING -> {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    scaleType = PreviewView.ScaleType.FILL_CENTER,
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
                    analyzer = viewModel.cameraAnalyzer,           // SDK'nın gerçek yüz tespiti
                    onImageCaptureReady = { imageCaptureRef.value = it },
                    outputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                )
                // Kendi guide çerçevenizi / rehber metninizi burada çizin, örn:
                Text(
                    text = uiState.guidanceMessage,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(32.dp)
                )
            }
            SelfieState.PREVIEW -> {
                uiState.capturedBitmap?.let { bmp ->
                    // kendi önizleme UI'ınız
                    Row {
                        Button(onClick = { viewModel.onRetry() }) { Text("Tekrar Çek") }
                        Button(onClick = { viewModel.onConfirm() }) { Text("Onayla") }
                    }
                }
            }
            SelfieState.COMPLETED -> { /* geçiş anı, onNext() zaten tetiklendi */ }
        }

        if (uiState.errorMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("Tekrar Dene") } },
                title = { Text("Hata") },
                text = { Text(uiState.errorMessage!!) }
            )
        }
    }
}

/**
 * ImageCapture'dan gelen JPEG'i Bitmap'e çevirip rotasyonu düzeltir.
 *
 */
private fun decodeImageProxy(proxy: ImageProxy): Bitmap? {
    return try {
        val buffer = proxy.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val degrees = proxy.imageInfo.rotationDegrees
        if (degrees == 0) bitmap
        else {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (e: Exception) {
        null
    }
}
```

Bu, SDK'nın kendi `sdk-ui-default/.../selfie/SelfieScreen.kt` dosyasındaki gerçek
implementasyonun sadeleştirilmiş halidir — stiller çıkarılmış, akış ve kamera bağlama mantığı
aynen korunmuştur.

---

## Seçenek B — `sdk-ui-default`'a bağımlı olmadan, kendi CameraX bind'inizi yazın

Tam özel UI (Senaryo 3) yapıyorsanız ve `sdk-ui-default` modülüne hiç bağımlı olmak
istemiyorsanız, tek fark yukarıdaki `CameraPreview(...)` çağrısının yerine kendi CameraX
bind'inizi yazmanız — geri kalan her şey (viewModel, `cameraAnalyzer`, `onPhotoCaptured`) aynı:

```kotlin
@Composable
fun MyRawCameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { androidx.camera.lifecycle.ProcessCameraProvider.getInstance(context) }
    val executor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier,
        factory = { ctx -> PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } },
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }   // <-- viewModel.cameraAnalyzer buraya

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview, imageAnalysis, imageCapture
                )
                onImageCaptureReady(imageCapture)
            }, ContextCompat.getMainExecutor(context))
        }
    )
}
```

> Not: Bu, `CameraPreview.kt`'nin basitleştirilmiş bir özeti — kamera izni isteme, tek seferlik
> bind koruması, `onDispose`'da `unbind`, auto-focus gibi production detaylarını içermiyor.
> Mümkünse Seçenek A'yı tercih edin; sadece `sdk-ui-default`'a bağımlı olamıyorsanız Seçenek
> B'yi referans alıp kendi production-ready hale getirin.

---

## Özet

- Gerçek kamera görüntüsünü **siz** üretmiyorsunuz — `viewModel.cameraAnalyzer`'ı CameraX
  `ImageAnalysis`'e bağlayınca SDK'nın MediaPipe tabanlı yüz tespiti otomatik çalışıyor.
- `ImageCapture.takePicture()`'dan gelen **gerçek** bitmap'i `viewModel.onPhotoCaptured(bitmap)`'e
  verin — sabit renkli/placeholder bitmap üretmenize gerek yok, üretilirse SDK tarafı da
  gerçek bir yüz göremeyeceği için doğrulama/karşılaştırma adımlarında hata alırsınız.
- Sizin yazacağınız kısım yalnızca: guide çerçevesi, buton/renk stilleri, preview/onay ekranının
  görünümü — akış state'i (`SelfieUiState`) ve kamera/ML tarafı SDK'da hazır.
