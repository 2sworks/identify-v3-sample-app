package com.identify.sample.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.Tag
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.identify.sdk.IdentifyActivity
import com.identify.sdk.core.di.SdkViewModelFactory
import com.identify.sdk.core.nfc.NfcAvailabilityChecker
import com.identify.sdk.presentation.nfc.NfcError
import com.identify.sdk.presentation.nfc.NfcStatus
import com.identify.sdk.presentation.nfc.NfcViewModel
import kotlinx.coroutines.delay

/**
 *
 *
 * - `IdentifyActivity.enableNfcReaderMode()` / `disableNfcReaderMode()` — ekran açıkken/kapanırken
 *   çağrılır, sistemin kendi NFC bildirimini bastırıp tag'leri SDK'ya yönlendirir.
 * - `IdentifyActivity.getNfcTagFlow(): Flow<Tag>` — algılanan NFC tag'lerini yayınlar.
 *
 *
 * ## Bağımlılık notu — Selfie'nin aksine burada YENİ bir Gradle bağımlılığı gerekmiyor
 *
 *
 * Tek gereksinim `AndroidManifest.xml`'de `android.permission.NFC`'nin tanımlı olması — bu zaten `sdk-core`'un
 * kendi manifest'inde var (`docs/dependencies.md`), host manifest'ine ayrıca eklemenize gerek yok.
 *
 * ## Manuel MRZ girişi neden var
 *
 * NFC çipini okumak için önce belge seri no + doğum tarihi + son geçerlilik tarihi (MRZ verisi)
 * gerekiyor — bu veriler kimlik kartı arka yüz taramasından ya da handshake'te backend'den şifreli
 * gelir. `NfcViewModel.hasMrzData()` bu veri eksikse `NfcStatus.NEED_MANUAL_INPUT`'a geçer; siz de
 * SDK'nın kendi ekranındaki gibi basit bir "MRZ'yi elle gir" dialogu göstermeniz gerekiyor
 * (`viewModel.onManualMrzSubmitted(...)`). Buradaki dialog, SDK'nın kendi `ManualMrzInputDialog`'unun
 * (tarih seçici, format ipucu vb. olmadan) sadeleştirilmiş hali — üç düz metin alanı yeterli.
 *
 * [SampleUiProvider]'daki `NfcScreen(...)` override'ı bu ekranı kullanıyor.
 */
@Composable
fun MyNfcScreen(onNext: () -> Unit, onBack: () -> Unit) {
    val viewModel: NfcViewModel = viewModel(factory = SdkViewModelFactory)
    val state by viewModel.uiState
    val context = LocalContext.current
    val activity = remember(context) { context.findIdentifyActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isNfcEnabled by remember { mutableStateOf(NfcAvailabilityChecker.isNfcEnabled(context)) }
    val isNfcAvailable = remember { NfcAvailabilityChecker.isNfcAvailable(context) }
    var showManualInput by remember { mutableStateOf(false) }

    // COMPLETED (SUCCESS) veya MAX_RETRIES_REACHED'e ulaşınca bir sonraki modüle geç.
    LaunchedEffect(state.status) {
        when (state.status) {
            NfcStatus.SUCCESS -> {
                delay(1500)
                viewModel.onNextClicked()
            }
            NfcStatus.MAX_RETRIES_REACHED -> {
                delay(2000)
                viewModel.onNextClicked()
            }
            else -> {}
        }
    }

    // Kullanıcı Ayarlar'dan NFC'yi açıp geri döndüğünde otomatik algıla.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val wasEnabled = isNfcEnabled
                isNfcEnabled = NfcAvailabilityChecker.isNfcEnabled(context)
                if (!wasEnabled && isNfcEnabled && state.status == NfcStatus.NFC_DISABLED) {
                    viewModel.onNfcEnabled()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Ekran görünürken sistemin NFC bildirimini bastır, tag'leri bize yönlendir; ekrandan
    // çıkınca kapat .
    DisposableEffect(activity) {
        activity?.enableNfcReaderMode()
        onDispose { activity?.disableNfcReaderMode() }
    }

    // Algılanan tag'i, sadece ViewModel gerçekten bekliyorsa ViewModel'e ilet.
    LaunchedEffect(activity) {
        activity?.getNfcTagFlow()?.collect { tag: Tag ->
            val currentStatus = viewModel.uiState.value.status
            if (currentStatus == NfcStatus.WAITING_FOR_TAG ||
                currentStatus == NfcStatus.READ_ERROR ||
                currentStatus == NfcStatus.API_ERROR
            ) {
                viewModel.onNfcTagDiscovered(tag, context)
            }
        }
    }

    LaunchedEffect(state.manualInputRequestNonce) {
        if (state.manualInputRequestNonce > 0) showManualInput = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    CustomScreenBadge("PARTIAL OVERRIDE — NfcScreen")
                }
                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(Color(0xFF1E293B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.status == NfcStatus.READING || state.status == NfcStatus.VERIFYING) {
                        CircularProgressIndicator(color = Color(0xFF60A5FA))
                    } else {
                        Icon(
                            Icons.Default.Nfc,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))

                Text(
                    text = errorText(state.error)
                        ?: state.guidanceMessage.takeIf { it.isNotBlank() }
                        ?: statusText(state.status),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )

                if (state.status == NfcStatus.READING) {
                    Spacer(Modifier.height(8.dp))
                    Text("%${state.progress.coerceIn(0, 100)}", color = Color(0xFF94A3B8))
                }
            }

            Column {
                NfcActionButton(
                    state = state,
                    isNfcAvailable = isNfcAvailable,
                    isNfcEnabled = isNfcEnabled,
                    onStartScan = {
                        when {
                            !isNfcAvailable -> viewModel.onNfcNotAvailable()
                            !isNfcEnabled -> {
                                viewModel.onNfcDisabled()
                                context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                            }
                            else -> viewModel.onStartScanClicked()
                        }
                    },
                    onOpenSettings = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                    onRetry = {
                        viewModel.onRetryClicked()
                        activity?.enableNfcReaderMode()
                    },
                    onManualInput = { showManualInput = true }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("Geri", color = Color.White) }
            }
        }
    }

    if (showManualInput) {
        ManualMrzDialog(
            initialDocNumber = viewModel.getDocumentNumber() ?: "",
            initialBirthDate = viewModel.getDateOfBirth() ?: "",
            initialExpiryDate = viewModel.getDateOfExpiry() ?: "",
            isValidDocNumber = { viewModel.isValidDocumentNumber(it) },
            onDismiss = { showManualInput = false },
            onSubmit = { docNumber, birthDate, expiryDate ->
                viewModel.onManualMrzSubmitted(docNumber, birthDate, expiryDate)
                showManualInput = false
            }
        )
    }
}

@Composable
private fun NfcActionButton(
    state: com.identify.sdk.presentation.nfc.NfcUiState,
    isNfcAvailable: Boolean,
    isNfcEnabled: Boolean,
    onStartScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onManualInput: () -> Unit
) {
    val (label, enabled, onClick) = when (state.status) {
        NfcStatus.IDLE -> Triple("Taramayı Başlat", true, onStartScan)
        NfcStatus.WAITING_FOR_TAG -> Triple("Bekleniyor…", false, {})
        NfcStatus.READING, NfcStatus.VERIFYING -> Triple("Okunuyor…", false, {})
        NfcStatus.NFC_DISABLED -> Triple("NFC Ayarlarını Aç", true, onOpenSettings)
        NfcStatus.WRONG_DATA, NfcStatus.READ_ERROR, NfcStatus.API_ERROR -> Triple("Tekrar Dene", true, onRetry)
        NfcStatus.NEED_MANUAL_INPUT -> Triple("MRZ Bilgilerini Gir", true, onManualInput)
        NfcStatus.SUCCESS, NfcStatus.MAX_RETRIES_REACHED -> Triple(null, false, {})
    }
    if (label != null) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF60A5FA))
        ) { Text(label, color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ManualMrzDialog(
    initialDocNumber: String,
    initialBirthDate: String,
    initialExpiryDate: String,
    isValidDocNumber: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSubmit: (docNumber: String, birthDate: String, expiryDate: String) -> Unit
) {
    var docNumber by remember { mutableStateOf(initialDocNumber) }
    var birthDate by remember { mutableStateOf(initialBirthDate) }
    var expiryDate by remember { mutableStateOf(initialExpiryDate) }

    fun isValidYymmdd(v: String) = v.length == 6 && v.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MRZ Bilgilerini Gir") },
        text = {
            Column {
                OutlinedTextField(
                    value = docNumber,
                    onValueChange = { docNumber = it.uppercase().replace(" ", "") },
                    label = { Text("Belge Seri No") },
                    singleLine = true,
                    isError = docNumber.isNotEmpty() && !isValidDocNumber(docNumber),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = birthDate,
                    onValueChange = { if (it.length <= 6) birthDate = it.filter(Char::isDigit) },
                    label = { Text("Doğum Tarihi (yyMMdd)") },
                    singleLine = true,
                    isError = birthDate.isNotEmpty() && !isValidYymmdd(birthDate),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = expiryDate,
                    onValueChange = { if (it.length <= 6) expiryDate = it.filter(Char::isDigit) },
                    label = { Text("Son Geçerlilik Tarihi (yyMMdd)") },
                    singleLine = true,
                    isError = expiryDate.isNotEmpty() && !isValidYymmdd(expiryDate),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValidDocNumber(docNumber) && isValidYymmdd(birthDate) && isValidYymmdd(expiryDate),
                onClick = { onSubmit(docNumber.trim(), birthDate, expiryDate) }
            ) { Text("Tamam") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İptal") } }
    )
}

private fun statusText(status: NfcStatus): String = when (status) {
    NfcStatus.IDLE -> "NFC okumaya hazır."
    NfcStatus.WAITING_FOR_TAG -> "Telefonun arkasını kimliğin/pasaportun çip bölümüne yaklaştırın."
    NfcStatus.NEED_MANUAL_INPUT -> "Devam etmek için MRZ bilgilerini girin."
    NfcStatus.READING -> "Çip okunuyor, telefonu sabit tutun…"
    NfcStatus.VERIFYING -> "Doğrulanıyor…"
    NfcStatus.SUCCESS -> "NFC doğrulama başarılı."
    NfcStatus.WRONG_DATA -> "Girilen bilgiler çip ile eşleşmedi."
    NfcStatus.READ_ERROR -> "Okuma hatası, tekrar deneyin."
    NfcStatus.API_ERROR -> "Sunucu hatası, tekrar deneyin."
    NfcStatus.MAX_RETRIES_REACHED -> "Deneme limiti aşıldı."
    NfcStatus.NFC_DISABLED -> "NFC kapalı, lütfen ayarlardan açın."
}

private fun errorText(error: NfcError?): String? = when (error) {
    null -> null
    NfcError.WRONG_MRZ_DATA -> "Girilen MRZ bilgileri hatalı."
    NfcError.AUTH_FAILED -> "Çip doğrulaması başarısız."
    NfcError.CONNECTION_LOST -> "Bağlantı koptu, telefonu tekrar yaklaştırın."
    NfcError.NFC_NOT_AVAILABLE -> "Bu cihazda NFC donanımı yok."
    NfcError.NFC_DISABLED -> "NFC kapalı."
    NfcError.COMPARISON_FAILED -> "Yüz karşılaştırma uyuşmadı."
    NfcError.API_ERROR -> "Sunucu hatası."
    NfcError.NETWORK_ERROR -> "Ağ hatası."
    NfcError.MAX_RETRIES_REACHED -> "Deneme limiti aşıldı."
}

/**
 * Activity finder fonksiyonumuz
 */
private tailrec fun Context.findIdentifyActivity(): IdentifyActivity? = when (this) {
    is IdentifyActivity -> this
    is ContextWrapper -> baseContext.findIdentifyActivity()
    else -> null
}
