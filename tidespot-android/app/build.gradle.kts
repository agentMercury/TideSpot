import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ─────────────────────────────────────────────────────────────────────────────
// 签名与 Copernicus 凭据（M6 批次 3 / CHG-11 单机口径）：
// 构建期从本机 local-secrets.properties 读取（文件不入库、不打包进 APK；
// Copernicus 凭据经 BuildConfig 注入，缺失时空串 → WaterBodyClassifier 探针
// 禁用 → UNKNOWN 降级（CHG-09），禁硬编码）。
// local-secrets.properties 或 keystore/tidespot.jks 缺失 → release 兜底 debug
// 签名并打告警日志（不阻塞 assembleDebug）。
// ─────────────────────────────────────────────────────────────────────────────
val secretsFile = rootProject.file("local-secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
val keystoreFile = rootProject.file(secrets.getProperty("signing.storeFile") ?: "keystore/tidespot.jks")
val signingReady = secretsFile.exists() && keystoreFile.exists() &&
    secrets.getProperty("signing.storePassword") != null &&
    secrets.getProperty("signing.keyAlias") != null
if (!signingReady) {
    logger.warn(
        "TideSpot: local-secrets.properties 或 keystore/tidespot.jks 缺失，" +
            "release 构建回退 debug 签名（仅本机调试用，禁对外分发）"
    )
}

android {
    namespace = "com.tidespot.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tidespot.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 19
        versionName = "1.15.14"

        // FO-25：显式声明 targetSdk，消除 AGP 缺省推断告警
        // CHG-25（P0-1）：Copernicus 凭据**构建期注入已移除**——APK 内零凭据；
        // 用户经设置页录入，运行期存 App 私有目录（FileCopernicusCredentialStore）。
        // 本文件仅保留签名配置读取（local-secrets.properties 的 signing.* 仍不入库）。
    }

    signingConfigs {
        if (signingReady) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secrets.getProperty("signing.storePassword")
                keyAlias = secrets.getProperty("signing.keyAlias")
                keyPassword = secrets.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (signingReady) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
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
        buildConfig = true
    }

    // 产物命名（客户约定）：APP 名字 + 版本号 →「潮点TideSpot-v{versionName}.apk」；
    // debug 加 -debug 后缀防同名混淆；versionName 变更时产物名自动跟随，无需改脚本
    applicationVariants.all {
        val vName = versionName
        val suffix = if (buildType.name == "debug") "-debug" else ""
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "潮点TideSpot-v$vName$suffix.apk"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Windows 非 ASCII 项目路径下，test worker 需显式 UTF-8 编码（与 :core:* / :feature:* 一致）
tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 产物分发（客户约定 / CHG-21）：每次编译完 APK 自动复制一份到 E:\Trans
// 仅复制 release 交付物（debug 为本机调试件，不入分发目录）；
// 目录不存在时 Copy 任务自动创建；finalizedBy = assemble 完成后自动执行，
// 不得用 dependsOn（否则 assembleRelease 会连带触发 debug 全量构建）
// ─────────────────────────────────────────────────────────────────────────────
val transDir = "E:\\Trans"
tasks.register<Copy>("copyApkToTrans") {
    from(layout.buildDirectory.dir("outputs/apk/release"))
    include("*.apk")
    into(transDir)
    doLast {
        logger.lifecycle("TideSpot: release APK 已复制到 $transDir")
    }
}
listOf("assembleDebug", "assembleRelease").forEach { name ->
    tasks.matching { it.name == name }.configureEach {
        finalizedBy("copyApkToTrans")
    }
}

dependencies {
    // CONTRACT 依赖方向：app → feature:* / core:*（禁反向）
    implementation(project(":core:ui"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":feature:map"))
    implementation(project(":feature:detail"))
    implementation(project(":feature:favorites"))
    implementation(project(":feature:settings"))

    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // Room：databaseBuilder 运行时入口（DAO 实现由 :core:data KSP 生成，:app 无需 ksp）
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    testImplementation(kotlin("test"))
}
