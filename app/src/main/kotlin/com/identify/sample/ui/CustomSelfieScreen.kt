package com.identify.sample.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.selfie.SelfieState
import com.identify.sdk.presentation.selfie.SelfieViewModel

/**
 * [SelfieScreen][com.identify.sdk.presentation.SdkUiProvider.SelfieScreen] için tamamen özel bir
 * override'ın referans implementasyonu, [SampleUiProvider] üzerinden bağlanıyor.
 *
 * `SelfieScreen(onNext, onBack)` host app'e tam kontrol verir — SDK burada kendi başına hiçbir
 * şey render etmez. Bu ekran, host app'in **kendi** ViewModel'ini (host'a özel UI durumu için,
 * [CustomSelfieViewModel]) SDK'nın **kendi** `SelfieViewModel`'i (gerçek yüz karşılaştırma/yükleme
 * mantığı için) ile YAN YANA nasıl kullanacağının gerçek bir örneğidir:
 *
 * - `hostViewModel: CustomSelfieViewModel = viewModel()` — normal Compose factory, SDK'nın hiç
 *   haberi olmadığı, tamamen bize ait state (rıza onayı, deneme sayacı).
 * - `sdkViewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)` — SDK'nın
 *   `StandardUiProvider`'ın da içeride kullandığı GERÇEK ViewModel'i; fotoğraf yükleme, backend
 *   karşılaştırması, retry/onay state machine'i burada yaşıyor.
 *
 * Kamera önizlemesi ve gerçek yüz tespiti burada **bilinçli olarak basitleştirildi**: gerçek bir
 * kamera pipeline'ı (CameraX + `sdkViewModel.cameraAnalyzer`) kurmak yerine, "Fotoğraf Çek" butonu
 * sabit renkli bir placeholder bitmap üretip doğrudan `sdkViewModel.onPhotoCaptured(bitmap)`'e
 * veriyor. Bundan sonraki her adım (crop, önizleme, onayla/tekrar dene, backend'e yükleme,
 * karşılaştırma uyarıları, `onNext()`'e geçiş) **gerçek** SDK mantığıyla çalışıyor — mock olan
 * tek şey kameranın kendisi.
 */
@Composable
fun CustomSelfieScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val hostViewModel: CustomSelfieViewModel = viewModel()
    val sdkViewModel: SelfieViewModel = viewModel(factory = SdkViewModelFactory)

    val consentGiven by hostViewModel.consentGiven.collectAsState()
    val captureAttempts by hostViewModel.captureAttempts.collectAsState()
    val state by sdkViewModel.uiState

    // sdkViewModel COMPLETED durumuna geçtiğinde (backend yüklemesi başarılı), akışı ilerlet.
    LaunchedEffect(state.selfieState) {
        if (state.selfieState == SelfieState.COMPLETED) {
            onNext()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0B1120)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CustomScreenBadge("CUSTOM UI PROVIDER — SelfieScreen override")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Host ViewModel deneme sayacı: $captureAttempts",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF64748B)
                )
                Spacer(modifier = Modifier.height(24.dp))

                if (!consentGiven) {
                    ConsentStep()
                } else {
                    when (state.selfieState) {
                        SelfieState.SCANNING -> CaptureStep()
                        SelfieState.PREVIEW -> PreviewStep(bitmap = state.croppedBitmap ?: state.capturedBitmap)
                        SelfieState.UPLOADING -> UploadingStep()
                        SelfieState.COMPLETED -> {} // LaunchedEffect zaten onNext() çağırıyor
                    }

                    state.comparisonWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(warning, color = Color(0xFFFACC15), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                    state.errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(error, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                    }
                }
            }

            Column {
                if (!consentGiven) {
                    Button(
                        onClick = { hostViewModel.giveConsent() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
                    ) {
                        Text("Kabul Ediyorum, Devam Et", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    when (state.selfieState) {
                        SelfieState.SCANNING -> Button(
                            onClick = {
                                hostViewModel.recordCaptureAttempt()
                                sdkViewModel.onPhotoCaptured(createPlaceholderSelfieBitmap())
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
                        ) {
                            Text("Fotoğraf Çek (Simüle)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        SelfieState.PREVIEW -> {
                            Button(
                                onClick = { sdkViewModel.onConfirm() },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
                            ) {
                                Text("Onayla, Yükle", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { sdkViewModel.onRetry() },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Tekrar Çek", color = Color.White)
                            }
                        }

                        SelfieState.UPLOADING, SelfieState.COMPLETED -> {
                            // Yükleme sırasında buton yok — kullanıcı bekliyor.
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Geri", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ConsentStep() {
    Icon(
        Icons.Default.Face,
        contentDescription = null,
        tint = Color(0xFF60A5FA),
        modifier = Modifier.size(96.dp)
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        "Bu, SDK'nın kendi kamera/yüz-tespiti tabanlı Selfie ekranı DEĞİL.",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "Host app'in kendi SdkUiProvider.SelfieScreen(...) override'ı — SDK burada hiçbir şey " +
            "render etmiyor. Devam ederek selfie fotoğrafınızın kimlik doğrulama amacıyla " +
            "işleneceğini kabul etmiş olursunuz.",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF94A3B8),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun CaptureStep() {
    Box(
        modifier = Modifier
            .size(220.dp)
            .background(Color(0xFF1E293B), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Face,
            contentDescription = null,
            tint = Color(0xFF60A5FA),
            modifier = Modifier.size(96.dp)
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Gerçek kamera/yüz-tespiti burada yeniden yazılmadı — bu buton sabit renkli bir " +
            "placeholder bitmap üretip doğrudan SelfieViewModel.onPhotoCaptured(...)'e veriyor. " +
            "Bundan sonrası (crop, önizleme, backend'e yükleme) gerçek SDK mantığıdır.",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun PreviewStep(bitmap: Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(220.dp)
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Çekilen fotoğraf — bu SelfieViewModel.uiState.croppedBitmap, gerçek SDK crop mantığından geçti.",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFF64748B),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun UploadingStep() {
    CircularProgressIndicator(color = Color(0xFF60A5FA))
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "Backend'e yükleniyor (gerçek API çağrısı)…",
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFF94A3B8)
    )
}

/** Gerçek kamera olmadan `SelfieViewModel.onPhotoCaptured(...)`'i tetiklemek için sabit renkli placeholder bitmap. */
private fun createPlaceholderSelfieBitmap(): Bitmap =
    Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.rgb(180, 190, 200))
    }

@Composable
internal fun CustomScreenBadge(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFF60A5FA), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
