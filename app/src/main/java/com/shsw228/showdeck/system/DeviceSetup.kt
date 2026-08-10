package com.shsw228.showdeck.system

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.shsw228.showdeck.admin.DeviceAdmin

/**
 * 端末側の常駐設定を「アプリが自分で」適用する層。
 *
 * adb が必要なのは初回の 2 行だけ:
 *   adb shell pm grant com.shsw228.showdeck android.permission.WRITE_SECURE_SETTINGS
 *   adb shell dpm set-device-owner com.shsw228.showdeck/.admin.AdminReceiver
 *
 * 権限さえ付いてしまえば Settings.Global / Settings.Secure はアプリから書ける。
 * 起動のたびに適用し直すので、OTA や設定リセットで飛んでも自動で復帰する。
 * この点で adb 一括セットアップより堅い。
 */
object DeviceSetup {

    private const val TAG = "ShowDeck/Setup"

    /**
     * いま何ができるか。設定画面と診断オーバーレイはこれを見て表示を変える。
     * 「なぜこの機能がグレーなのか」を端末上で説明できることが重要。
     */
    data class Capabilities(
        val canWriteSecureSettings: Boolean,
        val canWriteSystemSettings: Boolean,
        val isDeviceOwner: Boolean,
        val hasRoot: Boolean,
    ) {
        /** ウィンドウ輝度は無条件で使えるので、最低限の夜間モードは常に成立する。 */
        val canDimBelowSystemMinimum: Boolean get() = hasRoot
    }

    fun capabilities(context: Context) = Capabilities(
        canWriteSecureSettings = context.checkSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED,
        canWriteSystemSettings = Settings.System.canWrite(context),
        isDeviceOwner = DeviceAdmin.isDeviceOwner(context),
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
        Log.i(TAG, "capabilities=$caps")

        if (caps.canWriteSecureSettings) {
            // 7 = AC / USB / ワイヤレスのいずれかで給電中はスリープしない
            putGlobal(context, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, 7)
            // 内部 API を呼ぶために隠し API の検出を警告のみに落とす
            putGlobal(context, "hidden_api_policy", 1)
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

    private fun putSystem(context: Context, key: String, value: Int) {
        runCatching { Settings.System.putInt(context.contentResolver, key, value) }
            .onFailure { Log.w(TAG, "system/$key の書き込みに失敗", it) }
    }
}
