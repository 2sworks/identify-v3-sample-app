package com.identify.sample.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Host app'in KENDİ ViewModel'i — [CustomPreparationScreen] içinde SDK'nın `PreparationViewModel`'i
 * (`viewModel(factory = SdkViewModelFactory)` ile alınan) ile YAN YANA kullanılır. Normal
 * `viewModel()` (Compose'un varsayılan factory'si) ile alınır, `SdkViewModelFactory` gerekmez.
 *
 * Burada, kullanıcının izin isteme butonlarına kaç kez bastığını sayan basit bir host-side
 * analytics örneği tutuyoruz — gerçek bir entegrasyonda bunun yerine kendi analytics event'lerinizi,
 * onboarding durumunuzu vb. tutabilirsiniz.
 */
class CustomPreparationViewModel : ViewModel() {

    private val _permissionRequestCount = MutableStateFlow(0)
    val permissionRequestCount = _permissionRequestCount.asStateFlow()

    fun recordPermissionRequest() {
        _permissionRequestCount.value += 1
    }
}
