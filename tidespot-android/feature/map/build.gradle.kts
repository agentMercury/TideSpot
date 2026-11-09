plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tidespot.feature.map"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Windows 非 ASCII 项目路径下，test worker 需显式 UTF-8 编码才能正确加载测试类（与 :core:data 一致）
tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

dependencies {
    // M4：WaterBodyTabTitles canonical 实现迁至 :core:ui（CONTRACT「M4 子契约」），委托所需最小依赖
    implementation(project(":core:ui"))

    implementation(project(":core:domain"))
    // M3 集成：PlaceResolver/GeocoderApi 由 :core:data 的 ReverseGeocoder/GeocodingClient 接线（CONTRACT M3 子契约）
    implementation(project(":core:data"))

    // 地图：MapLibre Android v11（固定 11.x 版本，避免动态 11.+ 解析漂移；§7.1）
    implementation(libs.maplibre.android.sdk)

    // Compose（BOM 统一版本，Material3，禁 XML 布局）
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 定位双通道（F-1.2）：有 GMS 走 FusedLocationProviderClient
    implementation(libs.play.services.location)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
