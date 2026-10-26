package com.identify.sample

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaPlayer
import com.identify.sdk.IdentifySdk
import com.identify.sdk.core.model.GuidanceMessageKey
import com.identify.sdk.core.model.SdkError
import com.identify.sdk.core.model.SdkErrorKey
import com.identify.sdk.core.model.SdkLanguage
import com.identify.sdk.core.model.SdkScreen
import com.identify.sdk.core.navigation.SdkHooks
import com.identify.sdk.speech.SdkSpeechConfig
import com.identify.sdk.speech.SdkSpeechEngine
import com.identify.sdk.speech.SpeechRequest
import timber.log.Timber

/*
 * Hata mesajı ve TTS (metin + ses) özelleştirme örnekleri.
 *
 * Bu dosyadaki her şey SDK'nın public API'sini kullanır ve MainViewModel'de iki toggle'ın
 * arkasında devreye girer ("Özel hata mesajları", "Özel TTS"). Toggle'lar kapalıyken SDK
 * varsayılan davranışıyla çalışır.
 *
 * Kural: her override yalnızca birkaç anahtarı değiştirir, gerisi için `null` döner. `null`
 * dönen her anahtarda SDK kendi varsayılan metnini kullanır.
 */

// ---------------------------------------------------------------------------------------------
// 1. Hata mesajlarını anahtara göre değiştirme — SdkHooks.provideErrorMessageByKey
// ---------------------------------------------------------------------------------------------

/**
 * SDK, bir hatayı ekranda göstermeden ÖNCE [SdkHooks.provideErrorMessageByKey]'i çağırır.
 * Metin döndürürseniz o gösterilir; `null` döndürürseniz SDK'nın varsayılan metni kalır.
 *
 * [SdkError.key] hatanın türünü, [SdkError.screen] hangi ekrandan geldiğini söyler. Aynı anahtar
 * birden fazla ekranda kullanılabilir (ör. COMPARISON_FAILED kimlik, selfie ve canlılıkta).
 */
fun SdkHooks.installCustomErrorMessages(language: SdkLanguage) {
    provideErrorMessageByKey = { error: SdkError ->
        when (error.key) {
            SdkErrorKey.CONNECTION_ERROR -> language.pick(
                tr = "İnternet bağlantınızı kontrol edip tekrar deneyinnn.",
                en = "Please check your internet connection and try again.",
            )

            SdkErrorKey.CALL_CONNECTION_LOST -> language.pick(
                tr = "Görüşme bağlantısı koptu. Birazdan yeniden deneyeceğiz.",
                en = "The call connection dropped. We'll retry shortly.",
            )

            // Ekrana göre dallanma: yüz doğrulamada özel metin, belge karşılaştırmasında SDK metni.
            SdkErrorKey.COMPARISON_FAILED -> when (error.screen) {
                SdkScreen.LIVENESS, SdkScreen.SELFIE, SdkScreen.SELFIE_WITH_LIVENESS -> language.pick(
                    tr = "Yüzünüzü doğrulayamadık. Lütfen tekrar deneyin.",
                    en = "We couldn't verify your face. Please try again.",
                )
                else -> null
            }

            SdkErrorKey.DOCUMENT_NOT_DETECTED -> language.pick(
                tr = "Kimlik kartınızı çerçeveye tam olarak yerleştirin.",
                en = "Place your ID card fully inside the frame.",
            )

            SdkErrorKey.NFC_WRONG_MRZ_DATA, SdkErrorKey.NFC_CONNECTION_LOST -> language.pick(
                tr = "Çip okunamadı. Kartı telefonun arkasına sabit tutun.",
                en = "Couldn't read the chip. Hold the card steady on the back of your phone.",
            )

            SdkErrorKey.NFC_MAX_RETRIES_REACHED -> language.pick(
                tr = "NFC deneme hakkınız doldu.",
                en = "You've used all your NFC attempts.",
            )

            // Diğer tüm hatalar: SDK'nın (ya da sunucunun) kendi metni gösterilir.
            else -> null
        }
    }
}

/**
 * Hatayı yalnızca GÖZLEMLEMEK için (analitik/log). Metni değiştirmez, akışı etkilemez.
 * Aynı olaylar `IdentifySdk.errors` (SharedFlow) üzerinden de dinlenebilir.
 *
 * [speakErrors] açıksa hata metni ayrıca seslendirilir — SDK hataları kendiliğinden seslendirmez.
 * [SdkError.message], override uygulandıktan sonraki nihai metindir.
 */
fun SdkHooks.installErrorListener(speakErrors: Boolean) {
    onError = { error ->
        Timber.d(
            "SampleErrors key=%s screen=%s code=%s message=%s",
            error.key, error.screen, error.code, error.message,
        )
        if (speakErrors) {
            IdentifySdk.speak(error.message, error.screen, true)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2a. TTS metnini değiştirme — SdkHooks.provideGuidanceMessage
// ---------------------------------------------------------------------------------------------

/**
 * SDK, bir yönlendirme mesajını göstermeden ve seslendirmeden ÖNCE
 * [SdkHooks.provideGuidanceMessage]'ı çağırır. Dönen metin hem ekranda yazılır hem okunur.
 * `defaultMessage` aktif dile göre çözülmüş SDK metnidir; `null` dönerseniz o kullanılır.
 *
 * Tüm anahtarlar için bkz. GuidanceMessageKey ve docs/guidance-messages.md.
 *
 * Bu hook'ta daha önce atanmış bir provideGuidanceMessage varsa (ör. Hook Demo'nun
 * "[DEMO]" öneki) override etmediğimiz anahtarlar ona devredilir — iki örnek birlikte çalışır.
 */
fun SdkHooks.installCustomGuidanceTexts(language: SdkLanguage) {
    val previous = provideGuidanceMessage
    provideGuidanceMessage = { key, defaultMessage ->
        val custom = when (key) {
            // Liveness adım talimatları
            GuidanceMessageKey.LIVENESS_TURN_RIGHT -> language.pick(
                tr = "Başını yavaşça sağa çevir",
                en = "Slowly turn your head right",
            )
            GuidanceMessageKey.LIVENESS_TURN_LEFT -> language.pick(
                tr = "Başını yavaşça sola çevir",
                en = "Slowly turn your head left",
            )
            GuidanceMessageKey.LIVENESS_SMILE -> language.pick(
                tr = "Şimdi gülümse",
                en = "Now smile",
            )

            // NFC
            GuidanceMessageKey.NFC_WAITING_FOR_TAG -> language.pick(
                tr = "Kimlik kartını telefonun arkasına yaklaştır",
                en = "Hold your ID card to the back of your phone",
            )

            // Belge tarama
            GuidanceMessageKey.MOVE_CLOSER -> language.pick(
                tr = "Biraz yaklaş",
                en = "Move a bit closer",
            )

            else -> null
        }
        custom ?: previous?.invoke(key, defaultMessage)
    }
}

// ---------------------------------------------------------------------------------------------
// 2b. TTS sesini değiştirme — SdkConfig.Builder.setSpeechConfig(SdkSpeechConfig(...))
// ---------------------------------------------------------------------------------------------

/**
 * Metnin NASIL okunacağını ayarlar: hız, ton, ses, motor, kapalı ekranlar ve isteğe bağlı
 * kendi ses motorunuz.
 */
fun sampleSpeechConfig(context: Context, language: SdkLanguage): SdkSpeechConfig =
    SdkSpeechConfig(
        // Kendi kayıtlarınızı çalmak için (bkz. SampleSpeechEngine). null = yalnızca SDK TTS'i.
        engine = SampleSpeechEngine(context.applicationContext),
        // Bu ekranlarda ses kapalı; ekrandaki yazı etkilenmez.
        disabledScreens = setOf(SdkScreen.INTRO),
        speechRate = 0.95f, // 1.0 normal; biraz yavaş okusun
        pitch = 1.0f,       // ses tonu, 1.0 normal
        // Cihazdaki bir sesin adı (android.speech.tts.Voice.getName). Seçili dile uygun bir ses
        // verin. Cihazda bu ad yoksa motorun varsayılan sesi kullanılır.
        voiceName = when (language) {
            SdkLanguage.TR -> "tr-tr-x-ama-local"
            SdkLanguage.EN -> "en-us-x-sfg-local"
            else -> null
        },
        // Belirli bir TTS motorunu zorlamak için; null = cihaz varsayılanı.
        ttsEnginePackage = "com.google.android.tts",
    )

// ---------------------------------------------------------------------------------------------
// 2c. (İleri) Kendi ses kayıtlarını çalmak — SdkSpeechEngine
// ---------------------------------------------------------------------------------------------

/**
 * `speak` true dönerse mesajı siz çaldınız, SDK'nın TTS'i konuşmaz. false dönerse (ya da istisna
 * fırlarsa) SDK kendi TTS'iyle okur.
 *
 * Ses dosyasını metne değil [SpeechRequest.key]'e bağlayın: metin dile ve override'a göre
 * değişir, anahtar değişmez.
 *
 * Bu sample'da res/raw altında kayıt yok; dosya bulunamayınca false dönüp SDK'ya bırakır.
 * Denemek için ör. `res/raw/liveness_smile_tr.mp3` ekleyin.
 */
class SampleSpeechEngine(private val context: Context) : SdkSpeechEngine {
    private var player: MediaPlayer? = null

    override fun speak(request: SpeechRequest): Boolean {
        val baseName = when (request.key) {
            GuidanceMessageKey.LIVENESS_SMILE -> "liveness_smile"
            else -> return false
        }
        val resId = rawResFor(baseName, request.language)
        if (resId == 0) return false

        stop()
        player = MediaPlayer.create(context, resId)?.apply {
            setOnCompletionListener { it.release(); if (player === it) player = null }
            start()
        }
        return player != null
    }

    override fun stop() {
        player?.release()
        player = null
    }

    // Örneği kısa tutmak için ad ile arama yapılıyor; üretimde sabit bir anahtar → R.raw.x
    // eşlemesi tercih edin.
    @SuppressLint("DiscouragedApi")
    private fun rawResFor(baseName: String, language: SdkLanguage): Int =
        context.resources.getIdentifier("${baseName}_${language.code}", "raw", context.packageName)
}

// ---------------------------------------------------------------------------------------------

/** Örneği okunur tutmak için küçük dil seçici. AZ/DE için metin verilmezse İngilizceye düşer. */
private fun SdkLanguage.pick(tr: String, en: String, az: String? = null, de: String? = null): String =
    when (this) {
        SdkLanguage.TR -> tr
        SdkLanguage.AZ -> az ?: en
        SdkLanguage.DE -> de ?: en
        else -> en
    }
