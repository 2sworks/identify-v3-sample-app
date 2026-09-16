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
            //Bu componentler zorunlu değildir.
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
                //bu previewlar tamamen yapılabilitesini göstermek amaçlıdır.
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
