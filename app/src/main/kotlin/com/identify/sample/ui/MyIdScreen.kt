package com.identify.sample.ui

import android.graphics.RectF
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.scan.DocumentScanViewModel
import com.identify.sdk.presentation.selfie.CameraPreview
import com.identify.sdk.ui.standard.scan.DocumentScanScreen

/**
 * `docs/ui-customization.md`'deki **Senaryo 2** (kısmi override) pattern'inin `DocumentCombinedScreen`
 * (kimlik kartı ön+arka yüz tarama) için referans implementasyonu — [MySelfieScreen] ve
 * [MyNfcScreen] ile birebir aynı yaklaşım: kamerayı ve OCR/upload mantığını SDK'nın kendi
 * `DocumentScanViewModel`'i yönetir, biz sadece görsel katmanı (kart slotları, tarama çerçevesi,
 * renkler, buton stilleri) yazıyoruz.
 *
 * ## İki alt-ekran, tek Composable
 *
 * Gerçek `DocumentCombinedScreen` + `DocumentScanScreen` ikilisinin yaptığı gibi burada da iki hal
 * var, `showCamera` ile geçiş yapılıyor:
 * - **Panel (showCamera=false):** Ön/arka yüz için iki kart — boşsa placeholder, çekilmişse
 *   thumbnail + durum rozeti. Arka yüz kartı, ön yüz `SUCCESS` olana kadar kilitli
 *   (`DocumentScanViewModel.setCaptureMode` zaten aynı kısıtı ViewModel tarafında da uyguluyor).
 * - **Kamera (showCamera=true):** `CameraPreview` (ARKA kamera) + `viewModel.cameraAnalyzer` —
 *   otomatik kart tespiti/OCR SDK'da çalışıyor, biz sadece rehber çerçeveyi çiziyoruz. Selfie'nin
 *   aksine burada manuel `ImageCapture.takePicture()` YOK: `DocumentAnalyzer` her frame'i kendi
 *   analiz edip eşik sağlandığında `frontImage`/`backImage`'ı otomatik dolduruyor: bkz.
 *   `DocumentScanViewModel.onScanEvent` / `executeCapture`.
 *
 * ## `setViewport` neden gerekli
 *
 * `DocumentAnalyzer`, kart konumunu ekrandaki rehber çerçeveyle karşılaştırarak "çok uzak/yakın/eğik"
 * gibi UX event'leri üretiyor — bu yüzden ekran boyutu ölçülür ölçülmez (`BoxWithConstraints`)
 * rehber çerçevenin gerçek oranını (`viewModel.setViewport`) analyzer'a bildirmemiz gerekiyor,
 * aksi halde analyzer hep varsayılan/merkezi bir ROI varsayar ve rehberle senkron kalmaz.
 *
 * ## `onNext` parametresi neden burada hiç çağrılmıyor
 *
 * `DocumentScanViewModel.onNextClicked()` — tıpkı `NfcViewModel.onNextClicked()` gibi — navigasyonu
 * SDK'nın kendi `SdkNavigator`'ı üzerinden DOĞRUDAN tetikliyor (`navigator.navigateNext()`), bu
 * composable'a verilen `onNext` lambdası'nı beklemiyor. `IdentifyNavGraph.kt`'de
 * `DocumentCombinedScreen(onNext = { navigator.navigateNext() }, ...)` şeklinde bağlanmış olsa da,
 * gerçek SDK ekranı bu lambdayı hiç çağırmıyor — imzada durması yalnızca `SdkUiProvider`
 * interface'inin sabit metod imzasını karşılamak için (bkz. [MyNfcScreen] için `SUCCESS` sonrası
 * `viewModel.onNextClicked()` çağrısı, orada da aynı şekilde).
 *
 * Bu ekranı denemek için [SampleUiProvider]'daki `DocumentCombinedScreen(...)` override'ını
 * `MyIdScreen(...)` çağırır hale getirmeniz yeterli.
 *
 * ## Rehber çerçeve SDK'nın oranına/konumuna sabit mi kalmak zorunda?
 *
 * Hayır. `DocumentScanViewModel.setViewport` → `DocumentAnalyzer.setViewport(width, height, guide:
 * RectF)` normalize edilmiş (0..1) **herhangi bir** `RectF`'i kabul ediyor — analyzer içinde 1.586
 * kart oranı ya da merkezi konum gibi sabit bir varsayım yok; o oran/konum yalnızca aşağıdaki
 * [IdCameraOverlay]'de BİZİM tercihimiz. Her müşteri kendi marka diline göre tamamen farklı bir
 * çerçeve (farklı boyut, konum, hatta tam kenarlık yerine köşe-parantez gibi başka bir görsel stil)
 * çizebilir.
 *
 * Tek kural — atlanırsa gerçekten kıran tek şey — şu: `setViewport`'a giden `RectF`, o an ekranda
 * GÖRÜNEN çerçeveyle birebir aynı normalize koordinatları temsil etmeli ve ekran boyutu/çerçeve
 * geometrisi her değiştiğinde yeniden gönderilmeli. Aksi halde "kartı çerçeveye hizala / çok uzak /
 * çok yakın" gibi UX mesajları, kullanıcının gerçekte gördüğü çerçeveyle uyuşmayan bir alana göre
 * üretilir — algılama de facto o görünmeyen alana göre çalışır.
 *
 * [IdCameraOverlayCustomGuide], [IdCameraOverlay]'den kasıtlı olarak FARKLI bir çerçeve çiziyor
 * (daha dar, ortalanmış yerine üste yaslı, tam kenarlık yerine köşe-parantez) — sadece bunun
 * mümkün olduğunu göstermek için; [MyIdScreenCustomGuide] bunu kullanan alternatif giriş noktası.
 * Denemek için [SampleUiProvider]'da `MyIdScreen(...)` yerine `MyIdScreenCustomGuide(...)` çağırın.
 *
 * ## Üçüncü (en az kod gerektiren) seçenek: çerçeveyi sıfırdan çizmek yerine SDK'nın kendi
 * kamera bileşenini ödünç almak
 *
 * Yukarıdaki ikisi çerçeveyi Canvas ile BİZ çiziyor, `setViewport`'u BİZ senkronda tutuyoruz — bu,
 * çerçevenin şeklini/konumunu tamamen değiştirmek isteyenler için gerekli. Ama yalnızca RENGİNİ
 * markanıza uydurmak yetiyorsa buna hiç gerek yok: `sdk-ui-default` içindeki
 * `com.identify.sdk.ui.standard.scan.DocumentScanScreen` composable'ı zaten **public** —
 * kamerayı, gerçek rehber çerçevesini, `ScanningEffect` shimmer'ını ve `setViewport` senkronunu
 * SDK kendi içinde doğru şekilde yönetiyor; dışarıya yalnızca `scanColor`/`successColor`/
 * `errorColor` parametreleri açık. [IdCameraOverlaySdkReused] bunu gösteriyor — kamera adımı için
 * tek satır, hiç `RectF`/`Canvas` hesabı yok; biz yalnızca dışarıdaki panel (ön/arka yüz kartları)
 * kendi UI'ımız. [MyIdScreenSdkCamera] bunu kullanan üçüncü giriş noktası.
 */
@Composable
fun MyIdScreen(onNext: () -> Unit, onBack: () -> Unit) =
    IdScreenBase(onNext = onNext, onBack = onBack, cameraOverlay = { vm, back -> IdCameraOverlay(vm, back) })

/** Aynı [MyIdScreen] akışı, yalnızca kamera rehber çerçevesi farklı — bkz. [IdCameraOverlayCustomGuide]. */
@Composable
fun MyIdScreenCustomGuide(onNext: () -> Unit, onBack: () -> Unit) =
    IdScreenBase(onNext = onNext, onBack = onBack, cameraOverlay = { vm, back -> IdCameraOverlayCustomGuide(vm, back) })

/**
 * Aynı [MyIdScreen] akışı (panel bizim, dashboard bizim) — yalnızca kamera adımında Canvas ile
 * çerçeve çizmek yerine SDK'nın kendi `DocumentScanScreen`'ini ödünç alıyor, bkz. [IdCameraOverlaySdkReused].
 */
@Composable
fun MyIdScreenSdkCamera(onNext: () -> Unit, onBack: () -> Unit) =
    IdScreenBase(onNext = onNext, onBack = onBack, cameraOverlay = { vm, back -> IdCameraOverlaySdkReused(vm, back) })

@Composable
private fun IdScreenBase(
    onNext: () -> Unit,
    onBack: () -> Unit,
    cameraOverlay: @Composable (DocumentScanViewModel, () -> Unit) -> Unit
) {
    val viewModel: DocumentScanViewModel = viewModel(factory = SdkViewModelFactory)
    val uiState by viewModel.uiState
    var showCamera by remember { mutableStateOf(false) }

    LaunchedEffect(showCamera) {
        if (showCamera) viewModel.startScanning()
    }

    // İlgili yüz (mevcut capture mode) çekildiğinde kamerayı kapat, panele dön.
    LaunchedEffect(uiState.frontImage, uiState.backImage, showCamera) {
        if (!showCamera) return@LaunchedEffect
        val captured = if (uiState.currentCaptureMode == DocumentScanViewModel.CaptureMode.FRONT) {
            uiState.frontImage != null
        } else {
            uiState.backImage != null
        }
        if (captured) showCamera = false
    }

    if (showCamera) {
        cameraOverlay(viewModel) { showCamera = false }
    } else {
        IdPanel(
            viewModel = viewModel,
            onBack = onBack,
            onOpenFront = {
                viewModel.prepareForCapture(DocumentScanViewModel.CaptureMode.FRONT)
                viewModel.setCaptureMode(DocumentScanViewModel.CaptureMode.FRONT)
                showCamera = true
            },
            onOpenBack = {
                viewModel.prepareForCapture(DocumentScanViewModel.CaptureMode.BACK)
                viewModel.setCaptureMode(DocumentScanViewModel.CaptureMode.BACK)
                showCamera = true
            }
        )
    }

    uiState.comparisonWarning?.let { warning ->
        AlertDialog(
            onDismissRequest = { /* zorla aksiyon — kullanıcı tekrar çekmeli */ },
            title = { Text("Bilgi Uyuşmazlığı") },
            text = { Text(warning) },
            confirmButton = {
                TextButton(onClick = {
                    val retakeMode = if (uiState.frontState == DocumentScanViewModel.CaptureState.COMPARISON_WARNING) {
                        DocumentScanViewModel.CaptureMode.FRONT
                    } else {
                        DocumentScanViewModel.CaptureMode.BACK
                    }
                    viewModel.clearComparisonWarning()
                    viewModel.prepareForCapture(retakeMode)
                    viewModel.setCaptureMode(retakeMode)
                    showCamera = true
                }) { Text("Tekrar Çek") }
            }
        )
    }

    if (uiState.nonIdWarning) {
        AlertDialog(
            onDismissRequest = { viewModel.clearNonIdWarning(); showCamera = false },
            title = { Text("Hata") },
            text = { Text("Kimlik kartı algılanamadı, lütfen tekrar deneyin.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearNonIdWarning(); showCamera = false }) { Text("Tamam") }
            }
        )
    }

    uiState.comparisonErrors?.let { errors ->
        AlertDialog(
            onDismissRequest = { viewModel.clearErrors() },
            title = { Text("Doğrulama Hatası") },
            text = {
                Column {
                    errors.forEach { error -> Text("• $error") }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearErrors() }) { Text("Tamam") }
            }
        )
    }
}

@Composable
private fun IdPanel(
    viewModel: DocumentScanViewModel,
    onBack: () -> Unit,
    onOpenFront: () -> Unit,
    onOpenBack: () -> Unit
) {
    val uiState by viewModel.uiState
    val isBackLocked = uiState.frontState != DocumentScanViewModel.CaptureState.SUCCESS

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1120)).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            CustomScreenBadge("PARTIAL OVERRIDE — DocumentCombinedScreen (Kimlik Kartı)")
        }
        Spacer(Modifier.height(24.dp))

        IdSlot(
            title = "Ön Yüz",
            state = uiState.frontState,
            bitmap = uiState.frontImage,
            placeholder = com.identify.sdk.R.drawable.kimlik_on_yuz,
            locked = false,
            onClick = onOpenFront
        )
        Spacer(Modifier.height(16.dp))
        IdSlot(
            title = "Arka Yüz",
            state = uiState.backState,
            bitmap = uiState.backImage,
            placeholder = com.identify.sdk.R.drawable.kimlik_arka_plan,
            locked = isBackLocked,
            onClick = onOpenBack
        )
        if (isBackLocked) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Arka yüzü taramadan önce ön yüzü tamamlayın.",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = { viewModel.onNextClicked() },
            enabled = uiState.canProceed && !uiState.isUploading,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
        ) {
            Text(
                if (uiState.isUploading) "Yükleniyor…" else "Devam Et",
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun IdSlot(
    title: String,
    state: DocumentScanViewModel.CaptureState,
    bitmap: android.graphics.Bitmap?,
    placeholder: Int,
    locked: Boolean,
    onClick: () -> Unit
) {
    val borderColor = when (state) {
        DocumentScanViewModel.CaptureState.SUCCESS -> Color(0xFF41D97F)
        DocumentScanViewModel.CaptureState.ERROR -> Color(0xFFFF453A)
        DocumentScanViewModel.CaptureState.COMPARISON_WARNING -> Color(0xFFF57C00)
        else -> Color(0xFF334155)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clickable(enabled = !locked && state != DocumentScanViewModel.CaptureState.UPLOADING) { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, if (locked) Color(0xFF334155) else borderColor, RoundedCornerShape(16.dp))
        ) {
            when {
                state == DocumentScanViewModel.CaptureState.UPLOADING -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF60A5FA))
                    }
                }
                bitmap != null -> {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                            .background(
                                if (state == DocumentScanViewModel.CaptureState.ERROR) Color(0xFFFF453A) else Color(0xFF60A5FA),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = if (state == DocumentScanViewModel.CaptureState.ERROR) Icons.Default.PriorityHigh else Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(id = placeholder),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().graphicsLayer(alpha = if (locked) 0.35f else 0.9f),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(title, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdCameraOverlay(viewModel: DocumentScanViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState
    val density = LocalDensity.current

    val frameColor = when {
        uiState.scanErrorMessage != null -> Color(0xFFFF453A)
        else -> Color(0xFF60A5FA)
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            analyzer = viewModel.cameraAnalyzer
        )

        // Rehber çerçeveyi (görsel) analyzer'ın ROI'siyle (mantıksal) senkron tut — aksi halde
        // "kartı çerçeveye hizala" mesajları kullanıcının gördüğü çerçeveyle uyuşmaz.
        LaunchedEffect(screenWidth, screenHeight) {
            val sw = with(density) { screenWidth.toPx() }
            val sh = with(density) { screenHeight.toPx() }
            val rw = 0.9f
            val rh = (sw * rw / 1.58f) / sh
            val rx = (1f - rw) / 2f
            val ry = (1f - rh) / 2f
            viewModel.setViewport(sw, sh, RectF(rx, ry, rx + rw, ry + rh))
        }

        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = 0.99f)) {
            drawRect(Color.Black.copy(alpha = 0.65f))
            val frameWidthPx = size.width * 0.9f
            val frameHeightPx = frameWidthPx / 1.58f
            val left = (size.width - frameWidthPx) / 2
            val top = (size.height - frameHeightPx) / 2
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(left, top),
                size = Size(frameWidthPx, frameHeightPx),
                cornerRadius = CornerRadius(16.dp.toPx()),
                blendMode = BlendMode.Clear
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(screenWidth * 0.9f)
                .aspectRatio(1.58f)
                .border(width = 2.5.dp, color = frameColor, shape = RoundedCornerShape(16.dp))
        )

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                CustomScreenBadge(
                    if (uiState.currentCaptureMode == DocumentScanViewModel.CaptureMode.FRONT) {
                        "TARAMA — Ön Yüz"
                    } else {
                        "TARAMA — Arka Yüz"
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF0B1120).copy(alpha = 0.92f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                uiState.scanErrorMessage ?: uiState.guidanceMessage,
                color = if (uiState.scanErrorMessage != null) Color(0xFFFF453A) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (uiState.manualFallbackEnabled) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.triggerManualCapture() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
                ) { Text("Manuel Çek", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * [IdCameraOverlay]'in "farklı bir müşteri farklı bir çerçeve isterse" örneği — kasıtlı olarak
 * FARKLI: daha dar (%72 vs %90), ortalanmış yerine sabit bir üst boşlukla yaslı, karartma maskesi
 * yok, tam kenarlık yerine köşe-parantez (L-şekilli) tarayıcı stili ve farklı bir vurgu rengi.
 *
 * Buradaki tek zorunlu bağ [IdCameraOverlay] ile birebir aynı: `rw`/`rh`/`rx`/`ry` — hem Canvas'ta
 * çizilen köşe işaretleri hem de `viewModel.setViewport(...)`'a giden `RectF` — AYNI dört sayıdan
 * türetiliyor. Çerçevenin görünümünü (renk, stil, konum, boyut) istediğiniz gibi değiştirebilirsiniz;
 * değiştiremeyeceğiniz tek şey, çizdiğiniz alanla analyzer'a bildirdiğiniz alanın birbirinden
 * kopması.
 */
@Composable
private fun IdCameraOverlayCustomGuide(viewModel: DocumentScanViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState
    val density = LocalDensity.current
    // SDK'nın varsayılan mavisinden bilinçli olarak farklı — bu markanın kendi vurgu rengi olduğunu
    // göstermek için.
    val accent = if (uiState.scanErrorMessage != null) Color(0xFFFF453A) else Color(0xFFF5B942)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
            analyzer = viewModel.cameraAnalyzer
        )

        // [IdCameraOverlay]'deki 0.9 / merkezi konum yerine: 0.72 genişlik, sabit üst boşluk (0.16).
        // Kart fiziksel oranı (1.586) korunuyor ama bu da zorunlu değil — analyzer herhangi bir
        // RectF'i kabul eder, yalnızca ROI kartın tamamını makul biçimde kapsamalı.
        val rw = 0.72f
        val ry = 0.16f
        val rh = (with(density) { screenWidth.toPx() } * rw / 1.586f) / with(density) { screenHeight.toPx() }
        val rx = (1f - rw) / 2f

        LaunchedEffect(screenWidth, screenHeight) {
            val sw = with(density) { screenWidth.toPx() }
            val sh = with(density) { screenHeight.toPx() }
            viewModel.setViewport(sw, sh, RectF(rx, ry, rx + rw, ry + rh))
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val frameWidthPx = size.width * rw
            val frameHeightPx = size.height * rh
            val left = size.width * rx
            val top = size.height * ry
            val bracket = 26.dp.toPx()
            val stroke = 4.dp.toPx()

            fun drawCornerBracket(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(accent, Offset(x, y), Offset(x + bracket * dx, y), strokeWidth = stroke)
                drawLine(accent, Offset(x, y), Offset(x, y + bracket * dy), strokeWidth = stroke)
            }
            drawCornerBracket(left, top, 1f, 1f)
            drawCornerBracket(left + frameWidthPx, top, -1f, 1f)
            drawCornerBracket(left, top + frameHeightPx, 1f, -1f)
            drawCornerBracket(left + frameWidthPx, top + frameHeightPx, -1f, -1f)
        }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                CustomScreenBadge(
                    if (uiState.currentCaptureMode == DocumentScanViewModel.CaptureMode.FRONT) {
                        "ÖZEL ÇERÇEVE — Ön Yüz"
                    } else {
                        "ÖZEL ÇERÇEVE — Arka Yüz"
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF0B1120).copy(alpha = 0.92f))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                uiState.scanErrorMessage ?: uiState.guidanceMessage,
                color = accent,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (uiState.manualFallbackEnabled) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.triggerManualCapture() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) { Text("Manuel Çek", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/**
 * [IdCameraOverlay] / [IdCameraOverlayCustomGuide]'nin aksine burada hiçbir Canvas çizimi, hiçbir
 * `RectF`/`setViewport` hesabı YOK — kamera adımının tamamı SDK'nın kendi public composable'ı olan
 * `DocumentScanScreen`'e devrediliyor. Rehber çerçeve, `ScanningEffect` shimmer'ı, viewport senkronu,
 * TTS rehberlik konuşması (dahili `SpeechGuidanceManager`) hepsi SDK içinde zaten doğru — biz sadece
 * üç renk parametresiyle kendi marka vurgu rengimize uyarlıyoruz.
 *
 * `onNext = onBack` şaşırtıcı görünebilir ama doğru: `DocumentScanScreen` bir yüz (ön ya da arka)
 * başarıyla tarandığında kendi `onNext`'ini çağırır — gerçek `DocumentCombinedScreen.kt`'nin de
 * yaptığı tam olarak bu (`DocumentScanScreen(onNext = { showCamera = false }, ...)`): "bu taramayı
 * bitirdim" sinyali, bizim akışımızda "kamerayı kapat, panele dön" (yani [IdScreenBase]'e verdiğimiz
 * `back` callback'i) ile birebir aynı anlama geliyor.
 *
 * `DocumentScanScreen`'in kendi bir `onBack`'i YOK (gerçek `DocumentCombinedScreen.kt`'de de kamera
 * moduna geçince geri butonu kaybolur, kullanıcı yalnızca tarama bitince ya da manuel çekimle
 * çıkabilir) — burada kendi geri butonumuzu SDK'nın çizdiği ekranın üstüne bindiriyoruz.
 */
@Composable
private fun IdCameraOverlaySdkReused(viewModel: DocumentScanViewModel, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        DocumentScanScreen(
            viewModel = viewModel,
            onNext = onBack,
            scanColor = Color(0xFFF5B942), // kendi marka vurgu rengimiz — SDK varsayılanı Color.Cyan
            successColor = Color(0xFF41D97F),
            errorColor = Color(0xFFFF453A)
        )
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
        }
    }
}
