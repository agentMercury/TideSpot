plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tidespot.core.data"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        // A-1（CHG-18/BLK-04）：Room 真机仪器测试 runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // A-1（CHG-18/BLK-04）：MigrationTestHelper 从 instrumentation assets 加载 schema JSON
    sourceSets.getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// Windows 非 ASCII 项目路径下，test worker 需显式 UTF-8 编码才能正确加载测试类
tasks.withType<Test>().configureEach {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.room.testing)

    // A-1（CHG-18/BLK-04）：Room 真机仪器测试（RoomDaoTest/RoomMigrationTest @Ignore 解锁）
    androidTestImplementation(kotlin("test"))
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
