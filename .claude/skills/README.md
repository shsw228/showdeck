# 取り込んでいる Android skills

このディレクトリの `LICENSE.txt` 以外のファイルは、Google が公開している
[android/skills](https://github.com/android/skills) からそのまま複製したもの。

| 項目 | 内容 |
|---|---|
| 出所 | https://github.com/android/skills |
| 版 | `1e5e7ae6138bebd0835d0d5854b0b9adfeed3181`（2026-08-07） |
| ライセンス | Apache License 2.0（`LICENSE.txt`） |
| 著作権 | Google LLC（各 `SKILL.md` の frontmatter に記載） |
| 改変 | **なし。** 中身は一切変えていない |

## なぜリポジトリに入れているか

エージェントに毎回同じ基準でレビューさせるため。手元にあるかどうかで
指摘の内容が変わると、レビューの結果を比較できない。

公式の `android skills add --project=.` も同じ場所に配置する。

## 入れているもの

このリポジトリに関係するものだけを選んでいる。全部は入れていない。

| スキル | 用途 |
|---|---|
| `agp-9-upgrade` | AGP 9 の DSL と削除された API |
| `r8-analyzer` | R8 の設定と keep ルールの点検 |
| `android-intent-security` | 公開コンポーネントと Intent の扱い |
| `edge-to-edge` | 没入表示とシステムバー |
| `testing-setup` | テスト戦略、スクリーンショットテスト |

## 更新のしかた

上流を clone して該当ディレクトリを上書きし、この README の版を書き換える。
改変しないこと。改変するなら Apache-2.0 の 4 条 b に従って
「変更した旨」を各ファイルに明示する必要が出る。
