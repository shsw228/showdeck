# Repository Guidelines

Android 化した Echo Show 5 第2世代（`cronos`）向けの常駐ダッシュボード。設計の前提は README.md を参照。

## この端末固有の制約

作業前に必ず踏まえること。一般的な Android アプリの常識が通用しない箇所がある。

- **960×480 / 5.5 インチ / 視距離 1〜3m**。密度は機種で読めないので、サイズは必ず画面高からの相対で決める。固定 dp を書かない
- **RAM が少ない**。material3・WebView・大きな画像ライブラリを安易に入れない。依存を足すときは理由をコメントに残す
- **常時表示**。毎秒の再コンポーズを避ける。時刻は `State` のまま末端へ渡し、`derivedStateOf` で実際に変わったときだけ再コンポーズさせる
- **暗い部屋で使う**。新しい画面を足すときは夜間パレットでの見え方を必ず確認する
- **ストア配布しない**。`targetSdk = 28` は意図的。上げると Android 10 以降の制約が復活するので、上げる理由がない限り触らない

## 端末を壊さないための鉄則

`cronos` は BROM USBDL が塞がれており、パーティションを壊すと**復旧できない**。

- `LK` / `Preloader` / `TZ` パーティションには絶対に触れない
- 端末設定を変更するコードを足したら、`scripts/revert-device.sh` に戻す手順も同時に足す
- root コマンドは `Su` 経由に集約する。`Runtime.exec` を各所に散らさない

## Build, Test, and Development Commands

```sh
./gradlew assembleDebug        # ビルド
./gradlew installDebug         # 実機へインストール
./scripts/check-device.sh      # 端末の素性を確認（何も変更しない）
./scripts/setup-device.sh      # 端末側セットアップ
./scripts/revert-device.sh     # セットアップを巻き戻す
adb logcat -s ShowDeck:V 'ShowDeck/*:V'
```

debug ビルドは `applicationId` に `.debug` が付く。スクリプトは release の ID を前提にしているので、debug で試すときは `PKG` を読み替える。

## Coding Style & Naming Conventions

- Kotlin 公式スタイル。インデント 4 スペース
- コメントは日本語で書く。**何をしているか**ではなく**なぜそうしたか**を書く。この端末は判断の理由が失われると再現できない
- Composable は `PascalCase`、それ以外の関数は `camelCase`
- 権限やシステム設定に触る処理は `system/` か `admin/` に閉じ込め、UI 層から直接呼ばない

## Testing Guidelines

現状ユニットテストは無い。ロジックを足すときは `app/src/test/` に Kotlin のプレーンなテストを置く。時刻判定（`isNightAt` の日付またぎなど）は実機を待たずに検証できるので優先的にテストする。

実機確認は `./scripts/check-device.sh` の出力と、端末上の長押し診断オーバーレイで行う。

## Commit & Pull Request Guidelines

コミットメッセージは日本語で `[種類] 変更内容の説明`。種類は `feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore` / `perf` / `build` / `ci`。

PR には変更の目的、変更したパス、実機での確認手順を書く。UI が変わる場合は昼夜それぞれのスクリーンショットを添える。

## Security & Configuration Tips

API キー、Amazon アカウント情報、宅内の IP やトークンをコミットしない。端末固有の値は `local.properties` か ignore 済みファイルに置き、必要な変数は README に列挙する。
