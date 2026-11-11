plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
    // Windows 非 ASCII 项目路径下，test worker 需显式 UTF-8 编码才能正确加载测试类（与 :core:ui 一致）
    jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
    }
}
