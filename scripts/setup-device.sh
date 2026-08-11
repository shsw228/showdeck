#!/usr/bin/env bash
#
# ShowDeck を動かすための端末側セットアップ。
#
# 方針: アプリ側で頑張らず、環境側で決めてしまう。
#       ストア配布しない端末なので、adb で取れる特権はすべて取りに行く。
#       ここで行う変更はすべて scripts/revert-device.sh で戻せる。
#
# root は不要。プラットフォーム署名で system UID を取っているため、権限は署名だけで
# 通る。ここで adb が本当に要るのは Device Owner 化だけ。
#
# 使い方:
#   ./scripts/setup-device.sh                 # 一括適用
#   ./scripts/setup-device.sh --serial XXXX   # 端末を指定
#   ./scripts/setup-device.sh --no-systemui   # SystemUI も畳む（要再起動）
#
set -uo pipefail

PKG="com.shsw228.showdeck"
ACTIVITY="$PKG/.MainActivity"
ADMIN="$PKG/.admin.AdminReceiver"
SERIAL=""
DISABLE_SYSTEMUI=0

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --no-systemui) DISABLE_SYSTEMUI=1; shift ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "不明な引数: $1" >&2; exit 1 ;;
  esac
done

ADB="adb"
if [ -n "$SERIAL" ]; then ADB="adb -s $SERIAL"; fi

step() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }

run() {
  # 失敗しても止めない。多くは「すでに適用済み」なので握りつぶして続行する。
  if out=$($ADB shell "$@" 2>&1 | tr -d '\r'); then
    if [ -n "$out" ]; then warn "$out"; else ok "$*"; fi
  else
    warn "$* -> $out"
  fi
}

command -v adb >/dev/null 2>&1 || {
  echo "adb がありません。 brew install --cask android-platform-tools" >&2; exit 1; }

$ADB wait-for-device

step "アプリが入っているか確認"
if $ADB shell pm path "$PKG" >/dev/null 2>&1 && [ -n "$($ADB shell pm path "$PKG" | tr -d '\r')" ]; then
  ok "$PKG はインストール済み"
else
  echo "  $PKG が見つかりません。先に ./gradlew installRelease してください。" >&2
  echo "  鍵が無いとインストールできません: ./scripts/fetch-platform-keys.sh" >&2
  exit 1
fi

step "権限を付与"
# プラットフォーム署名が効いていれば署名だけで通るので、ここは保険。
# 署名なしでビルドした場合でも動くようにするために残してある。
run "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS"
run "appops set $PKG WRITE_SETTINGS allow"

step "常時点灯にする"
# 7 = AC + USB + ワイヤレスのいずれかで給電中はスリープしない
run "settings put global stay_on_while_plugged_in 7"
run "settings put system screen_off_timeout 2147483647"
run "locksettings set-disabled true"

step "省電力から除外"
run "dumpsys deviceidle whitelist +$PKG"

step "隠し API の制限を解除"
# 1 = 検出しても警告のみ。内部 API を呼ぶために必要。
run "settings put global hidden_api_policy 1"
# 没入モードに入るたび SystemUI が「全画面表示」のダイアログを被せてくるのを抑止。
run "settings put secure immersive_mode_confirmations confirmed"

step "既定のランチャーに設定"
run "cmd package set-home-activity $ACTIVITY"

step "Device Owner に設定"
# 端末にアカウントが 1 つでも登録されていると必ず失敗する。
# 失敗してもアプリは動く（ステータスバー無効化が効かなくなるだけ）。
if $ADB shell dpm set-device-owner "$ADMIN" 2>&1 | tr -d '\r' | grep -q Success; then
  ok "Device Owner 化 成功"
else
  warn "Device Owner 化 失敗。端末からアカウントを全て削除してから再実行すると通ります。"
  warn "  確認: adb shell dumpsys account | grep Account"
fi

step "不要な常駐アプリを止める"
# 実測: MemAvailable が 238MB -> 429MB になった。空きが 30MB しか無い端末なので効く。
# launcher3 は ShowDeck が置き換えているので純粋に無駄。停止すると 40MB 戻る。
run "pm disable-user --user 0 com.android.launcher3"
if [ "$DISABLE_SYSTEMUI" -eq 1 ]; then
  # SystemUI は system_server が明示的に起動するため、disable しても常駐は続く。
  # ただし機能を畳むぶん PSS が 62MB -> 24MB に減る。効果を得るには再起動が要る。
  # 完全に消すには user 0 からのアンインストールが要るが、ブートループの危険があり
  # この端末は復旧が難しいのでやらない。
  warn "反映には再起動が必要です。戻すのは revert-device.sh"
  run "pm disable-user --user 0 com.android.systemui"
fi

step "起動"
run "am start -n $ACTIVITY"

printf '\n\033[1m完了\033[0m。設定内容の確認は ./scripts/check-device.sh\n'
