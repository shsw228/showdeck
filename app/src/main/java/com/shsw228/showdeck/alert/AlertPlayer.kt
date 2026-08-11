package com.shsw228.showdeck.alert

import android.content.Context
import android.media.AudioAttributes
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
                tts?.language = Locale.JAPAN
            } else {
                Log.i(TAG, "TTS を初期化できなかった。音だけで通知する")
            }
        }
    }

    /**
     * アラーム音を鳴らして読み上げる。
     *
     * 音は端末の既定アラーム音。自前の音源を持つとリポジトリに音声ファイルを
     * 抱えることになり、この端末では割に合わない。
     */
    fun fire(label: String) {
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
                tts?.speak("$label の時間です", TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID)
            }.onFailure { Log.w(TAG, "読み上げに失敗", it) }
        }
    }

    fun stop() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching { tts?.stop() }
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
