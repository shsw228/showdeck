# ShowDeck

Android 化した Amazon Echo Show 5 第2世代（コードネーム `cronos`）を、常駐ダッシュボードに変えるためのアプリ。

大きな時計が主役。天気・予定・タイマーは順に足していく。

## 設計の前提

| 項目 | 内容 |
|---|---|
| 端末 | Echo Show 5 2nd gen (2021) / `cronos` |
| 画面 | 960×480、密度 195（≒ 788×394 dp）、5.5 インチ |
| OS | LineageOS 18.1 / Android 11 (API 30) `userdebug` `test-keys` |
| SoC / ABI | MediaTek MT8163 / **armeabi-v7a**（ROM が 32bit） |
| RAM | 973MB（実測で空き 30MB 前後、swap 使用中）|
| CPU | 4 コアだがアイドル時は 1 コアのみオンライン |
| バックライト | `/sys/class/leds/lcd-backlight/brightness`（0..255、system:system）|
| SELinux | **Permissive** |
| 電源 | 常時給電 |
| 用途 | 単一用途・据え置き・**触らない時間が大半** |

すべて `./scripts/check-device.sh` による実測値。

「非力・横長・遠目・触らない」。この 4 つから設計判断がほぼ決まる。

- **触らない** → 設定は端末上でやらせない。adb と、後に入れる Web 設定画面で済ませる
- **遠目** → 情報を詰め込まない。5.5 インチで読めるのは 3〜4 項目まで
- **非力** → material3 を入れない、WebView を使わない、毎秒の再コンポーズを避ける
- **常時点灯** → 暗い部屋で眩しくないことが機能要件

### プラットフォーム署名で system UID を取る

この ROM は `ro.build.tags = test-keys`、つまり **AOSP が公開しているテスト鍵**で署名されている。照合済み:

```
端末 /system/framework/framework-res.apk : c8a2e9bc…51192ab8
AOSP platform.x509.pem                   : c8a2e9bc…51192ab8
```

同じ鍵でアプリを署名し `android:sharedUserId="android.uid.system"` を付けることで、**Magisk も TWRP も使わずに** system UID として動作する。実機で確認済み:

| | |
|---|---|
| `isSystemUid` | ✓ |
| `WRITE_SECURE_SETTINGS` | ✓（signature で自動付与、`pm grant` 不要）|
| `WRITE_SETTINGS` | ✓ |
| バックライト直書き | ✓（root 不要）|
| `root (su)` | ✗（**不要になった**）|

root が要る作業は、いまのところ Device Owner の解除だけ。それも `adb root` で足りる（`userdebug` ビルドのため）。

### ストア配布しない前提で取っている選択

- `targetSdk = 28`。Scoped Storage、フォアグラウンドサービスの type 必須化、ランタイム権限の再確認といった Android 10 以降の制約を丸ごと回避する。従う利点が一つもない
- Device Owner としてステータスバー無効化とホームアプリ固定を行う
- `abiFilters = armeabi-v7a`、R8、ロケール絞りで release APK を 1.2MB に抑える

### 輝度制御の実測

暗室で眩しくないことが機能要件なので、どこまで落とせるかを実機で測った。

| 方法 | 到達できる raw | 判定 |
|---|---|---|
| ウィンドウ輝度 `screenBrightness = 0.01` | 255 のまま（**無視される**）| 使えない |
| `Settings.System.SCREEN_BRIGHTNESS = 1` | 10（OS の下限で頭打ち）| 不十分 |
| **sysfs 直書き** | **1** | 採用 |

sysfs 直書きは、何も起きなければ保持されるが、画面まわりのイベントで DisplayPowerController に 255 を書き戻される。そのため 15 秒ごとに読んで、違っていたら押し戻している。

## セットアップ

### 1. Mac 側

このリポジトリは Android 開発環境を前提にしている。

```sh
brew install openjdk@17                      # cask の temurin は sudo を要求するので formula を使う
brew install --cask android-platform-tools   # adb
brew install --cask android-commandlinetools # sdkmanager
brew install --cask android-studio

export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export ANDROID_HOME="$HOME/Library/Android/sdk"
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-37.1" "build-tools;37.0.0"
```

`local.properties` に `sdk.dir` を書けば `./gradlew` が通る。Gradle は Wrapper（9.7.0）で固定しているので、システムの Gradle バージョンは問わない。

#### ツールチェーンの固定について

- **AGP 9.3.1 / Gradle 9.7.0**。AGP 8.x は Gradle 9.6 以降が弾き、最新の androidx（core-ktx 1.19 / lifecycle 2.11）も AGP 9.1 以上を要求するため 9 系に乗せている
- AGP 9 は Kotlin を内蔵しているので `org.jetbrains.kotlin.android` プラグインは適用しない
- `compileSdk = 37` / `targetSdk = 28`。この差は意図的

### 2. 端末の素性を確認

```sh
./scripts/check-device.sh
```

SoC・RAM・画面密度・ABI・`su` の有無・バックライトの sysfs パスが出る。
`DeckConfig` と `Backlight.NODE` はこの出力を見て詰める。何も変更しないので、いつ実行しても安全。

### 3. プラットフォーム署名鍵を用意

```sh
./scripts/fetch-platform-keys.sh
```

AOSP から公開テスト鍵を取得し、フィンガープリントを端末の署名と照合したうえで `keys/platform.p12` を作る。鍵はリポジトリに含めていない（秘密ではないが、出所を明示するため都度取得する）。

**これを実行しないとビルドした APK はインストールできない。** `sharedUserId=android.uid.system` の署名検証で弾かれる。

### 4. インストールとブートストラップ

```sh
./gradlew installRelease
./scripts/setup-device.sh
```

プラットフォーム署名が効いていれば、権限はすべて署名だけで通る。**adb で残るのは Device Owner 化 1 行だけ**で、これも仕様上アプリからは実行できない。

```sh
adb shell dpm set-device-owner com.shsw228.showdeck/.admin.AdminReceiver
```

常時点灯・スリープ無効・隠し API ポリシー・没入モード確認の抑止は `DeviceSetup.apply()` が毎起動で適用し直す。OTA や設定リセットで飛んでも自動復帰する。

Device Owner 化は端末にアカウントが 1 つでも登録されていると失敗する。失敗してもアプリは動く（ステータスバー無効化とホーム固定が効かなくなるだけ）。

### 戻す

```sh
./scripts/revert-device.sh
```

**Device Owner が付いているとアンインストールできない**（`DELETE_FAILED_INTERNAL_ERROR`）。`dpm remove-active-admin` も `Attempt to remove non-test admin` で失敗する。実機で通った唯一の手順:

```sh
adb root
adb shell rm -f /data/system/device_owner_2.xml /data/system/device_policies.xml
adb reboot
adb wait-for-device && adb uninstall com.shsw228.showdeck
```

`cronos` は BROM USBDL が塞がれていて、詰むと復旧できない。ソフト側の変更は必ず往復できる状態を保つこと。

## 操作

| 操作 | 動作 |
|---|---|
| 天気をタップ | 5 日間の予報を開く |
| タップ | 消灯中なら一時的に画面を戻す / 発報中なら止める |
| 長押し | 診断オーバーレイ（権限の状態、設定画面の URL と資格情報） |

設定は端末上では行わない。**`http://<端末IP>:8080` を PC やスマホのブラウザで開く。**
タイマーの開始、夜間モードの時間帯、バックライトの値、消灯の設定、天気の地点と API キー、
アラーム時刻、logcat の閲覧ができる。

**Basic 認証を要求する。** ユーザー名は `showdeck`、パスワードは初回起動時に端末が生成し、
**画面を長押しすると診断オーバーレイに出る**（`xxxx-xxxx-xxxx` 形式）。ブラウザが記憶するので
入力は一度きり。パスワードは他の秘密と同じく Keystore の鍵で暗号化して保存する。

宅内 LAN からしか届かない前提は変えていない。防ぎたいのは「同じネットワークにいる
別の誰かや別の機器がうっかり触ること」で、このアプリは system UID で動いているぶん
無認証で置く危険が通常アプリより大きい。

## 3 つの表示モード

| モード | バックライト | 表示 |
|---|---|---|
| `DAY` | 設定値（既定 180）| 時計＋情報レール |
| `NIGHT` | 設定値（既定 1）| 赤単色・時計のみ |
| `BLACKOUT` | 0 | 消灯 |

消灯は画面を切っているのではなく**バックライトを 0 にしているだけ**で、Android 側は
`Awake` のまま。そのためタッチが即座に届き、復帰にラグが無い。`goToSleep` を使うと
touchscreen ごと止まってタッチで戻せなくなる。

復帰のトリガはタップと、部屋の明かりの**立ち上がり**。閾値を超えているかで判定すると
明るい間じゅう復帰し続けて消灯に入れない（実機で 15 秒周期の往復を観測した）。

## 天気

**OpenWeatherMap** を使う。当初は気象庁の予報 JSON を使っていたが、乗り換えた。

理由は**実況が取れない**こと。気象庁の予報 JSON は発表時刻を過ぎた当日の枠が実況値で
埋まり、最高と最低が同じ数字（実機で 29°/29°）になる。回避策として明日の予報を出して
みたが、時計に明日の気温を出しても意味がない。**時計に載せて一番役に立つのは今の気温**で、
それが取れないのは致命的だった。

無料枠で叩ける 2 つを使う。

```
/data/2.5/weather   現在の気温と天気
/data/2.5/forecast  3 時間ごと 5 日分
```

### 表示

| 位置 | 内容 |
|---|---|
| 主 | 現在気温 |
| 副 | `↑最高 ↓最低`（これから 24 時間） |
| 脚注 | 地名・天気の文言・降水確率 |

最高／最低を「今日」ではなく**これから 24 時間**にしているのは、当日で切ると日が暮れる
ほど残りの区間が減って最高と最低が潰れるため。矢印だけ添えて日付の説明を省いている。

**天気をタップすると 5 日間の予報**が全画面で出る。横 5 列に並べるのは、960×480 の
横長画面では縦にリストを積むより一目で比べられるから。列数は 5 に固定している
（日によって 5 列と 6 列が入れ替わると、1 列の幅も文字の大きさも変わって見え方が安定しない）。

### 実装上の落とし穴

- **応答の `dt_txt` は UTC。** そのまま日付として使うと JST の 15 時が「06:00」として
  前日に寄る。必ず `dt`（epoch）から現地時刻へ直す
- **末尾の日は区間が足りない。** 半日ぶんしか無い日は最高気温も降水確率も過小に出る
  （実機で最終日が降水 0% になった）。18 時間ぶんに満たない日は使わない
- 日ごとのアイコンは**その日の 12 時に一番近い区間**のもの。最も荒れた天気を選ぶと
  一瞬の通り雨で一日が雨になり、平均すると特徴が消える

アイコンは画像を持たず Canvas で描く。ビットマップだと夜間の赤単色パレットに追従できず、
そこだけ白く浮くため。線画ではなく塗りにしているのは、円を重ねた線画の雲が実機で
団子にしか見えなかったから。夜は太陽ではなく月にする。

通信が死んだらディスクのキャッシュを読み、それも無ければ天気欄ごと畳む。
**時計だけは必ず出す。**

### API キーの持ち方

**リポジトリにも APK にも入れない。** Web 設定画面から入力し、端末内で
Android Keystore の鍵により AES-GCM で暗号化して DataStore に置く。

- 鍵は Keystore の中で生成され、外に出ない
- `DeckSettings` はまるごとログに出しているため、キーは `ApiKey` 型で包んで
  `toString()` を `****` に潰してある。Web の `/logs` から平文が読めては意味がない
- Web 設定画面には伏せ字しか出さない。空のまま保存すれば現在のキーを維持する

平文のキーは通信時にどのみち復号するので、root を取れる相手からは守れない。
ここで防いでいるのは「ディスクに平文で転がること」と「うっかりログや git に載ること」。

## タイマー・アラーム

Android 化で Alexa が消えたぶん、キッチンタイマーと目覚ましは自前で持つ。

- タイマーは Web 設定画面から「分」を入れて開始する
- アラームは毎日 1 回。同じ日に二度は鳴らない
- 発報すると全画面表示になり、端末の既定アラーム音と TTS の読み上げが出る
- **消灯中に発報しても画面を点ける。** 真っ暗なまま音だけが鳴るのを防ぐ
- 発報中の配色は昼夜のパレットに従わない。夜中のアラームを暗い赤で出したら用をなさない

## メモリ

空きが 30MB しかない端末なので、常駐アプリの削減が効く。

| | MemAvailable |
|---|---|
| 素の状態 | 238MB |
| `launcher3` と `SystemUI` を停止して再起動 | **429MB** |

`SystemUI` は `system_server` が明示的に起動するため、`pm disable-user` しても常駐は
続く（PSS は 62MB → 24MB に減る）。完全に消すには user 0 からのアンインストールが
必要だが、ブートループの危険があり、この端末は復旧が難しいのでやらない。

## root（Magisk）について

**現時点では不要。** プラットフォーム署名で system UID が取れているため、当初 root が必要だと見ていた「OS 下限を超えた減光」は root なしで実現できている。

残る root 用途は `/system/priv-app` へ置いての `persistent` 化だけで、これは `adb root` + `remount` でも足りる可能性が高い。Magisk を入れるのは、それでも足りないと分かってからでよい。

もし入れるなら、`cronos` は TWRP が使えるので Magisk APK を `.zip` にリネームして焼き、再起動後に Magisk アプリで直接インストールする流れ。**先に TWRP バックアップを取ること。** この機種は BROM USBDL が無く、失敗すると戻せない。

参考: [XDA: [UNLOCK][ROOT][TWRP][UNBRICK] Echo Show 5 2nd Gen (cronos)](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-5-2nd-gen-2021-cronos.4772596/)

## ロードマップ

- [x] **1. 土台** — ランチャー化、大時計、日付、診断オーバーレイ、端末セットアップ
  - release APK 1.4MB、ユニットテスト 28 件、実機で動作確認済み
- [x] **2a. 夜間モード（減光）** — プラットフォーム署名 + sysfs 直書きで raw 1 まで到達
- [x] **2b. 消灯と自動復帰** — バックライト 0、タップと照度の立ち上がりで復帰
- [x] **3. 設定層** — DataStore による永続化、端末内 HTTP サーバ（WebCtl）で設定とログ
  - [ ] 自己更新（APK の投入）はまだ
- [x] **4. 天気** — 気象庁 JSON、右カラムの実装
- [x] **5. タイマー / アラーム / TTS** — Alexa が消えた穴を埋める
- [ ] **6. 連携** — ICS カレンダー、ゴミの日、フォト、MQTT / Home Assistant

## 構成

```
app/src/main/java/com/shsw228/showdeck/
├── MainActivity.kt          ランチャー本体。没入モード・輝度・長押し診断
├── DeckConfig.kt            据え置き前提の固定設定（3 で DataStore へ移す）
├── admin/AdminReceiver.kt   Device Owner。ステータスバー無効化とホーム固定
├── system/
│   ├── Clock.kt             秒境界に同期する時刻 State
│   ├── DeviceSetup.kt       端末設定をアプリ自身で適用する層
│   ├── DeviceInfo.kt        LAN IP 取得と system UID 判定
│   ├── Backlight.kt         sysfs 直書きと、書き戻しへの押し戻し
│   ├── LightSensor.kt       照度センサ（消灯の復帰判定に使う）
│   ├── Secrets.kt           Keystore による API キーの暗号化
│   └── Su.kt                root シェルの薄いラッパ（現状は未使用の保険）
├── DeckMode.kt              DAY / NIGHT / BLACKOUT の判定（純粋関数・テスト対象）
├── settings/
│   ├── DeckSettings.kt      実行時に変えられる設定
│   └── SettingsStore.kt     DataStore による永続化
├── weather/
│   ├── Weather.kt           OpenWeatherMap の解析（純粋関数・テスト対象）
│   └── WeatherRepository.kt 取得とディスクキャッシュ
├── alert/
│   ├── AlertCenter.kt       タイマーとアラームの発報判定（テスト対象）
│   └── AlertPlayer.kt       アラーム音と TTS
├── web/WebCtlServer.kt      端末内 HTTP サーバ（設定・状態・ログ・タイマー）
└── ui/
    ├── ClockScreen.kt       時計と情報レール
    ├── WeatherIcon.kt       Canvas で描く天気アイコン
    ├── ForecastOverlay.kt   5 日間の予報
    ├── AlertOverlay.kt      発報中の全画面表示
    ├── DiagnosticsOverlay.kt
    └── theme/DeckPalette.kt モードごとの配色
```
