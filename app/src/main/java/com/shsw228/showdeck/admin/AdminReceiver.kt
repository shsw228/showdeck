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
 * 使うのはホームアプリの固定だけ。ステータスバーは没入モードで隠すので、
 * ポリシーでの無効化はしない（[DeviceAdmin.enableStatusBar] を参照）。
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
     * ステータスバーの無効化を**解除**する。
     *
     * バーを隠すのは没入モード（`WindowInsetsController.hide`）の仕事で、
     * ポリシーで無効化する必要はない。`setStatusBarDisabled(true)` は
     * 通知シェードだけでなく**音量ダイアログまで止める**ので、音量キーを
     * 押しても何も出なくなる。
     *
     * 過去に有効化した端末が残っているので、毎起動で明示的に false に戻す。
     */
    fun enableStatusBar(context: Context) {
        if (!isDeviceOwner(context)) {
            Log.i(TAG, "Device Owner ではないためステータスバーの設定をスキップ")
            return
        }
        runCatching {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.setStatusBarDisabled(component(context), false)
        }.onFailure { Log.w(TAG, "ステータスバーの設定に失敗", it) }
    }

    /**
     * 自分自身を既定のホームアプリとして固定する／解除する。
     *
     * **既定は解除。** 固定すると選択ダイアログが二度と出なくなり、他の
     * ランチャーを入れても奪われない。据え置き専用にするなら有用だが、
     * 普通の Android 端末としても使いたい場合には邪魔になる。
     * 設定（`DeckSettings.homeLauncher`）で選ぶ。
     */
    fun pinAsHomeLauncher(context: Context, pinned: Boolean) {
        if (!pinned) {
            // 通常の preferred activity も消す。
            //
            // Device Owner の `clearPackagePersistentPreferredActivities` は
            // **永続設定のほうしか消さない。** 通常の既定が残っていると
            // ホームキーで選択ダイアログが出ず、外したつもりで ShowDeck に
            // 戻り続ける。system UID なのでこちらも呼べる。
            runCatching {
                context.packageManager.clearPackagePreferredActivities(context.packageName)
            }.onFailure { Log.w(TAG, "既定ホームの解除に失敗", it) }

            if (isDeviceOwner(context)) {
                runCatching {
                    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
                        as DevicePolicyManager
                    dpm.clearPackagePersistentPreferredActivities(
                        component(context),
                        context.packageName,
                    )
                }.onFailure { Log.w(TAG, "ホームアプリ固定の解除に失敗", it) }
            }
            return
        }
        if (!isDeviceOwner(context)) {
            // 固定には Device Owner が要る。無いときは何もしない
            // （手動で「常時」を選ぶことはできる）。
            Log.i(TAG, "Device Owner ではないためホームアプリ固定をスキップ")
            return
        }
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
