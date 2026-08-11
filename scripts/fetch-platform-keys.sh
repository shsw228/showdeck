#!/usr/bin/env bash
#
# AOSP のプラットフォーム署名鍵を取得して、Gradle が使えるキーストアを作る。
#
# この端末の LineageOS は ro.build.tags = test-keys、つまり AOSP が公開している
# テスト鍵で署名されている。同じ鍵でアプリを署名すると sharedUserId で
# system UID として動けるようになり、以下が root なしで手に入る。
#
#   - signature レベルの権限（WRITE_SECURE_SETTINGS / DEVICE_POWER など）
#   - /sys/class/leds/lcd-backlight/brightness への直接書き込み
#     （所有者が system:system かつ SELinux が Permissive のため）
#
# 鍵は AOSP の公開リポジトリにある「誰でも入手できるテスト鍵」であって秘密ではない。
# ただしリポジトリには含めず、このスクリプトで都度取得する。出所を明示するため。
#
set -euo pipefail

cd "$(dirname "$0")/.."
KEYS_DIR="keys"
BASE="https://android.googlesource.com/platform/build/+/refs/heads/main/target/product/security"
PASS="android"

# 端末側の署名と一致することを確認済みのフィンガープリント。
# 別の ROM に載せ替えたらここも確認し直すこと。
EXPECTED="c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"

step() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

mkdir -p "$KEYS_DIR"

step "AOSP から鍵を取得"
curl -sf "$BASE/platform.x509.pem?format=TEXT" | base64 -d > "$KEYS_DIR/platform.x509.pem"
curl -sf "$BASE/platform.pk8?format=TEXT"      | base64 -d > "$KEYS_DIR/platform.pk8"
[ -s "$KEYS_DIR/platform.x509.pem" ] || die "証明書の取得に失敗"
[ -s "$KEYS_DIR/platform.pk8" ]      || die "秘密鍵の取得に失敗"
ok "platform.x509.pem / platform.pk8"

step "フィンガープリントを検証"
ACTUAL=$(openssl x509 -in "$KEYS_DIR/platform.x509.pem" -noout -fingerprint -sha256 \
  | sed 's/.*=//; s/://g' | tr 'A-F' 'a-f')
if [ "$ACTUAL" != "$EXPECTED" ]; then
  die "想定と異なる鍵です: $ACTUAL"
fi
ok "$ACTUAL"

step "キーストアを生成"
# pk8 は DER 形式の PKCS#8。PEM に直してから PKCS#12 にまとめる。
openssl pkcs8 -inform DER -nocrypt -in "$KEYS_DIR/platform.pk8" -out "$KEYS_DIR/platform.key.pem"
openssl pkcs12 -export \
  -in "$KEYS_DIR/platform.x509.pem" \
  -inkey "$KEYS_DIR/platform.key.pem" \
  -name platform \
  -out "$KEYS_DIR/platform.p12" \
  -passout "pass:$PASS"
ok "$KEYS_DIR/platform.p12 (alias=platform, password=$PASS)"

step "端末の署名と照合"
if command -v adb >/dev/null 2>&1 && [ -n "$(adb devices | sed -n '2p')" ]; then
  APKSIGNER=$(find "${ANDROID_HOME:-$HOME/Library/Android/sdk}/build-tools" -name apksigner 2>/dev/null | sort -r | head -1)
  if [ -n "$APKSIGNER" ]; then
    TMP=$(mktemp -d)
    adb pull /system/framework/framework-res.apk "$TMP/f.apk" >/dev/null 2>&1
    DEVICE=$("$APKSIGNER" verify --print-certs "$TMP/f.apk" 2>/dev/null \
      | grep -m1 -i "SHA-256 digest" | sed 's/.*: *//')
    rm -rf "$TMP"
    if [ "$DEVICE" = "$EXPECTED" ]; then
      ok "端末のプラットフォーム署名と一致"
    else
      die "端末の署名が違います: $DEVICE（この ROM では system UID を取れません）"
    fi
  else
    printf '  \033[33m!\033[0m apksigner が見つからず照合をスキップ\n'
  fi
else
  printf '  \033[33m!\033[0m 端末が繋がっていないため照合をスキップ\n'
fi

printf '\n\033[1m完了\033[0m。sharedUserId を変えるため、既存インストールは一度消す必要があります:\n'
printf '  adb uninstall com.shsw228.showdeck\n'
printf '  ./gradlew installRelease\n'
