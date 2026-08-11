package com.shsw228.showdeck.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 発報時の音・読み上げ・振動。
 *
 * Echo Show としては声で知らせてくれることに価値があったので、音だけでなく
 * 読み上げも出す。TTS の初期化は数百 ms かかるため、起動時に一度だけ用意して
 * 使い回す。発報のたびに作ると最初の一言が欠ける。
 *
 * 鳴らすかどうかは 2 つで決まる。
 *
 *   - 設定（`DeckSettings.alertHapticOnly`）。うるさい音を聞きたくないとき
 *   - **端末のマナーモード。** `RINGER_MODE_SILENT` / `VIBRATE` を尊重する。
 *     端末側で消音にしているのに鳴るのは、単に壊れている
 *
 * どちらで黙るときも振動は出す。黙って何も起きないと、発報したことに
 * 気づけないまま時間が過ぎる。
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
     * @param hapticOnly 設定で音を切っているか。
     */
    fun fire(label: String, hapticOnly: Boolean = false) {
        // 振動は常に出す。音を出さない経路でも「鳴った」ことは伝える。
        vibrate()

        if (hapticOnly || isMuted()) {
            Log.i(TAG, "音は出さない（設定 or マナーモード）")
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
        runCatching { vibrator()?.cancel() }
    }

    /**
     * 端末が消音か。
     *
     * `RINGER_MODE_VIBRATE` も消音として扱う。振動だけにしている人に
     * アラーム音を浴びせる理由がない。
     */
    private fun isMuted(): Boolean {
        val audio = context.getSystemService(AudioManager::class.java) ?: return false
        return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            audio.getStreamVolume(AudioManager.STREAM_ALARM) == 0
    }

    /**
     * 振動。
     *
     * 短い→間→長いの 3 拍。単調な連続振動だと、机に置いた本体が唸るだけで
     * 何の通知か分からない。
     */
    private fun vibrate() {
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            vibrator.vibrate(
                VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_AMPLITUDES, -1),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "振動できなかった", it) }
    }

    private fun vibrator(): Vibrator? = context.getSystemService(Vibrator::class.java)

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "ShowDeck/Alert"
        const val UTTERANCE_ID = "showdeck-alert"

        /** 待ち・短・間・長。0 から始めるのが `createWaveform` の約束。 */
        val VIBRATION_PATTERN = longArrayOf(0, 120, 90, 320)
        val VIBRATION_AMPLITUDES = intArrayOf(0, 180, 0, 255)
    }
}
