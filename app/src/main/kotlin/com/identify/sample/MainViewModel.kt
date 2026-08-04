package com.identify.sample

import androidx.lifecycle.AndroidViewModel
import com.identify.sdk.IdentifySdk
import com.identify.sdk.SdkConfig
import com.identify.sdk.SslPin
import com.identify.sdk.core.nfc.NfcDependency
import com.identify.sdk.core.model.SdkLanguage
import com.identify.sdk.core.model.SdkModule
import com.identify.sdk.core.navigation.SdkHooks
import com.identify.sdk.core.navigation.SdkRoutes
import com.identify.sdk.ui.standard.StandardUiProvider
import com.identify.sample.ui.LivenessAfterHookScreen
import com.identify.sample.ui.LivenessBeforeHookScreen
import com.identify.sample.ui.NfcCompletedHookScreen
import com.identify.sample.ui.SampleUiProvider
import com.identify.sample.ui.SelfieConsentHookScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import com.identify.sdk.presentation.document.SelectedDocument
import timber.log.Timber

/**
 * Identify SDK entegrasyonunun referans implementasyonu. [startProcess] içindeki her çağrı,
 * bir partner uygulamasının yapması gerekeni birebir yansıtır — bu sınıf SDK-internal hiçbir
 * kod içermez, yalnızca public API kullanımı (SdkConfig.Builder, IdentifySdk.init/startAuthentication).
 *
 * Çalışma zamanı secret'ları (turnKey, secretKeyBase64, loggerSecretKey, socketSecretKey) burada
 * asla hardcode edilmez — build zamanında local.properties veya CI env değişkenlerinden beslenen
 * BuildConfig alanları üzerinden enjekte edilir. Bkz. local.properties.example.
 */
class MainViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("identify_sample_prefs", Context.MODE_PRIVATE)

    private val _baseUrl = MutableStateFlow("https://apiqa.identify.com.tr/")
    val baseUrl = _baseUrl.asStateFlow()

    private val _identId = MutableStateFlow("")
    val identId = _identId.asStateFlow()

    private val _lastConfigAvailable = MutableStateFlow(false)
    val lastConfigAvailable = _lastConfigAvailable.asStateFlow()

    /**
     * `SdkHooks.setBeforeHook`/`setAfterHook` API'si için demo switch'i — bkz.
     * [com.identify.sample.ui.SelfieConsentHookScreen] ve [startProcess]. Bu, host app'in bir SDK
     * modülünün önüne/sonrasına kendi ekranını enjekte etmesinin referans desenidir.
     */
    private val _useHookDemo = MutableStateFlow(false)
    val useHookDemo = _useHookDemo.asStateFlow()

    fun toggleHookDemo() {
        _useHookDemo.value = !_useHookDemo.value
    }

    /**
     * *Diğer* özelleştirme mekanizması için demo switch'i — tamamen özel bir [SdkUiProvider]
     * (hook'ların aksine; hook bir modülün önüne/sonrasına ekran enjekte eder ama modülün kendi
     * ekranı yine render edilir). Bkz. [com.identify.sample.ui.SampleUiProvider] — PreparationScreen
     * ve SelfieScreen'i override edip diğer tüm ekranları StandardUiProvider'a delege ediyor.
     */
    private val _useCustomUiProvider = MutableStateFlow(false)
    val useCustomUiProvider = _useCustomUiProvider.asStateFlow()

    fun toggleCustomUiProvider() {
        _useCustomUiProvider.value = !_useCustomUiProvider.value
    }

    // Form Alanları
    val name = MutableStateFlow("")
    val surname = MutableStateFlow("")
    val tcId = MutableStateFlow("")
    val serialNumber = MutableStateFlow("")
    val birthDate = MutableStateFlow("")
    val expiryDate = MutableStateFlow("")
    val project = MutableStateFlow("")

    // UI Durumları
    val selectedTab = MutableStateFlow(1) // 0: Yeni Müşteri (devre dışı), 1: Ident ID
    val isOptionsOpen = MutableStateFlow(false)
    val isSettingsOpen = MutableStateFlow(false)
    val isServerSettingsOpen = MutableStateFlow(false)
    val isModuleSelectionOpen = MutableStateFlow(false)
    val isNfcSettingsOpen = MutableStateFlow(false)
    val isEditMode = MutableStateFlow(false)
    val isLanguageMenuOpen = MutableStateFlow(false)

    private val _currentLanguage = MutableStateFlow(SdkLanguage.TR)
    val currentLanguage = _currentLanguage.asStateFlow()

    // NFC Bağımlılık Alanları
    val nfcDocumentNumber = MutableStateFlow("")
    val nfcDateOfBirth = MutableStateFlow("")
    val nfcDateOfExpiry = MutableStateFlow("")

    // Modül Yönetimi
    val allModules = listOf(
        "PREPARE", "ID_CARD", "ID_CARD_OVD", "MRZ_NFC", "LIVENESS",
        "SPEECH", "ADDRESS", "SIGNATURE", "VIDEO_RECORDER", "SELFIE", "CALL_WAIT"
    )

    private val _moduleOrder = MutableStateFlow(allModules)
    val moduleOrder = _moduleOrder.asStateFlow()

    private val _activeModules = MutableStateFlow<Set<String>?>(null)
    val activeModules = _activeModules.asStateFlow()

    init {
        checkLastConfig()
    }

    private fun checkLastConfig() {
        val lastId = prefs.getString("last_ident_id", null)
        _lastConfigAvailable.value = !lastId.isNullOrBlank()
    }

    fun loadLastConfiguration() {
        val lastId = prefs.getString("last_ident_id", "") ?: ""
        val lastModules = prefs.getString("last_modules", null)
        val lastOrder = prefs.getString("last_module_order", null)

        if (lastId.isNotEmpty()) {
            _identId.value = lastId
            selectedTab.value = 1
        }

        if (lastModules != null) {
            _activeModules.value = lastModules.split(";").filter { it.isNotEmpty() }.toSet()
        }

        if (lastOrder != null) {
            _moduleOrder.value = lastOrder.split(";").filter { it.isNotEmpty() }
        }
    }

    private fun saveLastConfiguration(id: String, modules: List<String>?, order: List<String>) {
        prefs.edit().apply {
            putString("last_ident_id", id)
            putString("last_modules", modules?.joinToString(";"))
            putString("last_module_order", order.joinToString(";"))
            apply()
        }
        _lastConfigAvailable.value = id.isNotEmpty()
    }

    fun onIdentIdChange(newId: String) {
        _identId.value = newId
    }

    fun setEnvironment(env: String) {
        when (env) {
            "LIVE" -> _baseUrl.value = "https://api.identify.com.tr/"
            "QA" -> _baseUrl.value = "https://apiqa.identify.com.tr/"
        }
    }

    fun setLanguage(language: SdkLanguage) {
        _currentLanguage.value = language
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(language.code)
        )
    }

    fun toggleModule(module: String) {
        val current = (_activeModules.value ?: allModules.toSet()).toMutableSet()
        if (current.contains(module)) current.remove(module) else current.add(module)
        _activeModules.value = current
    }

    fun moveModule(fromIndex: Int, toIndex: Int) {
        val current = _moduleOrder.value.toMutableList()
        if (fromIndex !in current.indices || toIndex !in current.indices) return
        val item = current.removeAt(fromIndex)
        current.add(toIndex, item)
        _moduleOrder.value = current
    }

    fun ensureCustomMode() {
        if (_activeModules.value == null) {
            _activeModules.value = allModules.toSet()
        }
    }

    fun resetModules() {
        _activeModules.value = null
        _moduleOrder.value = allModules
    }

    fun clearLastConfiguration() {
        prefs.edit().clear().apply()
        _lastConfigAvailable.value = false
    }

    fun saveNfcDependency(documentNumber: String, dateOfBirth: String, dateOfExpiry: String) {
        nfcDocumentNumber.value = documentNumber
        nfcDateOfBirth.value = dateOfBirth
        nfcDateOfExpiry.value = dateOfExpiry
    }

    fun clearNfcDependency() {
        nfcDocumentNumber.value = ""
        nfcDateOfBirth.value = ""
        nfcDateOfExpiry.value = ""
    }

    /**
     * Bir Identify doğrulama akışını başlatmanın referans çağrı sırası:
     * [SdkConfig] oluştur, [IdentifySdk.init]'i çağır, sonra [IdentifySdk.startAuthentication].
     */
    fun startProcess(activity: android.app.Activity) {
        val rawIdentId = if (selectedTab.value == 0) "NEW_CUSTOMER" else _identId.value
        val identIdValue = rawIdentId.trim().filter { it.code >= 32 }

        if (identIdValue.isBlank() && selectedTab.value == 1) {
            Timber.w("startProcess aborted: Ident ID is blank")
            return
        }

        val activeSet = _activeModules.value
        val manualModules = if (activeSet != null) {
            _moduleOrder.value.filter { activeSet.contains(it) }
        } else {
            null
        }

        if (selectedTab.value == 1 && identIdValue.isNotEmpty()) {
            saveLastConfiguration(identIdValue, manualModules, _moduleOrder.value)
        }

        // 1. Config oluştur — turnKey/secretKeyBase64/loggerSecretKey/socketSecretKey, build
        // zamanında local.properties veya CI secret'larından beslenen BuildConfig'ten geliyor
        // (asla hardcode edilmez).
        Timber.i("startProcess: useCustomUiProvider=%s", _useCustomUiProvider.value)
        val uiProvider = if (_useCustomUiProvider.value) SampleUiProvider() else StandardUiProvider()
        val builder = SdkConfig.Builder(_baseUrl.value, BuildConfig.IDENTIFY_TURN_KEY)
            .setIntroEnabled(true)
            .setUiProvider(uiProvider)
            .setCustomModules(manualModules?.mapNotNull { it.toSdkModule() })
            .setLanguage(_currentLanguage.value)
            .setApiTimeout(120)
            .setSecretKeyBase64(BuildConfig.IDENTIFY_SECRET_KEY_BASE64)
            .setLoggerSecretKey(BuildConfig.IDENTIFY_LOGGER_SECRET_KEY)
            .setSocketSecretKey(BuildConfig.IDENTIFY_SOCKET_SECRET_KEY)
            .setSslPins(sslPinsForCurrentBaseUrl())

        if (nfcDocumentNumber.value.isNotBlank() && nfcDateOfBirth.value.isNotBlank() && nfcDateOfExpiry.value.isNotBlank()) {
            builder.setNfcDependency(
                NfcDependency(
                    documentNumber = nfcDocumentNumber.value,
                    dateOfBirth = nfcDateOfBirth.value,
                    dateOfExpiry = nfcDateOfExpiry.value
                )
            )
        }

        val config = builder.build()

        // 2. Hook'lar — SdkHooks'un tüm yeteneklerini kapsayan referans kullanım (docs/hooks.md).
        // Demo switch'i kapalıyken boş bir SdkHooks()'a düşer (parametreyi hiç vermemekle aynı).
        // Test edilen senaryolar:
        //  - sadece setBeforeHook              -> Selfie
        //  - sadece setAfterHook               -> Nfc
        //  - AYNI rotada setBeforeHook + setAfterHook (zincir: before -> modül -> after)
        //                                       -> Liveness — currentIndex/activeHook navigasyon
        //                                          fix'i için en kritik regresyon senaryosu
        //  - kullanıcı dokunuşu olmadan LaunchedEffect ile otomatik ilerleyen after-hook
        //                                       -> Liveness after-hook
        //  - onIdentifyFinished                -> SDK'nın yerleşik başarı ekranını değiştirir
        //  - onIdentifyCancelled               -> kullanıcı akıştan çıkar / host akışı kapatır
        //  - provideErrorMessage               -> SDK hata metnini geçersiz kılar
        //  - provideGuidanceMessage            -> tüm ekranlardaki yönlendirme metnini geçersiz kılar
        Timber.i("startProcess: useHookDemo=%s", _useHookDemo.value)
        val hooks = if (_useHookDemo.value) {
            SdkHooks().apply {
                setBeforeHook(SdkRoutes.Selfie::class) { onNext ->
                    SelfieConsentHookScreen(onNext = onNext)
                }
                setAfterHook(SdkRoutes.Nfc::class) { onNext ->
                    NfcCompletedHookScreen(onNext = onNext)
                }
                setBeforeHook(SdkRoutes.Liveness::class) { onNext ->
                    LivenessBeforeHookScreen(onNext = onNext)
                }
                setAfterHook(SdkRoutes.Liveness::class) { onNext ->
                    LivenessAfterHookScreen(onNext = onNext)
                }

                onIdentifyFinished = {
                    Timber.i("Hook demo: onIdentifyFinished — skipping SDK's ResultSuccess screen")
                    android.widget.Toast.makeText(
                        activity,
                        "[DEMO] Doğrulama tamamlandı (custom onIdentifyFinished)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    activity.finish()
                }

                onIdentifyCancelled = {
                    Timber.i("Hook demo: onIdentifyCancelled")
                    android.widget.Toast.makeText(
                        activity,
                        "[DEMO] Doğrulama iptal edildi (onIdentifyCancelled)",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }

                provideErrorMessage = { code, message, throwable ->
                    Timber.w("Hook demo: provideErrorMessage code=%s message=%s throwable=%s", code, message, throwable)
                    message?.let { "[DEMO] $it" }
                }

                provideGuidanceMessage = { key, defaultMessage ->
                    Timber.d("Hook demo: provideGuidanceMessage key=%s default=%s", key, defaultMessage)
                    "[DEMO] $defaultMessage"
                }
            }
        } else {
            SdkHooks()
        }
        Timber.i("startProcess: built hooks instance=%s", System.identityHashCode(hooks))

        IdentifySdk.init(application = activity.application, config = config, hooks = hooks)

        // 3. Doğrulamayı Başlat
        IdentifySdk.startAuthentication(activity, identIdValue)
    }

    /**
     * SSL pinning burada yalnızca QA host'u için gösteriliyor — kendi production pin'lerinizle
     * değiştirin. Pin'lerin nasıl üretildiği için bkz. PUBLISHING_GUIDE / docs/sdk-config.md.
     */
    private fun sslPinsForCurrentBaseUrl(): List<SslPin> {
        val host = runCatching { java.net.URI(_baseUrl.value).host }.getOrNull().orEmpty()
        return if (host == "apiqa.identify.com.tr") {
            listOf(
                SslPin(host, "sha256/AlkzBmsGfIi9rbPQh9ID9naO4bqDG4l4NOftwkTqKa8="),
                SslPin(host, "sha256/brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4=")
            )
        } else {
            emptyList()
        }
    }
}

private fun String.toSdkModule(): SdkModule? = when (this) {
    "PREPARE"        -> SdkModule.PREPARE
    "ID_CARD"        -> SdkModule.ID_CARD
    "ID_CARD_OVD"    -> SdkModule.ID_CARD_OVD
    "MRZ_NFC"        -> SdkModule.NFC
    "LIVENESS"       -> SdkModule.LIVENESS
    "SPEECH"         -> SdkModule.SPEECH
    "ADDRESS"        -> SdkModule.ADDRESS
    "SIGNATURE"      -> SdkModule.SIGNATURE
    "VIDEO_RECORDER" -> SdkModule.VIDEO_RECORD
    "SELFIE"         -> SdkModule.SELFIE
    "CALL_WAIT"      -> SdkModule.AGENT_CALL
    else             -> null
}
