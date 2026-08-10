package com.shsw228.showdeck.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.shsw228.showdeck.MainActivity

/**
 * Device Owner として振る舞うためのレシーバ。
 *
 * `dpm set-device-owner` で有効化する（scripts/setup-device.sh 参照）。
 * Device Owner になるとステータスバーを SystemUI ごと止めずに無効化できるので、
 * `pm disable-user com.android.systemui` より穏当で戻しやすい。
 */
class AdminReceiver : DeviceAdminReceiver()

object DeviceAdmin {

    private const val TAG = "ShowDeck/Admin"

    fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        return dpm?.isDeviceOwnerApp(context.packageName) == true
    }

    /**
     * ステータスバーを無効化する。Device Owner でなければ黙って何もしない。
     *
     * セットアップの順序（アプリ先入れ → dpm 設定）に依存させたくないので、
     * 失敗を握りつぶして起動そのものは必ず通す。
     */
    fun disableStatusBar(context: Context) {
        if (!isDeviceOwner(context)) {
            Log.i(TAG, "Device Owner ではないためステータスバー無効化をスキップ")
            return
        }
        runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.setStatusBarDisabled(component(context), true)
        }.onFailure { Log.w(TAG, "ステータスバー無効化に失敗", it) }
    }

    /**
     * 自分自身を既定のホームアプリとして固定する。
     *
     * `cmd package set-home-activity` と違い、選択ダイアログが二度と出なくなる。
     * 他のランチャーを入れても奪われないので、据え置き機ではこちらが正解。
     */
    fun pinAsHomeLauncher(context: Context) {
        if (!isDeviceOwner(context)) return
        runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                component(context),
                homeFilter,
                ComponentName(context, MainActivity::class.java),
            )
        }.onFailure { Log.w(TAG, "ホームアプリ固定に失敗", it) }
    }

    /** 固定を解除する。設定画面から抜け出せなくなるのを防ぐための出口。 */
    fun unpinHomeLauncher(context: Context) {
        if (!isDeviceOwner(context)) return
        runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.clearPackagePersistentPreferredActivities(component(context), context.packageName)
        }.onFailure { Log.w(TAG, "ホームアプリ固定の解除に失敗", it) }
    }
}
