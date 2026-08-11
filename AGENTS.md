# Repository Guidelines

Android 化した Echo Show 5 第2世代（`cronos`）向けの常駐ダッシュボード。設計の前提は README.md を参照。

## この端末固有の制約

作業前に必ず踏まえること。一般的な Android アプリの常識が通用しない箇所がある。

- **960×480 / 密度 195（≒ 788×394 dp）/ 5.5 インチ / 視距離 1〜3m**。寸法は「寸法と文字」の節に従う
- **RAM が少ない**。material3・WebView・大きな画像ライブラリを安易に入れない。依存を足すときは理由をコメントに残す
- **常時表示**。毎秒の再コンポーズを避ける。時刻は `State` のまま末端へ渡し、`derivedStateOf` で実際に変わったときだけ再コンポーズさせる
- **輝度は sysfs を直接持っている**。ウィンドウ輝度も `Settings.System.SCREEN_BRIGHTNESS` も実機では十分に暗くならなかった（README の実測表を参照）。DisplayPowerController が書き戻すので、定期的に押し戻す前提で組む
- **ABI は armeabi-v7a のみ**。ROM が 32bit なので arm64 のネイティブライブラリを入れない
- **暗い部屋で使う**。新しい画面を足すときは夜間パレットでの見え方を必ず確認する。例外は発報画面で、そこだけは常に明るい色を使う（夜中のアラームが読めないと用をなさない）
- **アイコンは画像を持たず Canvas で描く**。ビットマップは夜間の赤単色に追従できない。線画だと重なった弧が見えて潰れるので塗りにする
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
- **`am force-stop` は効かない。** system UID で動いているため無視される。再起動させるには `adb root` してから `kill -9 $(adb shell pidof com.shsw228.showdeck)`。ランチャーなので system_server が即座に起動し直す
- **このリポジトリのファイルを `python3` や `sed` のスクリプトで書き換えない。** 一致しなくても無言で成功するため、直したつもりで実機を測り続ける事故が繰り返し起きた（照度のエッジ検出、ログの文字列展開、診断画面の URL）。編集は必ず、当たらなければエラーになる手段で行う

## 外部データの扱い

- **通信が死んでも時計は必ず出す。** 天気などの取得に失敗したらキャッシュを読み、それも無ければその欄ごと畳む。起動を止めない
- 気象庁 JSON は当日と週間で構造が違う。**添字の決め打ちをしない**。気温は時刻の枠（`00:00`=最低 / `09:00`=最高）で突き合わせる
- **外部データは「取れた」だけでは信用しない。** 気象庁は発表時刻を過ぎた当日の枠を実況値で埋めるため、最高と最低が同じ値になる。値が意味を持つかを検査してから画面に出す
- 解析は副作用のない関数に切り出し、実際の応答を `app/src/test/resources/` に置いてテストする
- **Android の `org.json` はユニットテストではスタブ**で、呼ぶと例外になる。`testImplementation(libs.json)` で実装を載せてある
- **時刻を含む応答は必ず epoch から現地時刻へ直す。** OpenWeatherMap の `dt_txt` は UTC で、そのまま使うと JST の夕方が前日に寄る

## 秘密の扱い

- API キーの類は**リポジトリにも APK にも入れない。** Web 設定画面から入れ、`Secrets` で暗号化して DataStore に置く
- 秘密は `ApiKey` のような `toString()` を潰した型で包む。`DeckSettings` はまるごとログに出しており、素の `String` で持つと `/logs` から平文が読める
- 設定画面に既存の値を書き戻さない。伏せ字を出し、空なら現状維持にする
- **Web 設定画面の経路を増やしたら認証を通すこと。** 認証は `serve()` の先頭で一括して行っている。パスごとに書く形にしない
- **`hidden_api_policy` を緩めない。** 端末上の全アプリの制限まで下がる。ShowDeck はリフレクションを使っていない

## 寸法と文字

**Android Auto の指針に従う。** `ui/theme/DeckMetrics.kt` に定数がある。

- 押せるものは **76dp 以上**。`DeckMetrics.TouchTarget` を使う
- 主要な情報の文字は **24sp 以上**（`DeckType.Body`）。それ未満は `DeckType.Caption` で補足だけ
- 4dp グリッド。`DeckMetrics.Gap*` を使い、生の `dp` を散らさない
- 太字を使わない。medium も控えめに
- **「画面高の何%」で寸法を決めない。** 密度が読めなかった頃の名残で、実測が出た以上は基準を割る（実際に 53dp / 20sp になっていた）。例外は時計だけ

## UI を変えたら

**`./gradlew :app:updateDebugScreenshotTest` で参照画像を撮り直し、差分を目で見る。**
960×480・密度 195 という特殊な画面で、レイアウト崩れは実機でしか起きなかった。

- Preview は `app/src/screenshotTest/` に置く。`@PreviewTest` と `device = DEVICE_SPEC` を付ける
- `dpi=195` を外さない。既定の 160dpi だと 1dp = 1px になり、実機と字詰まりが変わって意味がない
- 参照画像は `app/src/screenshotTestDebug/reference/` にコミットする
- 検証は `./gradlew :app:validateDebugScreenshotTest`

## アーキテクチャ

Android の推奨アーキテクチャに沿っている。UI 層（Compose + `DeckViewModel`）とデータ層
（`SettingsStore` / `WeatherRepository`）の 2 層。

- **状態と処理は `DeckViewModel` に置く。** Activity は UI のホストと Context 依存の生成だけ。以前は Activity に `LaunchedEffect` が 11 個並んでいた
- **`DeckViewModel` に `Context` を持たせない。** Context が要るものは Activity 側で作って渡す。実機なしで組み立てられる状態を保つ
- **現在時刻は `uiState` に混ぜない。** 毎秒変わるものを入れると、状態を読む階層が丸ごと毎秒再コンポーズされる。`DeckViewModel.now` という別の流れにしてある
- **グローバルな可変シングルトンを作らない。** 状態の持ち主が画面と二重になる（`AlertCenter` でやって作り直した）
- **Hilt は入れていない。** 依存は ViewModel の 5 つだけで、注入器を足す利点より 1GB 機での依存とビルド時間の増加が勝る。増えてきたら再考する
- ドメイン層も置いていない。公式でも optional で、ViewModel をまたぐ業務ロジックが無い

## 設定の書き込み

- **`SettingsStore.update()` に画面側の `DeckSettings` をそのまま渡すときは、DataStore の最初の値が届いているか確かめる。** 届く前は既定値なので、丸ごと書き戻すと API キーも地点も既定に潰れる（実際に API キーを消した）
- 一部のキーだけを変えたいときは `ensureWebPassword()` のように DataStore の編集トランザクション内で当該キーだけを触る

## Build, Test, and Development Commands

```sh
./scripts/fetch-platform-keys.sh   # 初回のみ。これが無いとインストールできない
./scripts/fetch-android-skills.sh  # Google の Android skills を .claude/skills/ に取得
./gradlew assembleRelease      # ビルド
./gradlew :app:updateDebugScreenshotTest    # UI を変えたら参照画像を撮り直す
./gradlew :app:validateDebugScreenshotTest  # 参照画像との差分を検証
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

ロジックは `app/src/test/` にプレーンなユニットテストを置く。**時間に依存する判定を実機で確かめない。** 日付をまたぐ夜間判定やポモドーロの「4 回目の作業の後だけ長い休憩」は、実機で試すと数時間かかる。純粋関数に切り出して境界を固める。

UI は `app/src/screenshotTest/` の Preview で撮る（「UI を変えたら」の節を参照）。実機確認は `./scripts/check-device.sh` の出力と、端末の長押しで出る操作パネルで行う。

## Commit & Pull Request Guidelines

コミットメッセージは日本語で `[種類] 変更内容の説明`。種類は `feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore` / `perf` / `build` / `ci`。

PR には変更の目的、変更したパス、実機での確認手順を書く。UI が変わる場合は昼夜それぞれのスクリーンショットを添える。

## Security & Configuration Tips

API キー、Amazon アカウント情報、宅内の IP やトークンをコミットしない。端末固有の値は `local.properties` か ignore 済みファイルに置き、必要な変数は README に列挙する。
