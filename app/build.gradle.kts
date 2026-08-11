plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // 960x480・密度 195 という特殊な画面なので、レイアウト崩れは実機でしか
    // 見つからなかった（「火曜E」と切れる、気温が 2 行に折り返す等）。
    // Preview を撮って比較することで、焼く前に気づけるようにする。
    alias(libs.plugins.screenshot)
}

val platformKeystore = rootProject.file("keys/platform.p12")
if (!platformKeystore.exists()) {
    logger.warn(
        "keys/platform.p12 がありません。./scripts/fetch-platform-keys.sh を実行してください。\n" +
            "プラットフォーム署名なしでビルドした APK は、sharedUserId=android.uid.system の\n" +
            "署名検証に失敗して端末にインストールできません。",
    )
}

android {
    namespace = "com.shsw228.showdeck"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.shsw228.showdeck"
        minSdk = 28

        // ストアに出さないので targetSdk はあえて 28 に留める。
        // Android 10 以降で追加された Scoped Storage / ランタイム権限の再確認 /
        // フォアグラウンドサービス type 必須化といった制約を丸ごと回避できる。
        // 端末は常時給電・単一用途なので、これらの制約に従う利点が一切ない。
        targetSdk = 28

        versionCode = 1
        versionName = "1.0.0"

        ndk {
            // 実機は LineageOS 18.1 の 32bit ビルド（ro.product.cpu.abi = armeabi-v7a）。
            // MT8163 は 64bit 対応だが ROM が 32bit なので arm64 は不要。
            abiFilters += "armeabi-v7a"
        }
    }

    signingConfigs {
        // この端末の LineageOS は AOSP の公開テスト鍵（ro.build.tags = test-keys）で
        // 署名されている。同じ鍵で署名することで sharedUserId=android.uid.system が
        // 通り、root なしで signature 権限とバックライト直書きが手に入る。
        // 鍵は scripts/fetch-platform-keys.sh が取得する。
        if (platformKeystore.exists()) {
            create("platform") {
                storeFile = platformKeystore
                storePassword = "android"
                keyAlias = "platform"
                keyPassword = "android"
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // 表示名は src/debug/res で差し替える。debug はホームアプリの候補として
            // release と並ぶので、同名だと「デフォルトのホームアプリ」に
            // ShowDeck が 2 つ出て、どちらか分からない。
            signingConfig = signingConfigs.findByName("platform") ?: signingConfigs.getByName("debug")
        }
        release {
            // 1GB 機に置くので APK とメモリを削れるだけ削る。
            // R8 を通さないと Compose だけで 18MB 前後になる。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 署名を変えると sharedUserId が通らなくなり、Device Owner も再設定になる。
            // 鍵は platform に固定して運用する。
            signingConfig = signingConfigs.findByName("platform") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    androidResources {
        // androidx が約 90 ロケール分のリソースを持ち込む。この端末では無駄。
        localeFilters += listOf("ja", "en")
    }

    // 端末の ABI が確定したら絞る。check-device.sh の ro.product.cpu.abi を見て決める。
    // splits { abi { isEnable = true; reset(); include("arm64-v8a") } }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // 止めている検査とその理由は app/lint.xml に書いてある。
    // 1 か所に集めて、理由付きでしか止めないため。
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 状態は ViewModel が持つ。Hilt は入れていない（README の「依存の注入」参照）。
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)

    // 端末内 HTTP サーバ。5.5 インチで設定を触らせないための要。
    // 自前実装だと HTTP パースのバグを抱え込むので、小さくて枯れた実装を借りる。
    implementation(libs.nanohttpd)

    // material3。
    //
    // 長らく「1GB 機なので依存とメモリを削る」として入れていなかったが、
    // その判断は検証されていなかった。material-icons-extended を足しても
    // APK が +0.2MB しか増えなかった時点で、大きさの根拠は崩れている。
    //
    // 入れずに済ませた代償のほうが大きかった。ripple・押下状態・タッチ領域を
    // 自前で作ることになり、実際に押下フィードバックを手で間違えた
    // （alpha と時間を定数で直指定していた）。プラットフォームに任せれば、
    // 端末のアニメーション設定やモーション低減の設定まで面倒を見てくれる。
    //
    // 配色が既定と喧嘩する懸念は、ColorScheme を DeckPalette から作って渡す
    // ことで解決している（ui/theme/DeckTheme.kt）。
    implementation(libs.androidx.compose.material3)

    // アイコンの図形。天気 9 種とナビ 5 種を Canvas で描いていたが、
    // 標準の図形で足りるものを手で持つ理由がない。未使用ぶんは R8 が落とす。
    implementation(libs.androidx.compose.icons)

    testImplementation(libs.junit)

    // Android の org.json はユニットテストではスタブで、呼ぶと例外になる。
    // 実装をテストの classpath に載せて、天気 JSON の解析を実機なしで確かめる。
    testImplementation(libs.json)

    testImplementation(libs.kotlinx.coroutines.test)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
