#!/usr/bin/env bash
#
# setup-device.sh が加えた変更を元に戻す。
#
# 「戻せる」ことを確認しないまま端末をいじると、実験の手が止まる。
# cronos は BROM USBDL が塞がれていて詰むと復旧できないので、
# ソフト側の変更は必ず往復できる状態を保つ。
#
set -uo pipefail

PKG="com.shsw228.showdeck"
ADMIN="$PKG/.admin.AdminReceiver"
SERIAL=""
[ "${1:-}" = "--serial" ] && { SERIAL="$2"; shift 2; }

ADB="adb"
if [ -n "$SERIAL" ]; then ADB="adb -s $SERIAL"; fi

step() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
run()  { $ADB shell "$@" 2>&1 | tr -d '\r' | sed 's/^/  /'; }

$ADB wait-for-device

step "SystemUI を戻す"
run "cmd package install-existing com.android.systemui"
run "pm enable com.android.systemui"

step "Device Owner を解除"
# Device Owner は端末側からは外せないため、アプリごと消すのが唯一の手段。
run "dpm remove-active-admin $ADMIN"

step "電源まわりを既定に戻す"
run "settings put global stay_on_while_plugged_in 0"
run "settings put system screen_off_timeout 60000"

step "隠し API の制限を戻す"
run "settings delete global hidden_api_policy"

step "省電力の除外を解除"
run "dumpsys deviceidle whitelist -$PKG"

step "既定のランチャーを選び直す"
echo "  以下で選択ダイアログが出ます:"
run "cmd package set-home-activity com.android.launcher3/.Launcher"
echo "  （LineageOS なら org.lineageos.trebuchet/.Launcher の場合もあります）"

printf '\n完了。必要なら adb uninstall %s\n' "$PKG"
