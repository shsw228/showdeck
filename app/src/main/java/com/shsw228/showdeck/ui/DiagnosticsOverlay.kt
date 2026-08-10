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
 * 設定変更 UI はここではなく、設定の永続化層と一緒に入れる（ロードマップ 3）。
 */
@Composable
fun DiagnosticsOverlay(
    capabilities: DeviceSetup.Capabilities,
    ipAddress: String?,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .clickable(onClick = onDismiss)
            .padding(24.dp),
    ) {
        BasicText(
            text = "ShowDeck / 診断",
            style = TextStyle(color = Color(0xFFF2EFE6), fontSize = 20.sp),
        )
        Spacer(Modifier.height(12.dp))

        Capability("WRITE_SECURE_SETTINGS", capabilities.canWriteSecureSettings)
        Capability("WRITE_SETTINGS", capabilities.canWriteSystemSettings)
        Capability("Device Owner", capabilities.isDeviceOwner)
        Capability("root (su)", capabilities.hasRoot)
        Capability("OS 下限を超える減光", capabilities.canDimBelowSystemMinimum)

        Spacer(Modifier.height(12.dp))
        BasicText(
            text = "IP: ${ipAddress ?: "取得できず"}",
            style = TextStyle(color = Color(0xFF9AA0A6), fontSize = 14.sp, fontFamily = FontFamily.Monospace),
        )
        Spacer(Modifier.height(12.dp))
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
