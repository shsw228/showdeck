# ShowDeck

Android 化した Amazon Echo Show 5 第2世代（コードネーム `cronos`）を、常駐ダッシュボードに変えるためのアプリ。

大きな時計が主役。天気・予定・タイマーは順に足していく。

## 設計の前提

| 項目 | 内容 |
|---|---|
| 端末 | Echo Show 5 2nd gen (2021) / `cronos` |
| 画面 | 960×480（横長 2:1）5.5 インチ |
| OS | LineageOS 18.1 (Android 11) 想定 |
| 電源 | 常時給電 |
| 用途 | 単一用途・据え置き・**触らない時間が大半** |

「非力・横長・遠目・触らない」。この 4 つから設計判断がほぼ決まる。

- **触らない** → 設定は端末上でやらせない。adb と、後に入れる Web 設定画面で済ませる
- **遠目** → 情報を詰め込まない。5.5 インチで読めるのは 3〜4 項目まで
- **非力** → material3 を入れない、WebView を使わない、毎秒の再コンポーズを避ける
- **常時点灯** → 暗い部屋で眩しくないことが機能要件

### ストア配布しない前提で取っている選択

- `targetSdk = 28`。Scoped Storage、フォアグラウンドサービスの type 必須化、ランタイム権限の再確認といった Android 10 以降の制約を丸ごと回避する。従う利点が一つもない
- `WRITE_SECURE_SETTINGS` を adb 経由で取得し、アプリが端末設定を書き換える
- Device Owner としてステータスバー無効化とホームアプリ固定を行う
- root があればバックライトを sysfs へ直接書き、OS の最低輝度を下回る

## セットアップ

### 1. Mac 側

このリポジトリは Android 開発環境を前提にしている。

```sh
brew install --cask temurin@17
brew install --cask android-platform-tools   # adb
brew install --cask android-studio           # SDK と Gradle Wrapper の生成
```

Android Studio で本リポジトリを開くと SDK と Gradle Wrapper が用意される。

### 2. 端末の素性を確認

```sh
./scripts/check-device.sh
```

SoC・RAM・画面密度・`su` の有無・バックライトの sysfs パスが出る。
`DeckConfig` と `Su.BACKLIGHT_PATHS` はこの出力を見て詰める。

### 3. インストールとブートストラップ

```sh
./gradlew installDebug
./scripts/setup-device.sh
```

**adb が絶対に必要なのは次の 2 つだけ**で、残りはアプリが起動のたびに自分で適用する。

```sh
adb shell pm grant com.shsw228.showdeck android.permission.WRITE_SECURE_SETTINGS
adb shell dpm set-device-owner com.shsw228.showdeck/.admin.AdminReceiver
```

`pm grant` は一度通れば永続する。以降 `Settings.Global` / `Settings.Secure` はアプリから直接書けるので、常時点灯・スリープ無効・隠し API ポリシーは `DeviceSetup.apply()` が毎起動で適用し直す。OTA や設定リセットで飛んでも自動復帰するため、adb 一括セットアップより堅い。

Device Owner 化は端末にアカウントが 1 つでも登録されていると失敗する。失敗してもアプリは動く（ステータスバー無効化とホーム固定が効かなくなるだけ）。

### 戻す

```sh
./scripts/revert-device.sh
```

`cronos` は BROM USBDL が塞がれていて、詰むと復旧できない。ソフト側の変更は必ず往復できる状態を保つこと。

## 操作

| 操作 | 動作 |
|---|---|
| 長押し | 診断オーバーレイ（権限・Device Owner・root の状態、IP） |

## root について

ステップ 1〜2 の大半に root は要らない。実際に必要なのは 2 つだけ。

| やりたいこと | root |
|---|---|
| `WRITE_SECURE_SETTINGS` の付与、Device Owner 化、SystemUI 停止、常時点灯 | 不要 |
| アプリのウィンドウを暗くする | 不要（権限すら不要） |
| **OS 下限を超えてバックライトを落とす** | 必要 |
| **`/system/priv-app` に置いて `persistent` 化** | 必要 |

`cronos` は TWRP が使えるので、root は Magisk APK を `.zip` にリネームして TWRP から焼き、再起動後に Magisk アプリで直接インストールを実行する流れ。

**先に TWRP バックアップを取ること。** この機種は BROM USBDL が無いため、失敗すると戻せない。

参考: [XDA: [UNLOCK][ROOT][TWRP][UNBRICK] Echo Show 5 2nd Gen (cronos)](https://xdaforums.com/t/unlock-root-twrp-unbrick-amazon-echo-show-5-2nd-gen-2021-cronos.4772596/)

## ロードマップ

- [x] **1. 土台** — ランチャー化、大時計、日付、診断オーバーレイ、端末セットアップ
- [ ] **2. 夜間モード** — バックライト直書き、深夜の画面 OFF と自動復帰
- [ ] **3. 設定層** — DataStore による永続化、端末内 HTTP サーバ（WebCtl）による設定・ログ・自己更新
- [ ] **4. 天気** — 気象庁 JSON、右カラムの実装
- [ ] **5. タイマー / アラーム / TTS** — Alexa が消えた穴を埋める
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
│   ├── DeviceInfo.kt        LAN IP 取得（権限不要）
│   └── Su.kt                root シェルの薄いラッパ
└── ui/
    ├── ClockScreen.kt       時計と情報レール
    ├── DiagnosticsOverlay.kt
    └── theme/DeckPalette.kt 昼夜の配色と輝度
```
