#!/usr/bin/env bash
#
# Google が公開している Android skills を .claude/skills/ に取得する。
#
#   https://github.com/android/skills  (Apache License 2.0, Google LLC)
#
# リポジトリには含めない（.gitignore 済み）。理由:
#   - 再頒布するとライセンス条件（全文同梱・帰属表示の保持）を背負う。
#     使うだけなら発生しない
#   - 上流は更新される。焼き込むと古い基準でレビューし続けることになる
#   - 秘密鍵（fetch-platform-keys.sh）と同じ「都度取得」で揃える
#
# エージェントに毎回同じ基準でレビューさせるのが目的なので、
# このリポジトリに関係するものだけを取る。全部は取らない。
#
# 使い方:
#   ./scripts/fetch-android-skills.sh            # 最新を取得
#   ./scripts/fetch-android-skills.sh --ref SHA  # 版を固定して取得
#
set -euo pipefail

cd "$(dirname "$0")/.."
DEST=".claude/skills"
UPSTREAM="https://github.com/android/skills.git"
REF="main"

# このリポジトリで使うものだけ。左が上流のパス。
SKILLS=(
  "build-system/agp/agp-9-upgrade"        # AGP 9 の DSL と削除された API
  "performance/r8-analyzer"               # R8 の設定と keep ルールの点検
  "security/android-intent-security"      # 公開コンポーネントと Intent の扱い
  "system/edge-to-edge"                   # 没入表示とシステムバー
  "testing/testing-setup"                 # テスト戦略、スクリーンショットテスト
)

while [ $# -gt 0 ]; do
  case "$1" in
    --ref) REF="$2"; shift 2 ;;
    -h|--help) sed -n '2,22p' "$0"; exit 0 ;;
    *) echo "不明な引数: $1" >&2; exit 1 ;;
  esac
done

step() { printf '\n\033[1m▸ %s\033[0m\n' "$1"; }
ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

step "上流を取得 ($REF)"
if [ "$REF" = "main" ]; then
  git clone -q --depth 1 "$UPSTREAM" "$TMP/skills"
else
  git clone -q "$UPSTREAM" "$TMP/skills"
  git -C "$TMP/skills" checkout -q "$REF"
fi
RESOLVED=$(git -C "$TMP/skills" rev-parse HEAD)
ok "$RESOLVED ($(git -C "$TMP/skills" log -1 --format=%cs))"

step "必要なスキルだけ配置"
rm -rf "$DEST"
mkdir -p "$DEST"
for skill in "${SKILLS[@]}"; do
  if [ ! -d "$TMP/skills/$skill" ]; then
    echo "  上流に $skill がありません。SKILLS の定義を見直してください。" >&2
    exit 1
  fi
  cp -R "$TMP/skills/$skill" "$DEST/"
  ok "$(basename "$skill")"
done

step "ライセンスと出所を残す"
# 各 SKILL.md が `license: Complete terms in LICENSE.txt` と書いているので、
# 参照先を必ず一緒に置く。ignore 対象だが、手元で読めないと意味がない。
cp "$TMP/skills/LICENSE.txt" "$DEST/LICENSE.txt"
cat > "$DEST/SOURCE.md" <<EOF
# 出所

このディレクトリの中身は $UPSTREAM から取得したもの。改変していない。

| 項目 | 内容 |
|---|---|
| 版 | \`$RESOLVED\` |
| 取得日 | $(date +%Y-%m-%d) |
| ライセンス | Apache License 2.0 (LICENSE.txt) |
| 著作権 | Google LLC |

git では追跡していない。取り直すには \`./scripts/fetch-android-skills.sh\`。
EOF
ok "LICENSE.txt / SOURCE.md"

printf '\n\033[1m完了\033[0m。%s に %d 個のスキルを配置しました。\n' "$DEST" "${#SKILLS[@]}"
