pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "TideSpot"
include(":core:domain", ":core:data")
// M3 起追加: ":feature:map", ":feature:detail", ":feature:favorites", ":app"
include(":feature:map")
// M4 批次 B（CONTRACT「M4 子契约」）：UI Agent 仅追加 include 行，不动其他内容
include(":core:ui")
include(":feature:detail")
// M5 批次 A（CONTRACT「M5 子契约」）：UI Agent 仅追加 include 行，不动其他内容
include(":feature:favorites")
// M6 批次 2（CONTRACT「M6 子契约」CHG-16⑱）：UI Agent 仅追加 include 行，不动其他内容
include(":feature:settings")

include(":app")  // M6 批次 3（CONTRACT「M6 子契约」CHG-16 批次 3）：:app 组装
