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

step "止めた常駐アプリを戻す"
run "pm enable com.android.systemui"
run "pm enable com.android.launcher3"

step "Device Owner を解除"
# `dpm remove-active-admin` は "Attempt to remove non-test admin" で必ず失敗する。
# Device Owner が付いている間はアンインストールも DELETE_FAILED_INTERNAL_ERROR になる。
# 実機で通った唯一の手順が、adb root で状態ファイルを消して再起動すること。
if adb $( [ -n "$SERIAL" ] && echo "-s $SERIAL" ) root >/dev/null 2>&1 && sleep 2 && \
   [ "$($ADB shell id -u 2>/dev/null | tr -d '\r')" = "0" ]; then
  run "rm -f /data/system/device_owner_2.xml /data/system/device_policies.xml"
  echo "  Device Owner の状態を削除しました。反映には再起動が要ります:"
  echo "    adb reboot && adb wait-for-device && adb uninstall $PKG"
else
  echo "  adb root が通りませんでした。userdebug ビルドであることを確認してください。" >&2
fi

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
