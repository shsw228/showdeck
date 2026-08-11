package com.shsw228.showdeck.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 前面を離れたら一定時間後にダッシュボードへ引き戻す。
 *
 * この端末はランチャーを置き換えているので、Android の設定を開いたあと
 * 戻る手段がユーザーの記憶しかない。放っておくと設定画面のまま朝を迎える。
 *
 * **「操作されていないこと」は検出できない。** 他アプリの中でのタップは
 * アクセシビリティサービスを入れないと見えない。だから実際には
 * 「離れてから一定時間」の固定タイムアウトになる。設定を触っている最中に
 * 引き戻されると邪魔なので、既定は余裕を持たせてある。
 *
 * 自前のコルーチンで待たないのは、背面に回ったプロセスがいつ止められるか
 * 保証が無いため。[AlarmManager] なら system_server 側で保持される。
 *
 * **ホームアプリとして使うときだけ張る。** そうでない端末で前面を奪うのは
 * ダッシュボードの越権（呼び出し側で判定している）。
 */
object HomeWatchdog {

    fun arm(context: Context, seconds: Int) {
        if (seconds <= 0) return
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val at = System.currentTimeMillis() + seconds * 1_000L
        // 分単位の話なので、doze を破ってまで正確に起こす必要はない。
        alarms.set(AlarmManager.RTC_WAKEUP, at, pendingIntent(context))
        Log.i(TAG, "$seconds 秒後に復帰する")
    }

    fun disarm(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pendingIntent(context))
    }

    /**
     * HOME インテントで起こす。クラスを直接指定してもよいが、HOME にしておくと
     * ランチャーを別のものに戻したときも「ホームへ帰る」意味が保てる。
     */
    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val TAG = "ShowDeck/Watchdog"
    private const val REQUEST_CODE = 1
}

/**
 * 「デフォルトのホームアプリ」の設定を開く。
 *
 * インストール時に選択ダイアログは出ない。Android がそれを出すのは
 * **ホームキーが押されて既定が未設定のとき**だけ。だから自分から
 * 選ばせる動線が要る。
 *
 * Device Owner なら固定もできるが、それは「二度と選択できなくする」操作。
 * 普通に選ぶだけならこちらを使う。
 */
fun openHomeSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Log.w("ShowDeck", "ホーム設定を開けなかった", it)
        openAndroidSettings(context)
    }
}

/** いま既定のホームアプリが自分か。 */
fun isDefaultHome(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    val resolved = context.packageManager.resolveActivity(intent, 0)
    return resolved?.activityInfo?.packageName == context.packageName
}

/** Android の設定を開く。 */
fun openAndroidSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure { Log.w("ShowDeck", "設定を開けなかった", it) }
}
