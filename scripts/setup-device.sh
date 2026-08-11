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
#
set -uo pipefail

PKG="com.shsw228.showdeck"
ACTIVITY="$PKG/.MainActivity"
ADMIN="$PKG/.admin.AdminReceiver"
SERIAL=""

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
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

step "没入モードの確認ダイアログを抑止"
# 没入モードに入るたび SystemUI が「全画面表示」のダイアログを被せてくる。
run "settings put secure immersive_mode_confirmations confirmed"

# hidden_api_policy は触らない。ShowDeck はリフレクションを一切使っておらず、
# 緩めると端末上の全アプリの制限まで下がる。以前に設定した端末は
# revert-device.sh が既定へ戻す。

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

# 常駐アプリは止めない。
#
# 以前は launcher3 と SystemUI を無効化して MemAvailable を稼いでいたが、
# どちらも「稼ぎより失うもののほうが大きい」判断ミスだった。
#
#   - SystemUI: ジェスチャーの戻る・音量パネル・電源長押しメニューを失う。
#     この端末に物理の戻るキーは無いので、設定画面に入ったら戻れない
#   - launcher3: **ShowDeck をホームにしない選択肢が消える。** 戻る先が
#     無くなるので「ホームアプリとして使う」設定が意味を持たなくなる
#
# 実測（何も無効化しない状態）: MemTotal 996MB / MemAvailable 343MB。
# 単一用途の端末には十分。

# SystemUI は**止めない。**
#
# 止めれば PSS 25MB ぶん空くが（実測。MemAvailable 417MB の 6%）、
# 代わりに次を失う。
#
#   - 標準設定のナビゲーションバー。この端末に物理の戻るキーが無いので、
#     設定タブから Android 設定を開いたあと戻れなくなる
#   - 電源長押しメニュー。端末上で再起動も電源オフもできなくなる
#
# 音量パネルは失っても構わない（アプリ側でインジケータを出している）。
# 25MB のためにこれを買うのは損。

step "起動"
run "am start -n $ACTIVITY"

printf '\n\033[1m完了\033[0m。設定内容の確認は ./scripts/check-device.sh\n'
