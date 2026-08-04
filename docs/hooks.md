# SDK Navigasyon Hook Kılavuzu

SDK navigasyon hook sistemi, doğrulama akışındaki herhangi bir yerleşik modülün öncesine veya sonrasına kendi Composable ekranlarınızı enjekte etmenizi sağlar — SDK iç işlerini değiştirmenize gerek kalmaz.

---

## İçindekiler

- [Genel Bakış](#genel-bakış)
- [Kullanılabilir Hook Noktaları](#kullanılabilir-hook-noktaları)
- [API Referansı](#api-referansı)
  - [setBeforeHook](#setbeforehook)
  - [setAfterHook](#setafterhook)
  - [onIdentifyFinished](#onidentifyfinished)
  - [onIdentifyFailed](#onidentifyfailed)
  - [onIdentifyCancelled](#onidentifycancelled)
  - [provideErrorMessage](#provideerrormessage)
  - [provideGuidanceMessage](#provideguidancemessage)
- [Kurulum](#kurulum)
- [Örnekler](#örnekler)
  - [Selfie öncesi kullanım koşulları](#selfie-öncesi-kullanım-koşulları)
  - [Liveness sonrası analytics eventi](#liveness-sonrası-analytics-eventi)
  - [Birden fazla hook](#birden-fazla-hook)
  - [Özel bitiş ekranı](#özel-bitiş-ekranı)
  - [Özel hata mesajları](#özel-hata-mesajları)
- [Çalışma Sırası](#çalışma-sırası)
- [Önemli Notlar](#önemli-notlar)

---

## Genel Bakış

`SdkHooks`, `IdentifySdk.init()` metoduna aktarılır ve hangi özel ekranların enjekte edileceğini, akış sonunda hangi callback'lerin çağrılacağını tanımlar.

```kotlin
val hooks = SdkHooks().apply {
    setBeforeHook(SdkRoutes.Selfie::class) { onNext ->
        OnayEkranim(onKabul = onNext)
    }
    onIdentifyFinished = { /* başka ekrana geç */ }
}

IdentifySdk.init(application, config, hooks)
```

Hook ekranınız tek bir parametre alır: `onNext: () -> Unit`. **Kullanıcı işlemini tamamladığında `onNext()` çağrılmalıdır** — akışın bir sonraki SDK ekranına geçmesini sağlayan budur.

---

## Kullanılabilir Hook Noktaları

Hook ekleyebileceğiniz tüm rotalar:

| Rota | Açıklama |
|---|---|
| `SdkRoutes.Intro` | Karşılama / tanıtım ekranı |
| `SdkRoutes.Prepare` | Doğrulama öncesi hazırlık ekranı |
| `SdkRoutes.DocumentSelection` | Belge türü seçim ekranı |
| `SdkRoutes.IdCard` | Kimlik kartı tarama (tek yüz) |
| `SdkRoutes.IdCardCombined` | Kimlik kartı tarama (ön + arka birleşik) |
| `SdkRoutes.Ovd` | OVD belge tarama |
| `SdkRoutes.Passport` | Pasaport tarama |
| `SdkRoutes.OtherDocument` | Diğer belge türleri tarama |
| `SdkRoutes.Nfc` | NFC çip okuma |
| `SdkRoutes.Selfie` | Selfie çekimi |
| `SdkRoutes.Liveness` | Canlılık tespiti |
| `SdkRoutes.AgentCall` | Operatörle canlı görüntülü görüşme |
| `SdkRoutes.VideoRecord` | Video kaydı |
| `SdkRoutes.Speech` | Ses tanıma |
| `SdkRoutes.Signature` | Dijital imza |
| `SdkRoutes.Address` | Adres doğrulama |


---

## API Referansı

### setBeforeHook

Belirtilen rotanın **hemen öncesine** bir Composable ekran enjekte eder. SDK ekranına geçebilmek için kullanıcının `onNext()` çağırması gerekir.

```kotlin
fun setBeforeHook(
    route: KClass<out SdkRoutes>,
    content: @Composable (onNext: () -> Unit) -> Unit
)
```

**Parametreler**

| Parametre | Tip | Açıklama |
|---|---|---|
| `route` | `KClass<out SdkRoutes>` | Önüne ekran enjekte edilecek SDK rotası |
| `content` | `@Composable (onNext: () -> Unit) -> Unit` | Kendi Composable ekranınız. İlerlemek için `onNext()` çağırın. |

---

### setAfterHook

Belirtilen rota tamamlandıktan **hemen sonrasına** bir Composable ekran enjekte eder. Bir sonraki modüle geçebilmek için kullanıcının `onNext()` çağırması gerekir.

```kotlin
fun setAfterHook(
    route: KClass<out SdkRoutes>,
    content: @Composable (onNext: () -> Unit) -> Unit
)
```

**Parametreler**

| Parametre | Tip | Açıklama |
|---|---|---|
| `route` | `KClass<out SdkRoutes>` | Sonrasına ekran enjekte edilecek SDK rotası |
| `content` | `@Composable (onNext: () -> Unit) -> Unit` | Kendi Composable ekranınız. İlerlemek için `onNext()` çağırın. |

---

### onIdentifyFinished

Tüm doğrulama akışı başarıyla tamamlandığında çağrılır. Bu değer atanmışsa SDK'nın varsayılan `ResultSuccess` ekranı **gösterilmez** — kullanıcıyı başka bir ekrana yönlendirmek sizin sorumluluğunuzdadır.

```kotlin
var onIdentifyFinished: (() -> Unit)? = null
```

`null` bırakılırsa SDK otomatik olarak kendi başarı ekranına geçer.

---

### onIdentifyFailed

Doğrulama akışı kurtarılamaz bir hatayla (örn. handshake sırasında ağ hatası, geçersiz kimlik ID'si) başarısız olduğunda çağrılır. Kullanıcıya gösterilecek hata mesajı string olarak iletilir.

```kotlin
var onIdentifyFailed: ((message: String) -> Unit)? = null
```

---

### onIdentifyCancelled

Akış ne `onIdentifyFinished` ne de `onIdentifyFailed` ile kesin bir sonuca ulaşmadan `IdentifyActivity` kapandığında çağrılır — kullanıcının geri tuşuyla/sistem kapatma ile akıştan çıkması **veya** host'un `IdentifySdk.close()` çağırması (bkz. [SdkConfig Referansı — Akışı Sonlandırma](sdk-config.md#akışı-sonlandırma-ve-lifecycle)). Her iki senaryo da aynı merkezi mekanizmadan geçtiği için ayrı ayrı sinyal yönetmenize gerek yoktur.

```kotlin
var onIdentifyCancelled: (() -> Unit)? = null
```

```kotlin
val hooks = SdkHooks().apply {
    onIdentifyCancelled = {
        // kullanıcı akıştan çıktı veya host IdentifySdk.close() çağırdı
        Toast.makeText(this@MainActivity, "Doğrulama iptal edildi", Toast.LENGTH_SHORT).show()
    }
}
```

Her yeni handshake başladığında (akış baştan başlatıldığında) bu sinyal otomatik sıfırlanır — önceki bir akıştan kalan durum yeni akışı etkilemez.

---

### provideErrorMessage

SDK'nın herhangi bir hata için göstereceği mesajı özelleştirmenizi sağlar. SDK varsayılanının yerine geçecek özel bir string döndürün; SDK varsayılanını kullanmak için `null` döndürün.

```kotlin
var provideErrorMessage: ((code: Int?, message: String?, throwable: Throwable?) -> String?)? = null
```

**Alınan parametreler**

| Parametre | Tip | Açıklama |
|---|---|---|
| `code` | `Int?` | Hata bir API çağrısından kaynaklanıyorsa HTTP durum kodu |
| `message` | `String?` | SDK'nın bu hata için varsayılan mesajı |
| `throwable` | `Throwable?` | Varsa altta yatan exception |

**Kapsam:** OVD ekranındaki (kimlik ön/OVD/arka yükleme) comparison uyarıları ve güvenlik öğesi (OVI) tespit edilemedi hatası da bu hook'tan geçer — `code=200`, `message` backend'in `messages` alanı veya SDK'nın varsayılan şablonu, `throwable=null`. OVI hatası özelinde SDK bu mesajı retry sayacına dahil etmez ve force-accept etmez; kullanıcı backend başarı dönene kadar adımı tekrar denemek zorundadır.

---

### provideGuidanceMessage

SDK akışındaki tüm ekranlarda (OCR, OVD, Selfie, Liveness, Pasaport, NFC, Hazırlık vb.) kullanıcıya gösterilen/seslendirilen yönlendirme mesajlarını (ör. "Biraz Yakınlaşın", "Kimliği yatay tutun") özelleştirmenizi sağlar. `null` dönerseniz SDK'nın varsayılan mesajı kullanılır. Mesajların tam listesi, tetiklenme koşulları ve örnek kullanım için bkz. [Yönlendirme Mesajları Rehberi](guidance-messages.md).

```kotlin
var provideGuidanceMessage: ((key: GuidanceMessageKey, defaultMessage: String) -> String?)? = null
```

---

## Kurulum

Yapılandırılmış `SdkHooks` örneğinizi `IdentifySdk.init()` metoduna aktarın:

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = SdkConfig.Builder(BASE_URL, TURN_KEY)
            .setUiProvider(StandardUiProvider())
            .build()

        val hooks = SdkHooks().apply {
            // hook'larınızı buraya ekleyin
        }

        IdentifySdk.init(
            application = application,
            config = config,
            hooks = hooks
        )

        IdentifySdk.startAuthentication(this, identificationId)
    }
}
```

---

## Örnekler

### Selfie öncesi kullanım koşulları

Kullanıcı selfie çekmeden önce bir onay ekranı gösterin. Selfie modülü yalnızca kullanıcı "Kabul Ediyorum"a bastıktan sonra başlar.

```kotlin
val hooks = SdkHooks().apply {
    setBeforeHook(SdkRoutes.Selfie::class) { onNext ->
        KullanimKosullariEkrani(
            onKabul = { onNext() },
            onReddet = { /* reddetme durumunu yönetin, örn. finish() */ }
        )
    }
}
```

```kotlin
@Composable
fun KullanimKosullariEkrani(onKabul: () -> Unit, onReddet: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Devam ederek Kullanım Koşullarımızı kabul etmiş olursunuz...")

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onReddet) { Text("Reddet") }
            Button(onClick = onKabul) { Text("Kabul Ediyorum") }
        }
    }
}
```

---

### Liveness sonrası analytics eventi

Canlılık kontrolü tamamlandığında bir analytics eventi gönderin ve akışa devam edin. Event gönderilirken kısa bir yükleme ekranı gösterilir.

```kotlin
val hooks = SdkHooks().apply {
    setAfterHook(SdkRoutes.Liveness::class) { onNext ->
        LaunchedEffect(Unit) {
            analytics.track("liveness_tamamlandi")
            onNext()
        }
        // Event gönderilirken kısa bir geçiş ekranı gösterilebilir
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}
```

---

### Birden fazla hook

Farklı rotalar için hook'lar serbestçe birleştirilebilir:

```kotlin
val hooks = SdkHooks().apply {

    // NFC okuma öncesi talimat ekranı
    setBeforeHook(SdkRoutes.Nfc::class) { onNext ->
        NfcTalimatEkrani(onDevam = onNext)
    }

    // Operatör görüşmesi öncesi KVKK onayı
    setBeforeHook(SdkRoutes.AgentCall::class) { onNext ->
        KvkkOnayEkrani(onKabul = onNext)
    }

    // Belge taraması sonrası "Neredeyse bitti" ekranı
    setAfterHook(SdkRoutes.IdCardCombined::class) { onNext ->
        NeredeysebittiEkrani(onDevam = onNext)
    }

    onIdentifyFinished = {
        startActivity(Intent(this@MainActivity, BasariEkraniActivity::class.java))
        finish()
    }
}
```

---

### Özel bitiş ekranı

SDK'nın yerleşik başarı ekranını kendi ekranınızla değiştirin:

```kotlin
val hooks = SdkHooks().apply {
    onIdentifyFinished = {
        // SDK'nın ResultSuccess ekranı atlanır.
        // Kendi ekranınıza yönlendirin.
        navController.navigate(BasariEkranim)
    }
}
```

> `onIdentifyFinished` atanmışsa **SDK Activity'sini sonlandırmak sizin sorumluluğunuzdadır** (`activity.finish()` veya back stack temizleme). SDK bunu otomatik yapmaz.

---

### Özel hata mesajları

SDK hata mesajlarını kendi metinleriniz veya yerelleştirilmiş string'lerle geçersiz kılın:

```kotlin
val hooks = SdkHooks().apply {
    provideErrorMessage = { code, message, throwable ->
        when (code) {
            401 -> getString(R.string.hata_oturum_suresi_doldu)
            503 -> getString(R.string.hata_servis_kullanilamiyor)
            else -> null  // SDK varsayılanını kullanmak için null döndürün
        }
    }

    onIdentifyFailed = { hataMesaji ->
        Toast.makeText(this, hataMesaji, Toast.LENGTH_LONG).show()
        finish()
    }
}
```

---

## Çalışma Sırası

Hem before hem de after hook'u olan bir rota için akış:

```
[Before Hook Ekranı]
       ↓  onNext() çağrıldı
[SDK Modül Ekranı]   ← yerleşik SDK ekranı
       ↓  modül tamamlandı
[After Hook Ekranı]
       ↓  onNext() çağrıldı
[Sonraki SDK Modülü]
```

Aynı rotanın farklı konumları (before/after) için hook'lar birbirinden bağımsızdır — birini, ikisini veya hiçbirini ekleyebilirsiniz.

---

## Önemli Notlar

**`onNext()` her zaman çağrılmalıdır.**
Hook ekranınız akışı ilerletmekten sorumludur. `onNext()` hiç çağrılmazsa kullanıcı ekranınızda mahsur kalır. Hata ve reddetme dahil tüm kod yollarının ya `onNext()` çağırdığından ya da SDK Activity'sini açıkça kapattığından emin olun.

**Hook'lar Composable'dır — standart Compose kuralları geçerlidir.**
Hook içinde `LaunchedEffect`, `remember`, `viewModel()`, navigasyon vb. kullanabilirsiniz. Hook içeriği SDK'nın `NavHost`'u içinde render edilir.

**Geri navigasyon hook içinde yönetilmez.**
SDK, hook ekranı içinde geri tuşunu engellemez. Kullanıcı hook ekranından geri basarsa Compose Navigation bunu yönetir. Doğrusal bir akış istiyorsanız geri tuşunu devre dışı bırakmayı veya geri basıldığında `onNext()` çağırmayı düşünebilirsiniz.

**Hook ekranları engellememeli.**
Composable body'de uzun süreli işlemlerden kaçının. Coroutine ve tek seferlik işler için `LaunchedEffect` kullanın.

**Her rota + konum için tek hook.**
Aynı rota için `setBeforeHook` iki kez çağrılırsa ilk kayıt üzerine yazılır. Aynı konumda birden fazla hook zincirleme desteklenmez.

**Hook'lar `IdentifySdk.init()` çağrıları arasında saklanmaz.**
`init()` tekrar çağrılırsa (örn. bir hatadan sonra) hook'larınızı yeniden tanımlamanız gerekir.
