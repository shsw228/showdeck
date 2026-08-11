# Repository Guidelines

Android 化した Echo Show 5 第2世代（`cronos`）向けの常駐ダッシュボード。設計の前提は README.md を参照。

## この端末固有の制約

作業前に必ず踏まえること。一般的な Android アプリの常識が通用しない箇所がある。

- **960×480 / 密度 195（≒ 788×394 dp）/ 5.5 インチ / 視距離 1〜3m**。寸法は「寸法と文字」の節に従う
- **RAM が少ない**。WebView や大きな画像ライブラリを安易に入れない。依存を足すときは理由をコメントに残す。material3 は入れてある（自前で ripple や Switch を作るほうが損だった）
- **常時表示**。毎秒の再コンポーズを避ける。時刻は `State` のまま末端へ渡し、`derivedStateOf` で実際に変わったときだけ再コンポーズさせる
- **輝度は sysfs を直接持っている**。ウィンドウ輝度も `Settings.System.SCREEN_BRIGHTNESS` も実機では十分に暗くならなかった（README の実測表を参照）。DisplayPowerController が書き戻すので、定期的に押し戻す前提で組む
- **ABI は armeabi-v7a のみ**。ROM が 32bit なので arm64 のネイティブライブラリを入れない
- **暗い部屋で使う**。新しい画面を足すときは夜間パレットでの見え方を必ず確認する。例外は発報画面で、そこだけは常に明るい色を使う（夜中のアラームが読めないと用をなさない）
- **アイコンは `material-icons-extended` から借りる**。自前で描かない
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
- **Web 設定画面に認証は無い。** 宅内 LAN 前提。system UID で動くぶん触れる範囲が広いので、外に出す段階になったら戻す
- **`/api/` で状態を変える経路は POST に限る。** mDNS を巡回する機器やブラウザの先読みでタイマーが動き出す
- **`hidden_api_policy` を緩めない。** 端末上の全アプリの制限まで下がる。ShowDeck はリフレクションを使っていない

## 寸法と文字

`ui/theme/DeckMetrics.kt` と `DeckType.kt` に定数がある。

- 押せるものは **44dp 以上**。`DeckMetrics.ButtonHeight` / `ButtonHeightSm` を使う
- 4dp グリッド。`DeckMetrics.Space1..6` を使い、生の `dp` を散らさない
- 文字は役割で持つ（`Display` / `Numeral` / `Body` / `Meta`）。段を増やす前に既存のどれとも違って見えるか確かめる
- 太字を使わない。medium も控えめに
- **「画面高の何%」で寸法を決めない。** 実測（密度 195 / 788×394dp）が出ている
- **収まり方を自分で計算しない。レイアウトにやらせる。** 時計を `weight` の箱に入れたせいで箱幅と文字幅の差分が生まれ、それを埋めるために「係数を決め打ち」→「実機のスクリーンショットを測って係数を出す」→「`TextMeasurer` で測る」と三段階も回り道した。正解は箱を作らないことだった。**数値を合わせる作業が始まったら、まず構造を疑う**
- **溢れたら文字を小さくするのではなく、出す情報を減らす。** 入る量はその枠の大きさで決まる。どの並べ方でも同じ中身を出そうとするのが間違い（天気タイルに `compact` を足したのがこれ）
- **デザインから写すのは構造と階層であって寸法ではない。** `claude.ai/design` のプロジェクトは 960×480 の**画素**で描かれている。dp キャンバスは 788×394 なので、そのまま dp にすると溢れる。4dp グリッドに載せ直し、見分けのつかない段は畳む（数字の 7 段 → 3 段）
- **プラットフォームが持っている部品を自前で作らない。** `Card` / `Button` / `Switch` / `SegmentedButton` / `LinearProgressIndicator` / `CircularProgressIndicator` / `Icon` / `Text` を使う。押下の alpha や時間を定数で置いた時期があったが、端末のアニメーション設定やモーション低減を無視することになる。配色は `DeckTheme` が `DeckPalette` から `ColorScheme` を作って渡す（`*Container` まで埋めないと material の既定色が出る）
- `material-icons-extended` は旧セット。`Foggy` `PartlyCloudyDay` などの Material Symbols 名は無い

## 端末を調べるとき

**「出力が空だった」を「機能が無い」と書かない。** 空振りは、機能の不在・測り方の誤り・
別の原因で無効、のどれでも起きる。実際に「ナビバーも音量パネルもこの ROM には無い」と
断定したが、原因は `SystemUI` が `DISABLED_USER` で残っていただけだった。

- **観測と解釈を分けて書く。** 「`grep` が 0 件」と「機能が無い」は別の文
- **矛盾する観測が出たら、両方を並べて報告する。** 都合のいい方を選ばない。
  `pidof` が返るのに `pm list packages -d` に載る、は両立する
  （`SystemUI` は `system_server` が起動するので、無効でもプロセスは動く）
- **自分が変えた状態を数えておく。** 切り分け中に `am crash` や `cmd overlay` を
  打ったら、そのあとの計測は「素の状態」ではない。戻すか、再起動してから測る
- **自分のアプリが前面のまま「端末の素の挙動」を測らない。** 没入モードや
  Device Owner のポリシーが効いている
- **`adb install -r` は動いているアプリを再起動しない。** 旧プロセスが残ったまま
  新しい挙動を探して「反映されない」と判断した。入れ直したら
  `kill -9 $(adb shell pidof com.shsw228.showdeck)` してから測る
- **DataStore のキーは書き込みが起きるまで作られない。** `preferences_pb` に
  キーが無いことは「その設定が存在しない」ではない。設定を 1 回変えてから見る
- **この ROM に無い adb コマンドがある。** `cmd audio` `media volume`
  `pm clear-package-preferred-activities` `cmd package get-home-activities` は
  いずれも使えなかった。「コマンドが無い」を「機能が無い」と混同しない
- **debug と release は別の DataStore を持つ。** パッケージが違うので設定も別。
  debug の画面（初期値）を見ながら release の保存値と比べて、
  「既定と違う値が入っている」と誤解した。同じビルドで見比べる
- **debug ビルドを併存させると既定ホームの選択が消える。** debug は
  `applicationIdSuffix = ".debug"` の別パッケージで、これも HOME の候補に入る。
  候補集合が変わると Android は preferred activity を捨てるので、
  `adb install` するたびユーザーの「常時」が白紙に戻る。実機に置くのは release にする
- **無効化パッケージは `enabled=` の数値で見る。** `pm enable` は成功と表示しても
  状態が戻らないことがある（`enabled=3` = `DISABLED_USER`）。`pm default-state` を使い、
  **`package-restrictions.xml` の書き出しは遅延するので 30 秒以上待ってから再起動する。**
  直後に reboot すると変更が落ちる

## SystemUI は止めない

止めれば PSS 25MB ぶん空くが、次を失う。

- ジェスチャーの戻る（`NavigationBar` 内の `EdgeBackGestureHandler`）
- 音量パネル
- 電源長押しメニュー

この端末に物理の戻るキーは無く、設定画面に入ったら戻れなくなる。25MB のために
買う損ではない。`setStatusBarDisabled` も呼ばない（バーは没入モードで隠す）。

## UI を変えたら

**`./gradlew :app:updateDebugScreenshotTest` で参照画像を撮り直し、差分を目で見る。**
960×480・密度 195 という特殊な画面で、レイアウト崩れは実機でしか起きなかった。

- Preview は `app/src/screenshotTest/` に置く。`@PreviewTest` と `device = DEVICE_SPEC` を付ける
- **描く内容を実時刻から作らない。** 秒の線を内部で `System.currentTimeMillis()` から出していたら、撮るたびに絵が変わって検証が落ちた。時刻に依存する値は引数で受け取り、Preview から固定値を渡す
- **常時動くアニメーションを足したら CPU を測る。** アイドル時 1 コアの端末で、60fps の線 1 本が 39% を食った（README の実測表を参照）。`adb shell top -n 3 -d 5 -b -p $(adb shell pidof com.shsw228.showdeck)` で定常値を見る。起動直後は作業が混ざるので 30 秒は待つ
- 消灯中はアニメーションを止める。真っ暗な画面のために CPU を起こし続けない
- `dpi=195` を外さない。既定の 160dpi だと 1dp = 1px になり、実機と字詰まりが変わって意味がない
- 参照画像は `app/src/screenshotTestDebug/reference/` にコミットする
- 検証は `./gradlew :app:validateDebugScreenshotTest`

## アーキテクチャ

Android の推奨アーキテクチャに沿っている。UI 層（Compose + `DeckViewModel`）とデータ層
（`SettingsStore` / `WeatherRepository`）の 2 層。

- **状態と処理は `DeckViewModel` に置く。** Activity は UI のホストと Context 依存の生成だけ。以前は Activity に `LaunchedEffect` が 11 個並んでいた
- **`DeckViewModel` に `Context` を持たせない。** Context が要るものは Activity 側で作って渡す。実機なしで組み立てられる状態を保つ
- **設定に依存する取得は、設定が届いてから走らせる。** 起動時に一度スナップショットを読む形にしたら、DataStore の初回値が届く前で購読先が空になり、カレンダーが 15 分間取得されなかった
- **現在時刻は `uiState` に混ぜない。** 毎秒変わるものを入れると、状態を読む階層が丸ごと毎秒再コンポーズされる。`DeckViewModel.now` という別の流れにしてある
- **グローバルな可変シングルトンを作らない。** 状態の持ち主が画面と二重になる（`AlertCenter` でやって作り直した）
- **Hilt は入れていない。** 依存は ViewModel の 5 つだけで、注入器を足す利点より 1GB 機での依存とビルド時間の増加が勝る。増えてきたら再考する
- ドメイン層も置いていない。公式でも optional で、ViewModel をまたぐ業務ロジックが無い

## 設定の書き込み

- **`SettingsStore.update()` に画面側の `DeckSettings` をそのまま渡すときは、DataStore の最初の値が届いているか確かめる。** 届く前は既定値なので、丸ごと書き戻すと API キーも地点も既定に潰れる
- **全キー一括書き込みの経路を外部に晒さない。** Web の `/save` は checkbox を `containsKey` で判定するので、部分的な POST は消灯・アラーム・24 時間表記を黙って OFF にする
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

UI は `app/src/screenshotTest/` の Preview で撮る（「UI を変えたら」の節を参照）。**Preview は `DeckTheme` を通すこと。** 通さないと material の部品が既定色で写り、実機と違う絵を確認したことになる。実機の状態確認は `./scripts/check-device.sh` と Settings タブで行う。

## Commit & Pull Request Guidelines

コミットメッセージは日本語で `[種類] 変更内容の説明`。種類は `feat` / `fix` / `docs` / `style` / `refactor` / `test` / `chore` / `perf` / `build` / `ci`。

PR には変更の目的、変更したパス、実機での確認手順を書く。UI が変わる場合は昼夜それぞれのスクリーンショットを添える。

## Security & Configuration Tips

API キー、Amazon アカウント情報、宅内の IP やトークンをコミットしない。端末固有の値は `local.properties` か ignore 済みファイルに置き、必要な変数は README に列挙する。
