# Yönlendirme Mesajları Rehberi (Guidance Messages)

SDK akışındaki hemen her ekran, kullanıcıyı hem **ekranda yazıyla** hem **sesli olarak (TTS)** yönlendirir — "Kimliği biraz yaklaştırın", "Yüzünüzü çerçeveye alın", "Lütfen kartı NFC alanına yaklaştırın" gibi. Bu doküman, bu mesajların **tamamının nasıl özelleştirileceğini** ve **sesin nasıl tamamen kapatılacağını** anlatır.

> Bu doküman tüm modülleri kapsar. Yalnızca OCR/OVD tarama ekranlarına özel detaylar (`onSpeak` ile ses çalma mekanizmasını override etme gibi) için bkz. [OCR Entegrasyonu — Sesli Yönlendirme Mesajları](ocr-integration.md#sesli-yönlendirme-mesajları).

---

## İçindekiler

- [Nasıl Çalışır](#nasıl-çalışır)
- [Mesajları Özelleştirme (`provideGuidanceMessage`)](#mesajları-özelleştirme-provideguidancemessage)
- [Sesi Tamamen Kapatma (`setTtsEnabled`)](#sesi-tamamen-kapatma-settsenabled)
- [Tüm Mesajlar — Modül Modül Referans](#tüm-mesajlar--modül-modül-referans)
  - [OCR (Kimlik Kartı Tarama)](#ocr-kimlik-kartı-tarama)
  - [OVD (Hologram Doğrulama)](#ovd-hologram-doğrulama)
  - [Intro](#intro)
  - [Hazırlık](#hazırlık)
  - [Belge Seçimi](#belge-seçimi)
  - [Selfie + Liveness (Yüz Rehberi)](#selfie--liveness-yüz-rehberi)
  - [Liveness Adımları](#liveness-adımları)
  - [Pasaport](#pasaport)
  - [Diğer Belge](#diğer-belge)
  - [NFC](#nfc)
  - [Temsilci Görüşmesi](#temsilci-görüşmesi)
  - [Adres](#adres)
  - [İmza](#i̇mza)
  - [Konuşma Testi](#konuşma-testi)
  - [Video Kayıt](#video-kayıt)
  - [Sonuç](#sonuç)
- [Dil Desteği](#dil-desteği)
- [Sıkça Sorulan Sorular](#sıkça-sorulan-sorular)

---

## Nasıl Çalışır

Her yönlendirme mesajının sabit bir kimliği vardır: `GuidanceMessageKey` enum'u (`com.identify.sdk.core.model.GuidanceMessageKey`). SDK bir mesaj göstermek/seslendirmek istediğinde şu sırayı izler:

1. `SdkHooks.provideGuidanceMessage` atanmışsa çağrılır — `(key, defaultMessage) -> String?` imzasında.
2. Bu callback `null` dışında bir şey dönerse, **o metin** hem ekranda gösterilir hem TTS ile okunur.
3. Callback atanmamışsa veya `null` dönerse, SDK'nın aktif dile (`SdkConfig.setLanguage(...)`) göre çözdüğü **varsayılan metin** (`defaultMessage`) kullanılır.

Ekrandaki yazı ile seslendirilen metin **her zaman aynı kaynaktan** gelir — birini özelleştirip diğerini unutmak gibi bir senaryo yoktur.

```
provideGuidanceMessage(key, defaultMessage)
        │
        ├── null dönerse ──────────► defaultMessage kullanılır
        └── String dönerse ────────► o metin hem yazılır hem seslendirilir
```

---

## Mesajları Özelleştirme (`provideGuidanceMessage`)

```kotlin
import com.identify.sdk.core.model.GuidanceMessageKey
import com.identify.sdk.core.navigation.SdkHooks

val hooks = SdkHooks().apply {
    provideGuidanceMessage = { key, defaultMessage ->
        when (key) {
            GuidanceMessageKey.MOVE_CLOSER -> "Kimliği biraz daha kameraya yaklaştırır mısınız?"
            GuidanceMessageKey.FACE_HOLD_STILL -> "Harika, öylece kalın!"
            GuidanceMessageKey.NFC_READING -> "Kartı kaldırmayın, okuma sürüyor…"
            else -> null // diğer tüm mesajlar için SDK varsayılanı kullanılsın
        }
    }
}

IdentifySdk.init(application, config, hooks)
```

- `key`: hangi mesajın çözüldüğünü belirten `GuidanceMessageKey` değeri (aşağıdaki referans tablolarına bakın).
- `defaultMessage`: SDK'nın o an aktif dile göre çözdüğü varsayılan metin — kendi metninizi yazmak istemediğiniz key'ler için bunu olduğu gibi de dönebilirsiniz.
- `null` dönmek, "bu key için SDK varsayılanını kullan" anlamına gelir — `when` bloğunda her key'i tek tek ele almanıza gerek yoktur, yalnızca değiştirmek istediklerinizi yazıp geri kalanı için `else -> null` yeterlidir.

---

## Sesi Tamamen Kapatma (`setTtsEnabled`)

Tüm akışlardaki sesli yönlendirmeyi (TTS) tek bir config alanıyla kapatabilirsiniz. **Ekrandaki yazılı yönlendirme mesajları bundan etkilenmez** — yalnızca ses kapanır.

```kotlin
SdkConfig.Builder(baseUrl, turnKey)
    .setUiProvider(StandardUiProvider())
    .setTtsEnabled(false) // varsayılan: true
    .build()
```

Bkz. [SdkConfig Referansı — Ses](sdk-config.md#ses).

---

## Tüm Mesajlar — Modül Modül Referans

**Kapsam ilkesi:** yalnızca **pozitif/yönlendirici** mesajlar (kullanıcının o an ne yapması gerektiğini söyleyen) hem yazılır hem seslendirilir. Hata/başarısızlık mesajları (dialog'lar, kırmızı banner'lar, `error` alanları) her zaman **görsel kalır**, seslendirilmez — kullanıcı bir hata metnini dinlemek zorunda bırakılmaz, ekranda okur.

### OCR (Kimlik Kartı Tarama)

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `ID_NOT_DETECTED` | Kamerada belge bulunamadı | "Kimlik tespit edilemedi. Lütfen geçerli bir kimlik kartı kullanın." |
| `DOCUMENT_ROTATE_HORIZONTAL` | Belge dikey/yan konumda | "Kimliği yatay tutun" |
| `DOCUMENT_DETECTED_CHECKING` | Belge bulundu, içerik kontrol ediliyor | "Kimliği netleştirin ve çerçevede tutun" |
| `MOVE_CLOSER` | Belge kameradan çok uzak | "Biraz Yakınlaşın" |
| `MOVE_FURTHER` | Belge kameraya çok yakın | "Biraz Uzaklaştırın" |
| `KEEP_FLAT` | Belge fazla eğik | "Düz Tutun" |
| `FOCUS_AND_STEADY` | Görüntü bulanık | "Netleştirin ve Sabit Tutun" |
| `CAPTURE_SUCCESS` | Fotoğraf başarıyla çekildi | "BAŞARILI!" |
| `OCR_PREPARING` | Tarama başlarken | "Hazırlanıyor..." |
| `OCR_ALIGN_FRONT_SIDE` | Arka yüz beklenirken ön yüz gösterildi | "Belgenin Ön Yüzünü Hizalayın" |
| `OCR_ALIGN_BACK_SIDE` | Ön yüz beklenirken arka yüz gösterildi | "Belgenin Arka Yüzünü Hizalayın" |
| `OCR_ALIGN_ID_FIRST` | Manuel çekim denendi, tamponda kullanılabilir kare yok | "Lütfen önce kimliği çerçeveye hizalayın" |

### OVD (Hologram Doğrulama)

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `ID_NOT_DETECTED` / `MOVE_CLOSER` / `MOVE_FURTHER` / `DOCUMENT_DETECTED_CHECKING` / `CAPTURE_SUCCESS` | (yukarıdaki OCR tablosuyla ortak) | — |
| `OVD_WAIT_FOR_CAPTURE` | Belge bulundu (OVD dışındaki adımlarda), sabit tutulması isteniyor | "Harika, Öylece Bekleyin..." |
| `OVD_SCAN_FRONT_SIDE` | Akış başlıyor / FRONT adımına dönülüyor | "Lütfen kimliğinizin ön yüzünü taratın." |
| `OVD_SCAN_BACK_SIDE` | BACK adımına geçildi | "Şimdi lütfen kimliğinizin arka yüzünü taratın." |
| `OVD_FLASH_MOVE_CARD` | Hologram efekti için belgeyi hafifçe oynatma talimatı | "Gökkuşağı efektini yakalamak için kimliği hafifçe oynatın." |
| `OVD_TOO_WHITE_TILT` | Aşırı parlama/ışık yansıması algılandı | "Çok fazla parlama var, kimliği hafifçe eğin." |
| `OVD_GLARE_CAPTURED` | Hologram (gökkuşağı) efekti yakalandı | "Efekt yakalandı, bekleyin..." |
| `OVD_WRONG_SIDE_FRONT` | FRONT adımında yanlış taraf gösterildi | "Lütfen kimliğinizin ön yüzünü çevirin." |
| `OVD_WRONG_SIDE_BACK` | BACK adımında yanlış taraf gösterildi | "Lütfen kimliğinizin arka yüzünü çevirin." |
| `OVD_VERIFICATION_COMPLETED` | FRONT + OVD + BACK adımlarının üçü de tamamlandı | "Kimlik doğrulaması başarıyla tamamlandı." |

### Intro

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `INTRO_WELCOME` | Ekran açıldığında (bir kez) | "Lütfen kimliğinizi doğrulamak için yönergeleri takip edin." |

### Hazırlık

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `PREPARE_INTRO` | Ekran açıldığında (bir kez) | "Lütfen kimlik doğrulama sürecine başlamadan önce aşağıdaki izinleri verdiğinizden emin olun." |

### Belge Seçimi

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `DOC_SELECTION_INSTRUCTION` | Ekran açıldığında (bir kez) | "Lütfen kaydınızı nasıl doğrulamak istediğinizi seçin ve devam edin." |

### Selfie (Yüz Rehberi)

Bu mesajlar yalnızca **Selfie** ekranında kullanılır — yüzün kamera çerçevesine göre konumuna göre her karede yeniden değerlendirilir. **Liveness akışı artık bir çerçeve/oval hizalaması istemiyor**, bu yüzden bu mesajlar Liveness'ta hiç tetiklenmez; yüz bulunamadığında Liveness kendi mesajını (`LIVENESS_NO_FACE`, bkz. aşağı) kullanır.

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `FACE_NO_FACE` | Kamerada yüz bulunamadı (yalnızca Selfie) | "Yüzünüzü oval alana yerleştirin" |
| `FACE_MULTIPLE_FACES` | Birden fazla yüz tespit edildi | "Sadece bir kişi olmalı" |
| `FACE_MOVE_LEFT` | Yüz çerçeveye göre sağda, sola kaymalı | "Sola Kayın" |
| `FACE_MOVE_RIGHT` | Yüz çerçeveye göre solda, sağa kaymalı | "Sağa Kayın" |
| `FACE_MOVE_UP` | Yüz çerçeveye göre aşağıda, yukarı kaymalı | "Yukarı Kayın" |
| `FACE_MOVE_DOWN` | Yüz çerçeveye göre yukarıda, aşağı kaymalı | "Aşağı Kayın" |
| `FACE_MOVE_CLOSER` | Yüz kameraya çok uzak | "Biraz Yaklaşın" |
| `FACE_MOVE_BACK` | Yüz kameraya çok yakın | "Biraz Uzaklaşın" |
| `FACE_HOLD_STILL` | Yüz doğru konumda, sabit tutulmalı | "Sabit Kalın" |
| `FACE_OPEN_EYES` | Gözler kapalı tespit edildi | "Gözlerinizi Açın" |
| `FACE_CLOSE_MOUTH` | Ağız açık tespit edildi | "Ağzınızı Kapatın" |
| `FACE_WELL_POSITIONED` | Yüz çerçeve içinde ve stabil | "Harika, Sabit Kalın!" |

### Liveness Adımları

Liveness akışında konum/çerçeve bazlı bir rehberlik yoktur. Yüz kamerada bulunamadığında Selfie'nin oval'a atıf yapan mesajı değil, konumdan bağımsız `LIVENESS_NO_FACE` seslendirilir; yüz algılandığı anda ise doğrudan aktif adımın talimatı seslendirilir — kullanıcı önce bir oval alana hizalanmayı beklemez, bu da akışı hızlandırır.

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `LIVENESS_NO_FACE` | Kamerada yüz bulunamadı (yalnızca Liveness) | "Yüz algılanamadı" |
| `LIVENESS_TURN_RIGHT` | Aktif adım: başı sağa çevirme | "Başınızı Sağa Çevirin" |
| `LIVENESS_TURN_LEFT` | Aktif adım: başı sola çevirme | "Başınızı Sola Çevirin" |
| `LIVENESS_SMILE` | Aktif adım: gülümseme | "Gülümseyin" |
| `LIVENESS_BLINK` | Aktif adım: göz kırpma | "Gözlerinizi Kırpın" |

### Pasaport

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `PASSPORT_ALIGN_TO_FRAME` | Tarama başlarken / yeniden denemede | "Pasaportu çerçeveye hizalayın." |

### Diğer Belge

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `OTHER_DOC_ALIGN_FRONT_SIDE` | Ön yüz çekimi bekleniyor | "Belgenin Ön Yüzünü Hizalayın" |
| `OTHER_DOC_ALIGN_BACK_SIDE` | Ön yüz çekildi, arka yüz çekimi bekleniyor | "Belgenin Arka Yüzünü Hizalayın" |

### NFC

Yalnızca aşağıdaki **pozitif/yönlendirici** durumlar seslendirilir. `WRONG_DATA`, `READ_ERROR`, `API_ERROR`, `MAX_RETRIES_REACHED`, `NFC_DISABLED` gibi hata durumları kapsam dışıdır — her zaman görsel kalır.

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `NFC_WAITING_FOR_TAG` | Kart bekleniyor | "Kartınızı telefonun arkasına yaklaştırın." |
| `NFC_READING` | Kart algılandı, çip okunuyor | "Kart algılandı, çip okunuyor. Lütfen kartı kaldırmayın." |
| `NFC_VERIFYING` | Okunan veri backend'de doğrulanıyor | "Doğrulanıyor…" |
| `NFC_SUCCESS` | Okuma ve doğrulama başarılı | "Kimlik bilgileri başarıyla okundu." |
| `NFC_NEED_MANUAL` | Gerekli MRZ verisi yok, manuel giriş isteniyor | "Lütfen kimlik bilgilerinizi manuel olarak girin." |

### Temsilci Görüşmesi

Yalnızca aşağıdaki pozitif durumlar seslendirilir; bağlantı kopması (`ConnectionLost`) ve cevapsız çağrı (`MissedCall`) her zaman görsel kalır.

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `AGENT_CALL_WAITING` | Ekran açıldığında, sırada bekleniyor | "Uzman temsilcimize bağlanıyorsunuz, lütfen bekleyin." |
| `AGENT_CALL_INCOMING` | Temsilci arıyor, kullanıcının kabul etmesi bekleniyor | "Temsilcimiz sizi arıyor." |

### Adres

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `ADDRESS_INSTRUCTION` | Ekran açıldığında (bir kez) | "İkametgahınızı gösteren bir belge fotoğrafı yükleyin." |

### İmza

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `SIGNATURE_INSTRUCTION` | Ekran açıldığında (bir kez) | "Aşağıdaki alana parmağınızla imzanızı atın." |

### Konuşma Testi

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `SPEECH_INSTRUCTION` | Ekran açıldığında (bir kez) | "Lütfen mikrofona basılı tutarak aşağıdaki kelimeyi okuyun" |
| `SPEECH_READ_WORD` | Okunacak kelime backend'den geldiğinde/değiştiğinde | "Lütfen şu kelimeyi okuyun: {kelime}" (`%1$s` yer tutucusu kelimeyle doldurulur) |

### Video Kayıt

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `VIDEO_READ_INSTRUCTION` | Ekran hazır durumda, okunacak cümle modu (`readText` dolu) | "Lütfen aşağıdaki metni okurken bir video kaydedin." |
| `VIDEO_FREE_INSTRUCTION` | Ekran hazır durumda, serbest kayıt modu (`readText` boş) | "En fazla N saniye uzunluğunda olacak bir video çekerek devam edin." (`%1$d` yer tutucusu saniye ile doldurulur) |

### Sonuç

| `GuidanceMessageKey` | Tetiklenme Koşulu | Varsayılan (TR) metin |
|---|---|---|
| `RESULT_SUCCESS` | Doğrulama başarıyla tamamlandığında | "İşleminiz Tamamlandı" |

---

## Dil Desteği

Yukarıdaki tablolardaki metinler `values/strings.xml` (TR, varsayılan) içindir. `values-en`, `values-de`, `values-az` altında İngilizce/Almanca/Azerice karşılıkları bulunur ve `SdkConfig.setLanguage(...)` ile seçilen dile göre `defaultMessage` parametresi otomatik o dilde gelir — `provideGuidanceMessage` içinde ayrıca dil kontrolü yapmanıza gerek yoktur, isterseniz `defaultMessage`'ı olduğu gibi kullanabilirsiniz.

---

## Sıkça Sorulan Sorular

**S: Bir mesajı sadece belirli bir dilde değiştirmek istiyorum, diğer dillerde SDK varsayılanı kalsın.**
A: `SdkConfig.language`'i okuyup `when` içinde dil bazlı dallanma yapabilirsiniz:

```kotlin
provideGuidanceMessage = { key, defaultMessage ->
    if (key == GuidanceMessageKey.MOVE_CLOSER && config.language == SdkLanguage.TR) {
        "Azıcık daha yaklaşır mısınız? 🙂"
    } else null
}
```

**S: Ekranda metni değiştirdim ama sesin hâlâ eski/varsayılan metni okuduğunu düşünüyorum, doğru mu çalışıyor?**
A: Hayır, ikisi aynı kaynaktan besleniyor — ek bir senkronizasyon adımı yok. Eğer farklı davranış görüyorsanız, `provideGuidanceMessage`'ın `else` dalında yanlışlıkla `defaultMessage` yerine `null` dönmediğinizden emin olun (`null`, "SDK varsayılanını kullan" anlamına gelir, `defaultMessage`'ı dönmek de aynı sonucu verir).

**S: Sadece OCR ekranındaki sesin nasıl *çalınacağını* (ör. kendi ses dosyalarım) değiştirebilir miyim?**
A: Evet — bu, `provideGuidanceMessage`'dan farklı bir mekanizmadır. Bkz. [OCR Entegrasyonu — Özel Ses / Kendi TTS Engine'in](ocr-integration.md#özel-ses--kendi-tts-enginein).
