package com.identify.sample.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.IdentifySdk
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.core.webrtc.AudioDeviceType
import com.identify.sdk.presentation.agentcall.AgentCallState
import com.identify.sdk.presentation.agentcall.AgentCallViewModel
import com.identify.sdk.ui.standard.agentcall.VideoRenderer

/**
 * `AgentCallScreen`'in (operatörle görüntülü görüşme) "kısmi özelleştirme" referans
 * implementasyonu — WebRTC bağlantısını, socket sinyalleşmesini ve kuyruk yönetimini SDK yapar,
 * host app yalnızca görsel katmanı yazar.
 *
 * Diğer custom ekranlardan üç farkı var:
 *
 * - **Navigasyonu ViewModel yapar.** Görüşme tamamlandığında `AgentCallViewModel` bir sonraki
 *   modüle kendisi geçer, bu yüzden bu ekran `onNext()` çağırmaz (imza uyumu için parametre
 *   duruyor). `onBack` yalnızca cevapsız çağrıdan çıkış için kullanılır.
 * - **Kamera + mikrofon izni kabul öncesinde alınır.** `onAcceptCall()` izinler verilmeden
 *   çağrılırsa görüşme başlatılamaz.
 * - **Video karelerini SDK'nın `VideoRenderer`'ı çizer.** `SurfaceViewRenderer` yaşam döngüsünü
 *   (init/mirror/release) o yönetiyor; bize yalnızca `EglBase` context'ini vermek kalıyor:
 *   `IdentifySdk.webRtcManager`.
 *
 * ## Bağımlılık notu
 *
 * WebRTC, `sdk-core`'a `implementation` scope'unda geliyor — yani APK'ya giriyor ama host'un
 * derleme classpath'inde olmuyor. Bu ekran `VideoRenderer`'ın `VideoTrack` / `EglBase.Context`
 * imzasını çözebilmek için `app/build.gradle.kts`'e `io.github.webrtc-sdk:android` eklendi;
 * `MySelfieScreen` için CameraX'te yapılanın aynısı (bkz. `docs/dependencies.md`).
 *
 * [SampleUiProvider]'daki `AgentCallScreen(...)` override'ı bu ekranı kullanıyor.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun MyAgentCallScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val viewModel: AgentCallViewModel = viewModel(factory = SdkViewModelFactory)
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Agent ekran görüntüsü aldığında tek seferlik mesaj gelir.
    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Short)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) viewModel.onAcceptCall()
    }

    fun acceptCall() {
        val needed = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        val missing = needed.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permissionLauncher.launch(needed) else viewModel.onAcceptCall()
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1120))) {
        when (val current = state) {
            is AgentCallState.Waiting -> WaitingPanel(
                queuePosition = current.queuePosition,
                estimatedWaitTimeMinutes = current.estimatedWaitTimeMinutes
            )

            is AgentCallState.IncomingCall -> IncomingCallPanel(onAccept = { acceptCall() })

            is AgentCallState.InCall -> InCallPanel(
                state = current,
                onToggleMic = { viewModel.toggleMic() },
                onSwitchCamera = { viewModel.switchCamera() },
                onSetAudioDevice = { viewModel.setAudioDevice(it) },
                onEndCall = { viewModel.endCall() }
            )

            is AgentCallState.ConnectionLost -> InfoPanel(
                title = "Bağlantı koptu",
                message = "İnternet bağlantınızı kontrol edip yeniden bağlanın.",
                primaryLabel = "Yeniden Bağlan",
                onPrimary = { viewModel.onReconnect() }
            )

            is AgentCallState.MissedCall -> InfoPanel(
                title = "Temsilci cevap vermedi",
                message = "Tekrar kuyruğa girebilir ya da bu adımdan çıkabilirsiniz.",
                primaryLabel = "Tekrar Dene",
                onPrimary = { viewModel.onRetryAfterMissedCall() },
                secondaryLabel = "Çık",
                onSecondary = onBack
            )
        }

        // Görüşme sırasında video tüm ekranı kapladığı için badge'i üstte ayrı katmanda tutuyoruz.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            CustomScreenBadge("PARTIAL OVERRIDE — AgentCallScreen")
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp)
        )
    }
}

@Composable
private fun WaitingPanel(queuePosition: Int, estimatedWaitTimeMinutes: Int) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = Color(0xFF60A5FA))
        Spacer(Modifier.height(24.dp))
        Text(
            "Temsilci bekleniyor",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (queuePosition > 0) "Sıradaki yeriniz: $queuePosition" else "Sıraya alınıyorsunuz…",
            color = Color(0xFFCBD5E1),
            style = MaterialTheme.typography.bodyMedium
        )
        if (estimatedWaitTimeMinutes > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Tahmini bekleme: ~$estimatedWaitTimeMinutes dk",
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun IncomingCallPanel(onAccept: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Temsilci arıyor",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Görüşmeyi kabul ettiğinizde kamera ve mikrofon izni istenir.",
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onAccept,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
        ) {
            Text("Görüşmeyi Kabul Et", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InCallPanel(
    state: AgentCallState.InCall,
    onToggleMic: () -> Unit,
    onSwitchCamera: () -> Unit,
    onSetAudioDevice: (AudioDeviceType) -> Unit,
    onEndCall: () -> Unit
) {
    // EglBase context ve ses cihazı listesi SDK'nın WebRtcManager'ından gelir.
    val webRtcManager = IdentifySdk.webRtcManager
    val eglContext = webRtcManager?.getEglBaseContext() ?: return
    val availableDevices by webRtcManager.availableAudioDevices.collectAsState()
    var showAudioDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (state.remoteTrack != null) {
            VideoRenderer(
                videoTrack = state.remoteTrack,
                eglBaseContext = eglContext,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        // Kendi görüntümüz (PIP) — ön kamerada aynalanır, arka kameraya geçilince aynalama kapanır.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(16.dp)
                .size(110.dp, 150.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .background(Color.DarkGray, RoundedCornerShape(12.dp))
        ) {
            VideoRenderer(
                videoTrack = state.localTrack,
                eglBaseContext = eglContext,
                modifier = Modifier.fillMaxSize(),
                isMirror = state.isFrontCamera,
                zOrderMediaOverlay = true
            )
        }

        // Flaş temsilci tarafından açılır; kullanıcıya yalnızca durum olarak gösteriyoruz.
        if (state.isFlashEnabled) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFACC15).copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Flaş açık", color = Color.Black, style = MaterialTheme.typography.labelMedium)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CallControl(
                icon = if (state.isMicEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                description = "Mikrofon",
                background = if (state.isMicEnabled) Color.White.copy(alpha = 0.2f) else Color(0xFFDC2626),
                onClick = onToggleMic
            )
            CallControl(
                icon = Icons.Default.Cameraswitch,
                description = "Kamera değiştir",
                background = Color.White.copy(alpha = 0.2f),
                onClick = onSwitchCamera
            )
            CallControl(
                icon = Icons.Default.VolumeUp,
                description = "Ses cihazı",
                background = Color.White.copy(alpha = 0.2f),
                onClick = { showAudioDialog = true }
            )
            CallControl(
                icon = Icons.Default.CallEnd,
                description = "Görüşmeyi bitir",
                background = Color(0xFFDC2626),
                onClick = onEndCall
            )
        }

        if (showAudioDialog) {
            AlertDialog(
                onDismissRequest = { showAudioDialog = false },
                title = { Text("Ses Cihazı") },
                text = {
                    Column {
                        availableDevices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSetAudioDevice(device)
                                        showAudioDialog = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = state.currentAudioDevice == device.label,
                                    onClick = null
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(device.label)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAudioDialog = false }) { Text("Kapat") }
                }
            )
        }
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    background: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

@Composable
private fun InfoPanel(
    title: String,
    message: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            title,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = Color(0xFF94A3B8),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
        ) {
            Text(primaryLabel, color = Color.Black, fontWeight = FontWeight.Bold)
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onSecondary,
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(secondaryLabel, color = Color.White)
            }
        }
    }
}
