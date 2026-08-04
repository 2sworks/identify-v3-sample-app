package com.identify.sample.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.presentation.prepare.PreparationViewModel

/**
 * [PreparationScreen][com.identify.sdk.presentation.SdkUiProvider.PreparationScreen] için
 * tamamen özel bir override'ın referans implementasyonu, [SampleUiProvider] üzerinden bağlanıyor.
 *
 * [SelfieScreen][com.identify.sdk.presentation.SdkUiProvider.SelfieScreen]'in aksine, interface
 * burada yalnızca `onBack` veriyor — `onNext` parametresi yok. Preparation modülü,
 * `PreparationViewModel.onContinue()` üzerinden ilerliyor (her ekranın kullandığı
 * `viewModel(factory = SdkViewModelFactory)` deseniyle, public `SdkViewModelFactory` ile alınan);
 * bu metod kendi `canContinue` checklist'i sağlandığında SDK'nın navigator'ını içeriden çağırıyor.
 *
 * Bu ekran da, [CustomSelfieScreen] gibi, host app'in **kendi** ViewModel'ini
 * ([CustomPreparationViewModel] — normal `viewModel()`, SDK'nın haberi yok) SDK'nın **kendi**
 * `PreparationViewModel`'i (`viewModel(factory = SdkViewModelFactory)`) ile YAN YANA kullanıyor.
 * Gerçekten çalışan bir entegrasyondur (mock değil) — gerçek izin istekleri, gerçek checklist
 * state'i — çünkü Preparation'ın yeniden yazılması gereken bir kamera-yakalama pipeline'ı yok.
 */
@Composable
fun CustomPreparationScreen(onBack: () -> Unit) {
    val hostViewModel: CustomPreparationViewModel = viewModel()
    val sdkViewModel: PreparationViewModel = viewModel(factory = SdkViewModelFactory)
    val context = LocalContext.current

    val permissionRequestCount by hostViewModel.permissionRequestCount.collectAsState()

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        sdkViewModel.checkPermissions(context)
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        sdkViewModel.checkPermissions(context)
    }

    LaunchedEffect(Unit) { sdkViewModel.checkPermissions(context) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                CustomScreenBadge("CUSTOM UI PROVIDER — PreparationScreen override")
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Başlamaya Hazır mısınız?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Host app'in kendi PreparationScreen override'ı — gerçek " +
                        "PreparationViewModel.onContinue() checklist'ini sürüyor. " +
                        "Host ViewModel izin talebi sayacı: $permissionRequestCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))

                PermissionRow(
                    label = "Kamera izni",
                    granted = sdkViewModel.cameraPermissionGranted.value,
                    onRequest = {
                        hostViewModel.recordPermissionRequest()
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
                PermissionRow(
                    label = "Mikrofon izni",
                    granted = sdkViewModel.micPermissionGranted.value,
                    onRequest = {
                        hostViewModel.recordPermissionRequest()
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                ChecklistRow(
                    label = "Kimlik belgem yanımda",
                    checked = sdkViewModel.idNear.value,
                    onCheckedChange = { sdkViewModel.idNear.value = it }
                )
                ChecklistRow(
                    label = "Yalnızım",
                    checked = sdkViewModel.isAlone.value,
                    onCheckedChange = { sdkViewModel.isAlone.value = it }
                )
                ChecklistRow(
                    label = "İyi bir ışık ortamındayım",
                    checked = sdkViewModel.goodLight.value,
                    onCheckedChange = { sdkViewModel.goodLight.value = it }
                )
                ChecklistRow(
                    label = "Ses tanıma izni verildi",
                    checked = sdkViewModel.soundRecognitionGranted.value,
                    onCheckedChange = { sdkViewModel.soundRecognitionGranted.value = it }
                )
            }

            Column {
                Button(
                    onClick = { sdkViewModel.onContinue() },
                    enabled = sdkViewModel.canContinue.value,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Devam Et", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Geri")
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean, onRequest: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (granted) Color(0xFF2E7D32) else Color.Gray)
        if (!granted) {
            OutlinedButton(onClick = onRequest) { Text("İzin Ver") }
        } else {
            Text("Verildi", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ChecklistRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
