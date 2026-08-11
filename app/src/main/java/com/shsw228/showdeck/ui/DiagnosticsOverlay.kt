package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shsw228.showdeck.system.DeviceSetup

/**
 * 長押しで出る診断オーバーレイ。
 *
 * 「なぜ夜間モードがこれ以上暗くならないのか」「Device Owner 化は効いているのか」を
 * 端末の前で即座に確認できるようにする。据え置き機は Mac の前に持ってくるのが面倒なので、
 * 画面上で状態が読めることの価値が大きい。
 *
 * 設定の変更は端末上ではなく、ここに表示される URL を PC で開いて行う。
 */
@Composable
fun DiagnosticsOverlay(
    capabilities: DeviceSetup.Capabilities,
    ipAddress: String?,
    webPort: Int,
    webUser: String,
    webPassword: String,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 半透明にすると背後の時計が透けて診断項目が読めない。完全に覆う。
            .background(Color(0xFF000000))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
    ) {
        BasicText(
            text = "ShowDeck / 診断",
            style = TextStyle(color = Color(0xFFF2EFE6), fontSize = 20.sp),
        )
        Spacer(Modifier.height(12.dp))

        Capability("system UID (プラットフォーム署名)", capabilities.isSystemUid)
        Capability("WRITE_SECURE_SETTINGS", capabilities.canWriteSecureSettings)
        Capability("WRITE_SETTINGS", capabilities.canWriteSystemSettings)
        Capability("Device Owner", capabilities.isDeviceOwner)
        Capability("バックライト直書き（OS 下限を超える減光）", capabilities.canWriteBacklight)
        Capability("root (su)", capabilities.hasRoot)

        Spacer(Modifier.height(16.dp))

        // 設定は端末上ではなく PC のブラウザで行う。ここが唯一の入口の案内なので、
        // そのまま打ち込めるよう **ポートまで含めた URL** を出す。
        // IP だけ出していた時期があり、ポートが分からず開けなかった。
        BasicText(
            text = "設定画面",
            style = TextStyle(color = Color(0xFF6B7075), fontSize = 12.sp),
        )
        BasicText(
            text = ipAddress?.let { "http://$it:$webPort" } ?: "IP を取得できません",
            style = TextStyle(
                color = Color(0xFFF2EFE6),
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )

        // Basic 認証の資格情報。端末の画面から読んで打ち込む前提なので、
        // 隠さずそのまま出す。伏せると設定画面に入る手段が無くなる。
        Spacer(Modifier.height(6.dp))
        BasicText(
            text = if (webPassword.isBlank()) {
                "パスワードを生成中"
            } else {
                "$webUser / $webPassword"
            },
            style = TextStyle(
                color = Color(0xFF9AA0A6),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
            ),
        )

        Spacer(Modifier.height(14.dp))
        BasicText(
            text = "タップで閉じる",
            style = TextStyle(color = Color(0xFF4A4F55), fontSize = 12.sp),
        )
    }
}

@Composable
private fun Capability(label: String, granted: Boolean) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        BasicText(
            text = if (granted) "✓" else "✗",
            style = TextStyle(
                color = if (granted) Color(0xFF6FBF73) else Color(0xFFB4564A),
                fontSize = 14.sp,
            ),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            text = label,
            style = TextStyle(color = Color(0xFFC8C4BA), fontSize = 14.sp),
        )
    }
}
