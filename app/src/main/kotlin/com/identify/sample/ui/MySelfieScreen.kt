package com.identify.sample.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.selfie.CameraPreview
import com.identify.sdk.presentation.selfie.SelfieState
import com.identify.sdk.presentation.selfie.SelfieViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * `docs/custom-selfie-screen-example.md`'deki **Seçenek A**'nın (SDK'nın hazır `CameraPreview`'i
 * ile "kısmi özelleştirme") çalışan, gerçek referans implementasyonu.

 * ## [CustomSelfieScreen] ile farkı
 *
 * [CustomSelfieScreen] "tam özel UI" (Senaryo 3) örneğidir: kamerayı **siz** yazarsınız (ya da bu
 * örnekte olduğu gibi hiç yazmaz, "Fotoğraf Çek" butonuna sabit renkli bir placeholder bitmap
 * bağlarsınız). `MySelfieScreen` ise "kısmi özelleştirme" (Senaryo 2) örneğidir — kamerayı SDK'nın
 * kendisi yönetir, siz sadece görsel katmanı (guide çerçevesi, renkler, buton stilleri) yazarsınız.
 *

 * Ayrıca "Tekrar Çek" butonunda, gerçek `SelfieScreen.kt`'da olduğu gibi `viewModel.onRetry()`'dan
 * ÖNCE `imageCaptureRef.value = null` set ediliyor: `CameraPreview` PREVIEW state'inde composition'dan
 * tamamen kalkıp `onDispose`'da kamerayı unbind ediyor; SCANNING'e dönüldüğünde yeniden mount olup
 * taze bir `ImageCapture` verene kadar eski referansı elde tutmamak, SDK'nın kendi ekranıyla birebir
 * aynı defansif davranışı sağlıyor.Bunuda kendi projenizde bu şekilde kullanabilirsiniz.
 *
 * Bu ekranı gerçekten denemek isterseniz [SampleUiProvider]'daki
 * `SelfieScreen(...) { CustomSelfieScreen(...) }` çağrısını `MySelfieScreen(...)` ile değiştirmeniz
 * yeterli — geri kalan her şey (ViewModel factory, navigasyon) aynı şekilde çalışır.
 *
 * **Bağımlılık notu:** Bu dosya `androidx.camera.*` sınıflarına (`CameraPreview`, `ImageCapture`,
 * `CameraSelector`...) doğrudan referans verdiği için `app/build.gradle.kts`'e `camera-core` ve
 * `camera-view`'ı elle eklemek gerekti — sadece bu dosya gibi kamerayı doğrudan kullanan kod için
 * gerekli, `CustomSelfieScreen` bu ek bağımlılığa ihtiyaç duymuyor (bkz. `docs/dependencies.md`).
 */

@Composable
fun MySelfieScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val viewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)
    val uiState by viewModel.uiState
    val context = LocalContext.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    val imageCaptureRef = remember { mutableStateOf<ImageCapture?>(null) }
    val isTakingPicture = remember { mutableStateOf(false) }

    // COMPLETED olduğunda bir sonraki modüle geç — gerçek SelfieViewModel state machine'i sürüyor.
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.uiState.value.selfieState }
            .filter { it == SelfieState.COMPLETED }
            .first()
        onNext()
    }

    // Yüz stabil hale gelir gelmez (readyToCapture=true) gerçek fotoğrafı otomatik çek — manuel
    // buton yok, tıpkı SDK'nın kendi SelfieScreen'inde olduğu gibi.
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
                        // Gerçek çekilen fotoğraf — placeholder değil.
                        if (bitmap != null) viewModel.onPhotoCaptured(bitmap) else viewModel.onPhotoCaptureFailed()
                        isTakingPicture.value = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        viewModel.onPhotoCaptureFailed()
                        isTakingPicture.value = false
                    }
                })
            }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (uiState.selfieState) {
            SelfieState.SCANNING, SelfieState.UPLOADING -> {
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    scaleType = PreviewView.ScaleType.FILL_CENTER,
                    cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
                    analyzer = viewModel.cameraAnalyzer, // SDK'nın gerçek yüz tespiti
                    onImageCaptureReady = { imageCaptureRef.value = it },
                    outputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
                )
                // SDK'nın kendi FaceOvalGuide/FaceMeshOverlay'i `ui.standard.components` içinde,
                // host app'e açık değil — bu yüzden kendi basit guide çerçevemizi çiziyoruz.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.72f)
                        .aspectRatio(0.75f)
                        .border(
                            width = 3.dp,
                            color = if (uiState.isWellPositioned) Color(0xFF4ADE80) else Color(0xFF60A5FA),
                            shape = RoundedCornerShape(percent = 50)
                        )
                )
            }

            SelfieState.PREVIEW -> {
                uiState.capturedBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f)
                    )
                }
            }

            SelfieState.COMPLETED -> {} // LaunchedEffect zaten onNext() çağırıyor
        }

        // Üst bar — badge + geri butonu
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                CustomScreenBadge("PARTIAL OVERRIDE — SelfieScreen (Seçenek A: SDK CameraPreview)")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "CustomSelfieScreen'den farkı: burada kamera GERÇEK (CameraX + SDK'nın " +
                    "cameraAnalyzer'ı), fotoğraf da yüz stabil olur olmaz OTOMATİK çekiliyor — " +
                    "\"Fotoğraf Çek\" butonu ve placeholder bitmap yok.",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFCBD5E1)
            )
        }

        // Alt panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xFF0B1120).copy(alpha = 0.92f))
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState.selfieState) {
                SelfieState.SCANNING -> {
                    Text(
                        uiState.guidanceMessage,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }

                SelfieState.UPLOADING -> {
                    CircularProgressIndicator(color = Color(0xFF60A5FA))
                    Spacer(Modifier.height(12.dp))
                    Text("Backend'e yükleniyor (gerçek API çağrısı)…", color = Color(0xFF94A3B8))
                }

                SelfieState.PREVIEW -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f).height(48.dp),
                            onClick = {
                                // CameraPreview PREVIEW state'inde composition'dan kalkıp kamerayı
                                // unbind ediyor; SCANNING'e dönünce yeniden mount olup taze bir
                                // ImageCapture verene kadar eski referansı elde tutmamak için temizle
                                // — SDK'nın kendi SelfieScreen'i de retry'da aynısını yapıyor.
                                imageCaptureRef.value = null
                                isTakingPicture.value = false
                                viewModel.onRetry()
                            }
                        ) { Text("Tekrar Çek", color = Color.White) }

                        Button(
                            modifier = Modifier.weight(1f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA)),
                            onClick = { viewModel.onConfirm() }
                        ) { Text("Onayla", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }
                }

                SelfieState.COMPLETED -> {}
            }
        }

        uiState.errorMessage?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.clearError() },
                title = { Text("Hata") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearError() }) { Text("Tekrar Dene") }
                }
            )
        }

        // Doküman örneğinde eksikti: comparisonWarning hiç gösterilmiyordu.
        uiState.comparisonWarning?.let { warning ->
            AlertDialog(
                onDismissRequest = { viewModel.clearComparisonWarning() },
                title = { Text("Yüz Eşleşme Uyarısı") },
                text = { Text(warning) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearComparisonWarning() }) { Text("Tekrar Dene") }
                }
            )
        }
    }
}

/** Gerçek `ImageCapture.OnImageCapturedCallback`'ten gelen JPEG'i Bitmap'e çevirir, rotasyonu düzeltir. */
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
