plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // 1GB 機に置くので APK とメモリを削れるだけ削る。
            // R8 を通さないと Compose だけで 18MB 前後になる。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 自前配布なので debug 鍵のまま release を焼ける状態にしておく。
            // 署名を変えると Device Owner の再設定が必要になるため、鍵は固定して運用する。
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // androidx が約 90 ロケール分のリソースを持ち込む。この端末では無駄。
        localeFilters += listOf("ja", "en")
    }

    // 端末の ABI が確定したら絞る。check-device.sh の ro.product.cpu.abi を見て決める。
    // splits { abi { isEnable = true; reset(); include("arm64-v8a") } }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // Play ストアの targetSdk 要件を課す検査。自前配布しかしないので該当しない。
        // targetSdk を 28 に留めるのは意図的な設計判断（defaultConfig のコメント参照）。
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // material3 は意図的に入れていない。1GB 機なので依存とメモリを削り、
    // 必要な描画は foundation だけで組む。

    testImplementation(libs.junit)
}
