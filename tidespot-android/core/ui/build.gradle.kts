plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.tidespot.core.ui"
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
        unitTests {
            isReturnDefaultValues = true
            // C-1（CHG-19/A-14）：Robolectric 需要资源访问
            isIncludeAndroidResources = true
        }
    }
}

// Windows 非 ASCII 项目路径下，test worker 需显式 UTF-8 编码才能正确加载测试类（与 :feature:map 一致）
// C-1（CHG-19）：Robolectric 运行期自下载 android-all jar 走独立 JVM——把 daemon 上的代理/信任库
// 系统属性透传给 test worker（无代理环境为 no-op，不影响 CI/沙箱）。
tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    listOf(
        "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
        "javax.net.ssl.trustStoreType",
    ).forEach { key -> System.getProperty(key)?.let { systemProperty(key, it) } }
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

dependencies {
    // CONTRACT M4 子契约：:core:ui 仅依赖 :core:domain（依赖方向 feature:* → core:*，core:ui 不依赖 :core:data）
    implementation(project(":core:domain"))

    // Compose（BOM 统一版本，Material3，禁 XML 布局；图表为 Compose Canvas 自绘，禁第三方图表库 §7.1）
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(kotlin("test"))
    // C-1（CHG-19/A-14）：Robolectric + Compose ui-test 组装层冒烟锁（补「组装层零覆盖」盲区）
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
