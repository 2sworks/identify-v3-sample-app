package com.identify.sample.ui

import androidx.activity.compose.BackHandler
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.analyzer.LivenessStep
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.liveness.LivenessViewModel
import com.identify.sdk.presentation.selfie.CameraPreview
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/**
 * `LivenessScreen`'in "kısmi özelleştirme" referans implementasyonu — kamerayı ve canlılık
 * mantığını SDK yönetir, host app yalnızca görsel katmanı yazar. [MySelfieScreen] ile aynı kalıp:
 * SDK'nın hazır [CameraPreview]'i + SDK'nın gerçek [LivenessViewModel]'i.
 *
 * [SampleUiProvider]'daki `LivenessScreen(...)` override'ı bu ekranı kullanıyor.
 */
@Composable
fun MyLivenessScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val viewModel: LivenessViewModel = viewModel(factory = SdkViewModelFactory)
    val uiState by viewModel.uiState

    // Modül tamamlandığında ilerlemeyi ekran tetikler.
    LaunchedEffect(Unit) {
        snapshotFlow { viewModel.uiState.value.currentStep }
            .filter { it == LivenessStep.COMPLETED }
            .first()
        onNext()
    }

    // Önce liveness adımları arasında geriye git; adım kalmadıysa ekrandan çık.
    BackHandler { if (!viewModel.onBackPress()) onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Ön kamera + SDK'nın kendi yüz analizi.
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            scaleType = PreviewView.ScaleType.FILL_CENTER,
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
            analyzer = viewModel.cameraAnalyzer
        )

        // Liveness adımları yüzün çerçeve içinde olmasını şart koşmaz (kural yaw/gülümseme/göz
        // kırpma eşiğidir), bu yüzden çerçeve yalnızca "yüz görülüyor mu" geri bildirimi verir.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.74f)
                .aspectRatio(0.78f)
                .border(
                    width = 3.dp,
                    color = if (uiState.isFaceDetected) Color(0xFF4ADE80) else Color(0xFF64748B),
                    shape = RoundedCornerShape(percent = 50)
                )
        )

        // Üst bar — geri + badge + adım göstergesi
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!viewModel.onBackPress()) onBack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                CustomScreenBadge("PARTIAL OVERRIDE — LivenessScreen")
            }
            Spacer(Modifier.height(12.dp))
            LivenessStepIndicator(currentStep = uiState.currentStep)
        }

        // Alt panel — aktif adım talimatı + upload durumu
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xFF0B1120).copy(alpha = 0.92f))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = stepIcon(uiState.currentStep),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                // Aktif adımın metni — ViewModel bunu ayrıca seslendirir.
                Text(
                    text = uiState.guidanceMessage.ifBlank { defaultStepText(uiState.currentStep) },
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            // `yaw` ~-100..+100 aralığında normalize bir skordur: sağa dönüş negatif, sola dönüş
            // pozitif. Eşik `LivenessConfig.rotationThreshold`'dan gelir.
            if (uiState.currentStep == LivenessStep.TURN_RIGHT || uiState.currentStep == LivenessStep.TURN_LEFT) {
                Spacer(Modifier.height(14.dp))
                val progress = (abs(uiState.yaw) / LIVENESS_ROTATION_THRESHOLD).coerceIn(0f, 1f)
                val correctDirection = when (uiState.currentStep) {
                    LivenessStep.TURN_RIGHT -> uiState.yaw < 0f
                    else -> uiState.yaw > 0f
                }
                LinearProgressIndicator(
                    progress = { if (correctDirection) progress else 0f },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = if (progress >= 1f && correctDirection) Color(0xFF4ADE80) else Color(0xFF60A5FA),
                    trackColor = Color(0xFF334155)
                )
            }

            if (uiState.isUploading) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = Color(0xFF60A5FA),
                    trackColor = Color(0xFF334155)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Adım backend'e yükleniyor (gerçek API çağrısı)…",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            if (uiState.currentStep == LivenessStep.COMPLETED) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tüm adımlar tamamlandı — bir sonraki modüle geçiliyor.",
                    color = Color(0xFF4ADE80),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Start
                )
            }
        }

        // `errorMessage` temizlenene kadar kamera kareleri işlenmez, bu yüzden hata dialogunun
        // `clearError()` çağırması gerekir.
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

        // Uyarı kapatıldığında aynı adım yeniden denenir.
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

/** Liveness adım sırası: TURN_RIGHT → TURN_LEFT → SMILE → BLINK → COMPLETED. */
private val LIVENESS_STEPS = listOf(
    LivenessStep.TURN_RIGHT,
    LivenessStep.TURN_LEFT,
    LivenessStep.SMILE,
    LivenessStep.BLINK
)

/** `LivenessConfig.rotationThreshold` varsayılanı — yalnızca ilerleme çubuğunu ölçeklemek için. */
private const val LIVENESS_ROTATION_THRESHOLD = 25f

@Composable
private fun LivenessStepIndicator(currentStep: LivenessStep) {
    val currentIndex = if (currentStep == LivenessStep.COMPLETED) {
        LIVENESS_STEPS.size
    } else {
        LIVENESS_STEPS.indexOf(currentStep).coerceAtLeast(0)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LIVENESS_STEPS.forEachIndexed { index, step ->
            val done = index < currentIndex
            val active = index == currentIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = when {
                            done -> Color(0xFF166534)
                            active -> Color(0xFF1D4ED8)
                            else -> Color(0xFF1E293B)
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (done) Icons.Default.Check else stepIcon(step),
                    contentDescription = null,
                    tint = if (done || active) Color.White else Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        // Tamamlandı rozeti — `LivenessStep.COMPLETED`.
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = if (currentStep == LivenessStep.COMPLETED) Color(0xFF16A34A) else Color(0xFF1E293B),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (currentStep == LivenessStep.COMPLETED) Color.White else Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun stepIcon(step: LivenessStep): ImageVector = when (step) {
    LivenessStep.TURN_RIGHT -> Icons.Default.ArrowForward
    LivenessStep.TURN_LEFT -> Icons.Default.ArrowBack
    LivenessStep.SMILE -> Icons.Default.EmojiEmotions
    LivenessStep.BLINK -> Icons.Default.Visibility
    LivenessStep.COMPLETED -> Icons.Default.Check
}

/** `guidanceMessage` ilk kare gelmeden önce boş olduğu için kullanılan yedek metin. */
private fun defaultStepText(step: LivenessStep): String = when (step) {
    LivenessStep.TURN_RIGHT -> "Başınızı Sağa Çevirin"
    LivenessStep.TURN_LEFT -> "Başınızı Sola Çevirin"
    LivenessStep.SMILE -> "Gülümseyin"
    LivenessStep.BLINK -> "Gözlerinizi Kırpın"
    LivenessStep.COMPLETED -> "Tamamlandı"
}
