package com.shsw228.showdeck.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 発報時の音と読み上げ。
 *
 * Echo Show としては声で知らせてくれることに価値があったので、音だけでなく
 * 読み上げも出す。TTS の初期化は数百 ms かかるため、起動時に一度だけ用意して
 * 使い回す。発報のたびに作ると最初の一言が欠ける。
 *
 * 振動は持たない。**この端末に振動子が無い**（`cmd vibrator vibrate` を
 * 直接投げても `mIsVibrating=false` のまま、`mSupportedEffects=null`）。
 *
 * 黙るのは 2 つの場合。どちらでも [com.shsw228.showdeck.ui.AlertOverlay] が
 * 全画面で出るので、発報したこと自体は伝わる。
 *
 *   - 設定（`DeckSettings.alertSilent`）
 *   - **端末のマナーモード。** 端末側で消音にしているのに鳴るのは壊れている
 */
class AlertPlayer(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ringtone: Ringtone? = null

    fun prepare() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                // 読み上げる文言も予定名も英語なので、音声も英語に合わせる。
                // 日本語の音声で英語を読ませると、まず聞き取れない。
                tts?.language = Locale.US
            } else {
                Log.i(TAG, "TTS を初期化できなかった。音だけで通知する")
            }
        }
    }

    /**
     * 発報する。
     *
     * 音は端末の既定アラーム音。自前の音源を持つとリポジトリに音声ファイルを
     * 抱えることになり、この端末では割に合わない。
     *
     * @param silent 設定で音を切っているか。画面表示は呼び出し側が出す。
     */
    fun fire(label: String, silent: Boolean = false) {
        if (silent || isMuted()) {
            Log.i(TAG, "無音で発報（設定 or マナーモード）。画面表示だけ")
            return
        }

        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
        }.onFailure { Log.w(TAG, "アラーム音を鳴らせなかった", it) }

        if (ttsReady) {
            runCatching {
                tts?.speak("$label is up", TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            }.onFailure { Log.w(TAG, "読み上げに失敗", it) }
        }
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { tts?.stop() }
    }

    /**
     * 端末が消音か。
     *
     * `RINGER_MODE_VIBRATE` も消音として扱う。振動に寄せている人に
     * アラーム音を浴びせる理由がない（この端末は振動しないので実質無音）。
     */
    private fun isMuted(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }


    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "ShowDeck/Alert"
        const val UTTERANCE_ID = "showdeck-alert"
    }
}
