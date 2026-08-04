package com.identify.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * `SdkHooks.setBeforeHook(SdkRoutes.Selfie::class)`'in referans kullanımı. Gerçek Selfie
 * modülünden hemen önce enjekte edilir — [onNext] çağrılana kadar SDK Selfie'ye girmez.
 * Bkz. docs/hooks.md.
 */
@Composable
fun SelfieConsentHookScreen(onNext: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                HookBadge(text = "HOOK — BEFORE Selfie (setBeforeHook)")
                Spacer(modifier = Modifier.height(24.dp))
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Before You Take a Selfie",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This screen is injected by the host app via setBeforeHook — it is not part of " +
                        "the SDK's own UI. Continuing means you consent to your selfie photo being " +
                        "processed for identity verification.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Column {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("I Agree, Continue", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The SDK will not enter the Selfie module until onNext() is called.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }
        }
    }
}

/**
 * `SdkHooks.setAfterHook(SdkRoutes.Nfc::class)`'in referans kullanımı. NFC modülü tamamlandıktan
 * hemen sonra, akış bir sonraki modüle geçmeden önce enjekte edilir.
 */
@Composable
fun NfcCompletedHookScreen(onNext: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                HookBadge(text = "HOOK — AFTER Nfc (setAfterHook)")
                Spacer(modifier = Modifier.height(24.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "NFC Reading Complete",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Also injected by the host app via setAfterHook. In a real integration this is " +
                        "where you'd fire an analytics event or show a short transition message.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Continue", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * `SdkHooks.setBeforeHook(SdkRoutes.Liveness::class)`'in referans kullanımı. Hem before hem
 * after hook'u olan bir rotayı test etmek için aşağıdaki [LivenessAfterHookScreen] ile eşleştirildi
 * — docs/hooks.md'deki "Çalışma Sırası" zinciri: [Before Hook] -> [SDK Modülü] -> [After Hook] ->
 * [Sonraki Modül]. Bu kombinasyon, currentIndex/activeHook navigasyon fix'i için en kritik
 * regresyon senaryosudur, çünkü tek bir modülün etrafında art arda iki hook geçişini zorluyor.
 */
@Composable
fun LivenessBeforeHookScreen(onNext: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                HookBadge(text = "HOOK — BEFORE Liveness (setBeforeHook)")
                Spacer(modifier = Modifier.height(24.dp))
                Icon(
                    Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.height(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Liveness Check Coming Up",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "This module also has an AFTER hook registered — after the real Liveness module " +
                        "finishes, you'll see another host-injected screen before moving on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Start Liveness Check", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * `SdkHooks.setAfterHook(SdkRoutes.Liveness::class)`'in referans kullanımı. Diğer demo
 * ekranlarından (manuel dokunma) farklı olarak, doküman'daki "Liveness sonrası analytics eventi"
 * örneğini birebir yansıtıyor — [onNext]'i kullanıcı etkileşimi olmadan, bir `LaunchedEffect`
 * içinden otomatik çağırıyor. Bu, hook'un akışı kendiliğinden ilerlettiği kod yolunu test eder;
 * buton tabanlı onNext()'ten farklı bir timing senaryosudur.
 */
@Composable
fun LivenessAfterHookScreen(onNext: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1200)
        onNext()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HookBadge(text = "HOOK — AFTER Liveness (setAfterHook, auto-advance)")
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Sending analytics event…",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Text(
                "(auto-continues, no tap needed — same pattern as docs/hooks.md)",
                style = MaterialTheme.typography.labelSmall,
                color = Color.LightGray
            )
        }
    }
}

@Composable
private fun HookBadge(text: String) {
    Box(
        modifier = Modifier
            .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF856404), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
