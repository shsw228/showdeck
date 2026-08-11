package com.shsw228.showdeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.shsw228.showdeck.DeckMode
import com.shsw228.showdeck.DeckUiState
import com.shsw228.showdeck.settings.DeckSettings
import com.shsw228.showdeck.settings.minutesToTime
import com.shsw228.showdeck.ui.parts.ButtonLabel
import com.shsw228.showdeck.ui.parts.DashedRule
import com.shsw228.showdeck.ui.parts.DeckIcon
import com.shsw228.showdeck.ui.parts.DeckIcons
import com.shsw228.showdeck.ui.parts.Gap
import com.shsw228.showdeck.ui.parts.Label
import com.shsw228.showdeck.ui.parts.PillButton
import com.shsw228.showdeck.ui.parts.Readout
import com.shsw228.showdeck.ui.parts.Tile
import com.shsw228.showdeck.ui.parts.tappable
import com.shsw228.showdeck.ui.theme.DeckMetrics
import com.shsw228.showdeck.ui.theme.DeckPalette
import com.shsw228.showdeck.ui.theme.DeckType

/**
 * 設定。
 *
 * ここに置くのは**その場で押して即座に効くもの**だけ。明るさ、表示の選び方、
 * 時間帯、ポモドーロの長さ。文字を打つもの（ICS の URL、API キー、地名）は
 * 5.5 インチでは苦行なので Web 設定画面に残す。
 *
 * 左に設定の列、右に端末の状態と外部への出口。
 */
@Composable
fun SettingsScreen(
    state: DeckUiState,
    palette: DeckPalette,
    webPort: Int,
    actions: DeckActions,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DeckMetrics.TileGap),
        ) {
            DisplaySection(state.settings, palette, actions)
            BacklightSection(state, palette, actions)
            FocusSection(state.settings, palette, actions)
            ScheduleSection(state.settings, palette, actions)
        }

        StatusPanel(
            state = state,
            palette = palette,
            webPort = webPort,
            actions = actions,
            modifier = Modifier.width(DeckMetrics.SidePanelWidth).fillMaxHeight(),
        )
    }
}

// --- 表示 ---

@Composable
private fun DisplaySection(
    settings: DeckSettings,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Tile(palette, Modifier.fillMaxWidth()) {
        Label("Display", palette.tide)
        Gap(DeckMetrics.Space3)

        Choice(
            title = "Navigation",
            options = NavStyle.entries.map { it to it.settingLabel },
            selected = settings.navStyle,
            palette = palette,
            onPick = { actions.setNavStyle(it.name) },
        )
        Gap(DeckMetrics.Space3)

        Choice(
            title = "Home layout",
            options = HomeLayout.entries.map { it to it.settingLabel },
            selected = settings.homeLayout,
            palette = palette,
            onPick = { actions.setHomeLayout(it.name) },
        )
        Gap(DeckMetrics.Space3)

        Toggle("24-hour clock", settings.clock24, palette) { actions.setClock24(it) }
        Gap(DeckMetrics.Space2)
        Toggle("Show seconds", settings.showSeconds, palette) { actions.setShowSeconds(it) }
        Gap(DeckMetrics.Space2)
        // 固定すると選択ダイアログが出なくなる。普通の端末としても使いたい
        // なら切っておく。
        Toggle("Use as home app", settings.homeLauncher, palette) { actions.setHomeLauncher(it) }
        Gap(DeckMetrics.Space2)
        // 切ると SystemUI のスライダが出る（キーを消費しない）。
        Toggle("Own volume bar", settings.volumeOverlay, palette) { actions.setVolumeOverlay(it) }
    }
}

// --- 明るさ ---

@Composable
private fun BacklightSection(
    state: DeckUiState,
    palette: DeckPalette,
    actions: DeckActions,
) {
    val night = state.mode != DeckMode.DAY

    Tile(palette, Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label("Backlight", palette.tide)
            // いま効いているのがどちらの値かを出す。片方だけ動かすので、
            // どちらを触っているか分からないと迷う。
            BasicText(
                text = if (night) "night · ${state.backlightRaw}" else "day · ${state.backlightRaw}",
                style = DeckType.Meta.copy(color = palette.ink3),
            )
        }
        Gap(DeckMetrics.Space3)

        Row(horizontalArrangement = Arrangement.spacedBy(DeckMetrics.Space2)) {
            PillButton(
                onClick = { actions.adjustBrightness(-1) },
                background = palette.surface,
                modifier = Modifier.weight(1f),
                height = DeckMetrics.ButtonHeightSm,
            ) { ButtonLabel("Dimmer", palette.ink, DeckType.Body) }
            PillButton(
                onClick = { actions.adjustBrightness(1) },
                background = palette.surface,
                modifier = Modifier.weight(1f),
                height = DeckMetrics.ButtonHeightSm,
            ) { ButtonLabel("Brighter", palette.ink, DeckType.Body) }
        }
    }
}

// --- 集中 ---

@Composable
private fun FocusSection(
    settings: DeckSettings,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Tile(palette, Modifier.fillMaxWidth()) {
        Label("Focus", palette.tide)
        Gap(DeckMetrics.Space3)

        Stepper(
            title = "Work",
            value = "${settings.pomodoroWorkMinutes} min",
            palette = palette,
            onStep = { actions.setPomodoroWorkMinutes(settings.pomodoroWorkMinutes + it * 5) },
        )
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Short break",
            value = "${settings.pomodoroShortBreakMinutes} min",
            palette = palette,
            onStep = { actions.setPomodoroShortBreak(settings.pomodoroShortBreakMinutes + it) },
        )
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Long break",
            value = "${settings.pomodoroLongBreakMinutes} min",
            palette = palette,
            onStep = { actions.setPomodoroLongBreak(settings.pomodoroLongBreakMinutes + it * 5) },
        )
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Rounds until long break",
            value = "${settings.pomodoroRoundsBeforeLongBreak} ×",
            palette = palette,
            onStep = { actions.setPomodoroRounds(settings.pomodoroRoundsBeforeLongBreak + it) },
        )

        Gap(DeckMetrics.Space3)
        Toggle("Auto-start breaks", settings.pomodoroAutoStartBreak, palette) {
            actions.setPomodoroAutoBreak(it)
        }
        Gap(DeckMetrics.Space2)
        Toggle("Auto-start work", settings.pomodoroAutoStartWork, palette) {
            actions.setPomodoroAutoWork(it)
        }
    }
}

// --- 時間帯 ---

@Composable
private fun ScheduleSection(
    settings: DeckSettings,
    palette: DeckPalette,
    actions: DeckActions,
) {
    Tile(palette, Modifier.fillMaxWidth()) {
        Label("Schedule", palette.tide)
        Gap(DeckMetrics.Space3)

        // 時刻は 30 分刻みで送る。分単位で刻める UI をこの画面幅に載せると
        // 押し間違えるだけで、夜間の開始を 23:00 か 23:30 かで悩むことはない。
        Stepper(
            title = "Night from",
            value = clockText(settings.nightStartMinutes),
            palette = palette,
            onStep = { actions.setNightStart(settings.nightStartMinutes + it * HALF_HOUR) },
        )
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Night until",
            value = clockText(settings.nightEndMinutes),
            palette = palette,
            onStep = { actions.setNightEnd(settings.nightEndMinutes + it * HALF_HOUR) },
        )

        Gap(DeckMetrics.Space3)
        Toggle("Blackout", settings.blackoutEnabled, palette) { actions.setBlackout(it) }
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Blackout from",
            value = clockText(settings.blackoutStartMinutes),
            palette = palette,
            onStep = { actions.setBlackoutStart(settings.blackoutStartMinutes + it * HALF_HOUR) },
        )
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Blackout until",
            value = clockText(settings.blackoutEndMinutes),
            palette = palette,
            onStep = { actions.setBlackoutEnd(settings.blackoutEndMinutes + it * HALF_HOUR) },
        )

        Gap(DeckMetrics.Space3)
        Toggle("Daily alarm", settings.alarmEnabled, palette) { actions.setAlarmEnabled(it) }
        Gap(DeckMetrics.Space2)
        // 端末のマナーモードは設定に関係なく尊重する。これは鳴らせる状態でも
        // 鳴らさないための設定。
        Toggle("Vibrate only", settings.alertHapticOnly, palette) { actions.setAlertHapticOnly(it) }
        Gap(DeckMetrics.Space2)
        Stepper(
            title = "Alarm at",
            value = clockText(settings.alarmMinutes),
            palette = palette,
            onStep = { actions.setAlarmTime(settings.alarmMinutes + it * HALF_HOUR) },
        )
    }
}

// --- 右の状態パネル ---

@Composable
private fun StatusPanel(
    state: DeckUiState,
    palette: DeckPalette,
    webPort: Int,
    actions: DeckActions,
    modifier: Modifier,
) {
    // 権限の行数と文字の大きさで溢れるので縦スクロールさせる。
    // 末尾の「Android settings」に届かないと、設定へ入る手段が無くなる。
    Readout(palette, modifier.verticalScroll(rememberScrollState())) {
        Label("Web settings", palette.readoutMut)
        Gap(DeckMetrics.Space2)
        // ポートまで出す。IP だけでは繋げない。
        BasicText(
            text = "http://${state.ipAddress ?: "?"}:$webPort",
            style = DeckType.Body.copy(color = palette.readoutFg),
        )
        Gap(DeckMetrics.Space1)
        BasicText(
            text = "Calendar URLs and the weather key live here",
            style = DeckType.MetaSm.copy(color = palette.readoutMut),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Gap(DeckMetrics.Space3)
        DashedRule(palette.readoutMut)
        Gap(DeckMetrics.Space3)

        Label("Permissions", palette.readoutMut)
        Gap(DeckMetrics.Space2)
        state.capabilities?.let { caps ->
            Capability("system UID", caps.isSystemUid, palette)
            Capability("Secure settings", caps.canWriteSecureSettings, palette)
            Capability("System settings", caps.canWriteSystemSettings, palette)
            Capability("Device Owner", caps.isDeviceOwner, palette)
            Capability("Backlight write", caps.canWriteBacklight, palette)
        }
        state.luxReading?.let {
            Gap(DeckMetrics.Space2)
            BasicText(
                text = "${it.toInt()} lux",
                style = DeckType.Meta.copy(color = palette.readoutMut),
            )
        }

        Box(Modifier.weight(1f))

        // この端末はランチャーを置き換えているので、ステータスバーも
        // 通知シェードも無い。**ここが Android の設定への唯一の入口。**
        PillButton(
            onClick = actions.openAndroidSettings,
            background = DeckPalette.ReadoutAccent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ButtonLabel("Android settings", DeckPalette.OnReadoutAccent)
        }
        Gap(DeckMetrics.Space1)
        BasicText(
            text = "Returns here after ${state.settings.returnAfterSeconds / 60} min idle",
            style = DeckType.MetaSm.copy(color = palette.readoutMut),
        )
    }
}

// --- 部品 ---

/**
 * 選択肢。material3 の分割ボタン。
 *
 * 3 つ以内なら並べたほうが、開いて選ぶより速い。見た目・押下・選択の
 * 表現はプラットフォームに任せる。
 */
@Composable
private fun <T : Enum<T>> Choice(
    title: String,
    options: List<Pair<T, String>>,
    selected: String,
    palette: DeckPalette,
    onPick: (T) -> Unit,
) {
    BasicText(text = title, style = DeckType.BodySm.copy(color = palette.ink2))
    Gap(DeckMetrics.Space2)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value.name == selected,
                onClick = { onPick(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, style = DeckType.BodySm) },
                icon = {},
            )
        }
    }
}

/**
 * ON/OFF。material3 の [Switch]。
 *
 * 行ごと押せるようにしてあるが、つまみ自体も押せる。標準の部品なので、
 * 状態の見え方も指で触ったときの返りもプラットフォームが面倒を見る。
 */
@Composable
private fun Toggle(
    title: String,
    on: Boolean,
    palette: DeckPalette,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(DeckMetrics.RowShape)
            .tappable { onChange(!on) }
            .heightIn(min = DeckMetrics.ButtonHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style = DeckType.BodySm.copy(color = palette.ink2),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = on, onCheckedChange = onChange)
    }
}

/** 増減。値の左右に錠剤を置く。 */
@Composable
private fun Stepper(
    title: String,
    value: String,
    palette: DeckPalette,
    onStep: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(DeckMetrics.ButtonHeightSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = title,
            style = DeckType.BodySm.copy(color = palette.ink2),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StepButton("−", palette) { onStep(-1) }
        // 値は中央寄せ。左寄せだと「25 min」と「5 min」で桁が揃わず、
        // 増減ボタンとの間隔も行ごとに変わって見える。
        BasicText(
            text = value,
            style = DeckType.Meta.copy(
                color = palette.ink,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            maxLines = 1,
            modifier = Modifier.width(STEPPER_VALUE_WIDTH),
        )
        StepButton("＋", palette) { onStep(1) }
    }
}

@Composable
private fun StepButton(sign: String, palette: DeckPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(DeckMetrics.ButtonHeightSm)
            .width(DeckMetrics.ButtonHeightSm)
            .clip(DeckMetrics.Pill)
            .background(palette.surface)
            .tappable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = sign, style = DeckType.Body.copy(color = palette.ink))
    }
}

@Composable
private fun Capability(label: String, granted: Boolean, palette: DeckPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(text = label, style = DeckType.MetaSm.copy(color = palette.readoutMut))
        BasicText(
            text = if (granted) "OK" else "NG",
            style = DeckType.MetaSm.copy(color = if (granted) OK else NG),
        )
    }
    Gap(DeckMetrics.Space1)
}

private fun clockText(minutes: Int) = minutesToTime(minutes).let {
    "%02d:%02d".format(it.hour, it.minute)
}

/** 設定画面に出す名前。enum の名前をそのまま出すと読めない。 */
private val NavStyle.settingLabel: String
    get() = when (this) {
        NavStyle.RAIL -> "Rail"
        NavStyle.DOCK -> "Dock"
        NavStyle.TILES -> "Tiles"
    }

private val HomeLayout.settingLabel: String
    get() = when (this) {
        HomeLayout.TIMELINE -> "Timeline"
        HomeLayout.GRID -> "Grid"
        HomeLayout.HERO -> "Focus"
    }

private const val HALF_HOUR = 30
/** 「25 min」が中央に収まる幅。 */
private val STEPPER_VALUE_WIDTH = androidx.compose.ui.unit.Dp(84f)

private val OK = Color(0xFF6FBF73)
private val NG = Color(0xFFB4564A)
