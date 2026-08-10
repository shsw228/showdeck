#!/usr/bin/env bash
# 端末の素性を洗い出す。設計判断（RAM の余裕・密度・root の有無）はここの出力で決める。
# 何も変更しないので、いつ実行しても安全。
set -uo pipefail

ADB="${ADB:-adb}"
SERIAL="${1:-}"
if [ -n "$SERIAL" ]; then ADB="$ADB -s $SERIAL"; fi

prop() { $ADB shell getprop "$1" 2>/dev/null | tr -d '\r'; }

section() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }

if ! command -v adb >/dev/null 2>&1; then
  echo "adb が見つかりません。 brew install --cask android-platform-tools" >&2
  exit 1
fi

$ADB wait-for-device

section "端末"
printf '%-22s %s\n' "モデル"        "$(prop ro.product.model)"
printf '%-22s %s\n' "デバイス名"    "$(prop ro.product.device)"
printf '%-22s %s\n' "ビルド"        "$(prop ro.build.display.id)"
printf '%-22s %s\n' "Android"       "$(prop ro.build.version.release) (API $(prop ro.build.version.sdk))"
printf '%-22s %s\n' "ビルドタイプ"  "$(prop ro.build.type)"
printf '%-22s %s\n' "署名鍵"        "$(prop ro.build.tags)"

section "SoC / メモリ"
printf '%-22s %s\n' "ハードウェア"  "$(prop ro.hardware)"
printf '%-22s %s\n' "プラットフォーム" "$(prop ro.board.platform)"
printf '%-22s %s\n' "ABI"           "$(prop ro.product.cpu.abi)"
printf '%-22s %s\n' "コア数"        "$($ADB shell 'nproc' 2>/dev/null | tr -d '\r')"
$ADB shell 'cat /proc/meminfo | head -3' 2>/dev/null | tr -d '\r' | sed 's/^/  /'

section "画面"
$ADB shell wm size 2>/dev/null | tr -d '\r' | sed 's/^/  /'
$ADB shell wm density 2>/dev/null | tr -d '\r' | sed 's/^/  /'

section "root"
if $ADB shell 'su -c id' 2>/dev/null | grep -q 'uid=0'; then
  echo "  su あり（バックライト直書き・priv-app 設置が可能）"
else
  echo "  su なし（ロードマップ 1〜2 の大半はこのままで進められる）"
fi

section "バックライト候補パス"
$ADB shell 'ls /sys/class/leds/ 2>/dev/null; ls /sys/class/backlight/ 2>/dev/null' \
  | tr -d '\r' | sed 's/^/  /'

section "現在の関連設定"
for k in stay_on_while_plugged_in hidden_api_policy; do
  printf '  %-28s %s\n' "global/$k" "$($ADB shell settings get global $k 2>/dev/null | tr -d '\r')"
done
printf '  %-28s %s\n' "system/screen_off_timeout" \
  "$($ADB shell settings get system screen_off_timeout 2>/dev/null | tr -d '\r')"

section "常駐に効きそうな既存パッケージ"
$ADB shell 'pm list packages -e com.android.systemui; pm list packages -3' \
  2>/dev/null | tr -d '\r' | sed 's/^/  /'
