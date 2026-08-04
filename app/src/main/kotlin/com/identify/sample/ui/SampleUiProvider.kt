package com.identify.sample.ui

import androidx.compose.runtime.Composable
import com.identify.sdk.presentation.SdkUiProvider
import com.identify.sdk.ui.standard.StandardUiProvider

/**
 * "Tamamen özel ekran" pattern'inin referans implementasyonu (docs/ui-customization.md).
 *
 * `StandardUiProvider` `final`'dır — extend edilemez (`class MyUiProvider : StandardUiProvider()`
 * derlenmez). Desteklenen yöntem composition'dır: [SdkUiProvider]'ı doğrudan implement edin, bir
 * [StandardUiProvider] instance'ı tutun, yalnızca değiştirmek istediğiniz ekran(lar)ı override edin,
 * geri kalan her ekranı tek satırlık bir çağrıyla ona delege edin.
 *
 * Bu demo [PreparationScreen] ve [SelfieScreen]'i override ediyor; diğer tüm ekranlar doğrudan
 * SDK'nın kendi UI'ına düşüyor.
 *
 * ---
 * ## `onNext` neden bazı metodlarda var, bazılarında yok?
 *
 * [SdkUiProvider] interface'indeki imza, o ekranın akışı **nasıl ilerlettiğine** göre değişir:
 *
 * - **`onNext` + `onBack` olanlar** (Selfie, Passport, Nfc, Liveness, Signature, Speech,
 *   VideoRecord, Address, AgentCall, OtherDocument, DocumentCombinedScreen): bu ekranlar her zaman
 *   **sabit bir sonraki modüle** gidiyor. `IdentifyNavGraph.kt` bu callback'i doğrudan
 *   `onNext = { navigator.navigateNext() }` olarak veriyor — sizin işiniz sadece modül
 *   tamamlandığında bu hazır callback'i çağırmak.
 *
 * - **Sadece `onBack` olanlar** (PreparationScreen, DocumentSelectionScreen, OvdScreen) —
 *   çünkü "sonraki ekran" tek/sabit değil, o modülün kendi iç mantığına göre değişiyor:
 *     - `DocumentSelectionScreen`: kullanıcının ID Card/Passport/Other seçimi **farklı rotalara**
 *       gidiyor. `DocumentSelectionViewModel.applyClientSideRouting()` modül listesini çalışma
 *       zamanında yeniden yazıp `navigator.navigateNext()`'i **kendisi** çağırıyor.
 *     - `PreparationScreen`: `PreparationViewModel.onContinue()`, kendi checklist'i
 *       (`canContinue`) tamamsa `navigator.navigateNext()`'i içeriden çağırıyor
 *       (bkz. [CustomPreparationScreen]).
 *     - `OvdScreen`: çok adımlı (ön+hologram+arka tarama + backend onayı) iç akışı bittiğinde
 *       ViewModel kendisi navigasyonu tetikliyor.
 *
 *   Kural: modül tamamlanma mantığı basit/doğrusal ise arayüz size hazır bir `onNext` veriyor;
 *   modülün kendi iç state machine'i / dinamik rota kararı varsa arayüzde `onNext` yok, o
 *   modülün ViewModel'i (`viewModel(factory = SdkViewModelFactory)` ile alınan) navigasyonu
 *   kendisi yönetiyor.
 *
 * `HandshakeScreen` (`onSuccess`/`onFailure`) ve `ResultSuccessScreen` (`onFinish`) de aynı
 * sebepten farklı imzaya sahip — akışın giriş/çıkış noktaları, "sonraki modül" kavramı onlara
 * uygulanmıyor.
 *
 * ---
 * ## `standard.xxx(...)`'e delege etmek zorunlu mu?
 *
 * `SdkUiProvider`'ın hiçbir metodunda default implementasyon yok (hepsi `abstract`) — yani
 * interface'i implement ederken **17 metodun tamamına bir gövde yazmak derleme zorunluluğu**,
 * boş bırakamazsınız. Ama "bir gövde yazmak" ile "`standard.xxx(...)`'e delege etmek" aynı şey
 * değil, üç seçeneğiniz var:
 *
 * 1. `standard.xxx(...)`'e delege — o ekran SDK'nın varsayılan UI'ıyla render edilir (aşağıdaki
 *    örneklerin çoğu gibi).
 * 2. Kendi custom composable'ınızı yazın — o ekran tamamen sizin UI'nızla render edilir
 *    ([PreparationScreen], [SelfieScreen] gibi).
 * 3. **Boş gövde** (`override fun DocumentSelectionScreen(onBack: () -> Unit) {}`) — derlenir,
 *    hata vermez, ama o modül gerçekten ziyaret edildiğinde kullanıcı bomboş bir ekranla
 *    karşılaşır (buton/içerik yok, ilerleyecek yolu yok). Bu sessiz bir bug'dır, Kotlin hiçbir
 *    uyarı vermez.
 *
 * Üçüncü seçenek **sadece** o modül `SdkConfig.setCustomModules(...)` listenizde hiç aktif
 * değilse zararsızdır (rota hiç ziyaret edilmediği için boş gövde hiç çalışmaz — sadece
 * derleyiciyi susturmak için var). Aktif bir modülü yanlışlıkla boş bırakırsanız, gerçek bir
 * kullanıcı orada takılı kalır. Bu yüzden kullanılmayacağından emin olduğunuz modüller için boş
 * `{}` yerine şunu tercih edin:
 *
 * ```kotlin
 * override fun SpeechScreen(onNext: () -> Unit, onBack: () -> Unit) {
 *     error("Speech modülü bu entegrasyonda kullanılmıyor")
 * }
 * ```
 *
 * `error(...)` ile stub bırakmak, boş bir composable bırakmaktan daha güvenlidir: ileride biri
 * yanlışlıkla o modülü config'e eklerse sessizce boş ekran göstermek yerine anında hata alınır
 * ve yanlış konfigürasyon hemen fark edilir. (Bu demo'da 17 metodun tamamı `standard`'a delege
 * edildiği veya override edildiği için `error()` stub'a burada ihtiyaç yok — yukarıdaki sadece
 * gerçekten kullanılmayan bir modülünüz olduğunda uygulayacağınız pattern.)
 */
class SampleUiProvider : SdkUiProvider {
    private val standard = StandardUiProvider()

    @Composable
    override fun HandshakeScreen(identificationId: String, onSuccess: () -> Unit, onFailure: (String) -> Unit) =
        standard.HandshakeScreen(identificationId, onSuccess, onFailure)

    @Composable
    override fun IntroScreen(onNext: () -> Unit) = standard.IntroScreen(onNext)

    // Özel — CustomPreparationScreen'e bakın: onBack'in tek başına olmasının sebebi ve
    // PreparationViewModel.onContinue() ile gerçek navigasyon entegrasyonu orada anlatılıyor.
    @Composable
    override fun PreparationScreen(onBack: () -> Unit) {
        CustomPreparationScreen(onBack = onBack)
    }

    @Composable
    override fun DocumentSelectionScreen(onBack: () -> Unit) = standard.DocumentSelectionScreen(onBack)

    @Composable
    override fun DocumentCombinedScreen(onNext: () -> Unit, onBack: () -> Unit) =
        standard.DocumentCombinedScreen(onNext, onBack)

    @Composable
    override fun OvdScreen(onBack: () -> Unit) = standard.OvdScreen(onBack)

    // Özel — CustomSelfieScreen'e bakın: hem SDK'nın SelfieViewModel'i hem host app'in kendi
    // CustomSelfieViewModel'i birlikte nasıl kullanılır, orada gösteriliyor.
    @Composable
    override fun SelfieScreen(onNext: () -> Unit, onBack: () -> Unit) {
        CustomSelfieScreen(onNext = onNext, onBack = onBack)
    }

    @Composable
    override fun PassportScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.PassportScreen(onNext, onBack)

    @Composable
    override fun NfcScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.NfcScreen(onNext, onBack)

    @Composable
    override fun AgentCallScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.AgentCallScreen(onNext, onBack)

    @Composable
    override fun AddressScreen(onNext: () -> Unit, onBack: () -> Unit, initialAddress: String?) =
        standard.AddressScreen(onNext, onBack, initialAddress)

    @Composable
    override fun LivenessScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.LivenessScreen(onNext, onBack)

    @Composable
    override fun SignatureScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.SignatureScreen(onNext, onBack)

    @Composable
    override fun SpeechScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.SpeechScreen(onNext, onBack)

    @Composable
    override fun OtherDocumentScreen(onNext: () -> Unit, onBack: () -> Unit) =
        standard.OtherDocumentScreen(onNext, onBack)

    @Composable
    override fun VideoRecordScreen(onNext: () -> Unit, onBack: () -> Unit) = standard.VideoRecordScreen(onNext, onBack)

    @Composable
    override fun ResultSuccessScreen(onFinish: () -> Unit) = standard.ResultSuccessScreen(onFinish)
}
