package com.shsw228.showdeck.system

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API キーのような秘密を端末内で暗号化して持つ。
 *
 * 方針:
 *   - **リポジトリにも APK にも入れない。** ビルド成果物に埋めると、配布しなくても
 *     端末から取り出せるうえ、鍵を変えるたびに再ビルドが要る
 *   - 入力は Web 設定画面から。保存時にここで暗号化し、DataStore には暗号文だけ置く
 *   - 鍵は Android Keystore の中で作られ、外に出ない（TEE があればその中に留まる）
 *
 * 平文の API キーは通信時にどのみち復号する必要があるので、root を取れる相手からは
 * 守れない。ここで防いでいるのは「ディスク上に平文で転がっていること」と
 * 「うっかりログや git に載ること」で、実害が出るのは主にそちら。
 */
object Secrets {

    private const val TAG = "ShowDeck/Secrets"
    private const val ALIAS = "showdeck.secrets"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_BITS = 128

    /**
     * 端末の画面から読んで打ち込むためのパスワードを作る。
     *
     * 紛らわしい文字（l/1/o/0 など）を除き、`xxxx-xxxx-xxxx` の形に区切る。
     * 5.5 インチの画面から桁を見失わずに読み取れることを優先している。
     */
    fun generatePassword(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = java.security.SecureRandom()
        return (1..12)
            .map { alphabet[random.nextInt(alphabet.length)] }
            .joinToString("")
            .chunked(4)
            .joinToString("-")
    }

    /** 平文を暗号化して base64 にする。失敗したら空文字を返し、保存を諦める。 */
    fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.doFinal(plain.toByteArray())
            // IV は毎回変わるので暗号文の先頭にくっつけて保存する。
            Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
        }.onFailure { Log.w(TAG, "暗号化に失敗", it) }.getOrDefault("")
    }

    /** 復号する。鍵が作り直された場合などは復号できないので空文字を返す。 */
    fun decrypt(stored: String): String {
        if (stored.isBlank()) return ""
        return runCatching {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, raw, 0, IV_LENGTH))
            }
            String(cipher.doFinal(raw, IV_LENGTH, raw.size - IV_LENGTH))
        }.onFailure { Log.w(TAG, "復号に失敗。設定し直しが必要", it) }.getOrDefault("")
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 画面ロックは無効にしてある端末なので、認証要求は付けない。
                // 付けると常駐アプリが自分で復号できなくなる。
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }
}

/**
 * API キーを表す型。
 *
 * `toString()` を潰してあるのが要点。`DeckSettings` はまるごとログに出しており、
 * 素の `String` で持つと logcat に平文が載り、それが Web の `/logs` から読めてしまう。
 * 型で塞いでおけば、うっかり別の場所で出力しても漏れない。
 */
@JvmInline
value class ApiKey(val value: String) {
    val isSet: Boolean get() = value.isNotBlank()

    override fun toString(): String = if (isSet) "****" else "未設定"
}
