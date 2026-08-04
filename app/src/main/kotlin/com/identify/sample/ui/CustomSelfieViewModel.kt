package com.identify.sample.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Host app'in KENDİ ViewModel'i — SDK'nın hiç bilmediği, tamamen bizim uygulamamıza ait UI durumu.
 * [CustomSelfieScreen] içinde, SDK'nın kendi `SelfieViewModel`'i (bkz.
 * `viewModel(factory = SdkViewModelFactory)`) ile YAN YANA kullanılır.
 *
 * Normal `viewModel()` (Compose'un varsayılan factory'si) ile alınır — `SdkViewModelFactory`'ye
 * hiç ihtiyaç yok, çünkü bu sınıf SDK'nın parçası değil, tamamen host app'e ait.
 *
 * Burada tuttuğumuz alanlar sadece örnek: kendi ekranınızda ihtiyaç duyduğunuz herhangi bir
 * host-side state'i (rıza metni onayı, kendi analytics sayaçlarınız, A/B test varyantı vb.)
 * aynı şekilde burada tutabilirsiniz.
 */
class CustomSelfieViewModel : ViewModel() {

    private val _consentGiven = MutableStateFlow(false)
    val consentGiven = _consentGiven.asStateFlow()

    private val _captureAttempts = MutableStateFlow(0)
    val captureAttempts = _captureAttempts.asStateFlow()

    fun giveConsent() {
        _consentGiven.value = true
    }

    fun recordCaptureAttempt() {
        _captureAttempts.value += 1
    }
}
