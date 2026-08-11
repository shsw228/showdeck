# Repository Guidelines

Android 化した Echo Show 5 第2世代（`cronos`）向けの常駐ダッシュボード。設計の前提は README.md を参照。

## この端末固有の制約

作業前に必ず踏まえること。一般的な Android アプリの常識が通用しない箇所がある。

- **960×480 / 密度 195（≒ 788×394 dp）/ 5.5 インチ / 視距離 1〜3m**。サイズは必ず画面高からの相対で決める。固定 dp を書かない
- **RAM が少ない**。material3・WebView・大きな画像ライブラリを安易に入れない。依存を足すときは理由をコメントに残す
- **常時表示**。毎秒の再コンポーズを避ける。時刻は `State` のまま末端へ渡し、`derivedStateOf` で実際に変わったときだけ再コンポーズさせる
- **輝度は sysfs を直接持っている**。ウィンドウ輝度も `Settings.System.SCREEN_BRIGHTNESS` も実機では十分に暗くならなかった（README の実測表を参照）。DisplayPowerController が書き戻すので、定期的に押し戻す前提で組む
- **ABI は armeabi-v7a のみ**。ROM が 32bit なので arm64 のネイティブライブラリを入れない
- **暗い部屋で使う**。新しい画面を足すときは夜間パレットでの見え方を必ず確認する
- **ストア配布しない**。`targetSdk = 28` は意図的。上げると Android 10 以降の制約が復活するので、上げる理由がない限り触らない

## プラットフォーム署名が前提

この ROM は AOSP の公開テスト鍵で署名されており、アプリも同じ鍵で署名して `sharedUserId=android.uid.system` で動いている。

- **署名を変えない。** 変えるとインストールできなくなり、Device Owner の解除（`adb root` + 状態ファイル削除 + 再起動）からやり直しになる
- `keys/platform.p12` が無いとインストールできない。`./scripts/fetch-platform-keys.sh` で取得する
- system UID なので signature 権限は署名だけで通る。新しい権限が要るときは `pm grant` を書き足す前に、まず manifest への宣言だけで足りないか確かめる
- **manifest に宣言しないと権限は効かない。** `WRITE_SETTINGS` は appop を許可しても宣言が無いと `Settings.System.canWrite()` が false のままだった

## 端末を壊さないための鉄則

`cronos` は BROM USBDL が塞がれており、パーティションを壊すと**復旧できない**。

- `LK` / `Preloader` / `TZ` パーティションには絶対に触れない
- 端末設定を変更するコードを足したら、`scripts/revert-device.sh` に戻す手順も同時に足す
- root コマンドは `Su` 経由に集約する。`Runtime.exec` を各所に散らさない
- **Device Owner が付いているとアンインストールできない。** `dpm remove-active-admin` は失敗する。`adb root` で `/data/system/device_owner_2.xml` と `device_policies.xml` を消して再起動するのが唯一の手順

## Build, Test, and Development Commands

```sh
./scripts/fetch-platform-keys.sh  # 初回のみ。これが無いとインストールできない
./gradlew assembleRelease      # ビルド
./gradlew installRelease       # 実機へインストール
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
