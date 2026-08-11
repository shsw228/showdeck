package com.shsw228.showdeck.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.shsw228.showdeck.admin.DeviceAdmin

/**
 * 端末側の常駐設定を「アプリが自分で」適用する層。
 *
 * プラットフォーム署名（sharedUserId=android.uid.system）が効いていれば、
 * 必要な権限はすべて署名だけで通るため adb は一切要らない。
 * 署名が無い環境向けに、adb で以下 2 行を流すブートストラップも残してある。
 *   adb shell pm grant com.shsw228.showdeck android.permission.WRITE_SECURE_SETTINGS
 *   adb shell dpm set-device-owner com.shsw228.showdeck/.admin.AdminReceiver
 *
 * 起動のたびに適用し直すので、OTA や設定リセットで飛んでも自動で復帰する。
 */
object DeviceSetup {

    private const val TAG = "ShowDeck/Setup"

    /**
     * いま何ができるか。設定画面と診断オーバーレイはこれを見て表示を変える。
     * 「なぜこの機能が効かないのか」を端末上で説明できることが重要。
     */
    data class Capabilities(
        val isSystemUid: Boolean,
        val canWriteSecureSettings: Boolean,
        val canWriteSystemSettings: Boolean,
        val isDeviceOwner: Boolean,
        val canWriteBacklight: Boolean,
        val hasRoot: Boolean,
    )

    fun capabilities(context: Context) = Capabilities(
        isSystemUid = isRunningAsSystem(),
        canWriteSecureSettings = context.checkSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED,
        canWriteSystemSettings = Settings.System.canWrite(context),
        isDeviceOwner = DeviceAdmin.isDeviceOwner(context),
        canWriteBacklight = Backlight.canWriteDirectly || Su.isAvailable,
        hasRoot = Su.isAvailable,
    )

    /**
     * 効かせられる設定を効かせる。権限が無いものは黙って飛ばす。
     *
     * ここで失敗しても起動は必ず通す。据え置き機で「設定できないので起動しません」は
     * 一番やってはいけない振る舞い。
     */
    fun apply(context: Context) {
        val caps = capabilities(context)
        Log.i(TAG, "capabilities=$caps backlight=${Backlight.read()}/${Backlight.max}")

        if (caps.canWriteSecureSettings) {
            // 7 = AC / USB / ワイヤレスのいずれかで給電中はスリープしない
            putGlobal(context, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7)
            // hidden_api_policy は緩めない。PowerManager#goToSleep を使う前提で
            // 一度入れたが、消灯はバックライト 0 方式にしたのでリフレクションは
            // 一切使っていない。端末上の全アプリの制限まで緩む設定を、
            // 使っていない機能のために残さない。
            // 没入モードに入るたび SystemUI が「全画面表示」のダイアログを被せてくる。
            // 常時表示の端末では邪魔でしかないので既読扱いにする。
            putSecure(context, "immersive_mode_confirmations", "confirmed")
        }

        if (caps.canWriteSystemSettings) {
            putSystem(context, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE)
            // 自動調光は自前でやるので切る。OS 側と取り合いになると画面がちらつく。
            putSystem(
                context,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
        }

        if (caps.isDeviceOwner) {
            DeviceAdmin.disableStatusBar(context)
            DeviceAdmin.pinAsHomeLauncher(context)
        }
    }

    private fun putGlobal(context: Context, key: String, value: Int) {
        runCatching { Settings.Global.putInt(context.contentResolver, key, value) }
            .onFailure { Log.w(TAG, "global/$key の書き込みに失敗", it) }
    }

    private fun putSecure(context: Context, key: String, value: String) {
        runCatching { Settings.Secure.putString(context.contentResolver, key, value) }
            .onFailure { Log.w(TAG, "secure/$key の書き込みに失敗", it) }
    }

    private fun putSystem(context: Context, key: String, value: Int) {
        runCatching { Settings.System.putInt(context.contentResolver, key, value) }
            .onFailure { Log.w(TAG, "system/$key の書き込みに失敗", it) }
    }
}
