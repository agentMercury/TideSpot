package com.tidespot.core.ui.strings

/**
 * 用户可见文案唯一入口（FO-01，M6 批次 2 部分落地；CHG-16⑳）：
 * 本期只迁「Tab 标题 / 免责 / 徽标」核心串并保证零行为漂移
 * （[com.tidespot.core.ui.titles.WaterBodyTabTitles] 与 [com.tidespot.core.ui.badge.Kind]
 * 的既有常量/标签改为引用本对象，值一字不改，既有测试断言保持通过）。
 *
 * - 全量文案外移登记 v1.1；
 * - 否决 reason 文案由 :core:domain VetoEvaluator 产出（冻结模块不可改）；消费侧禁 parse 文案，
 *   结构化兜底走 [com.tidespot.core.domain.model.VetoCode]（Models.kt 冻结契约）。
 */
object TideSpotStrings {

    // ────────────── Tab 标题（WaterBodyTabTitles 串源；顺序 §8.1：风浪|天气|钓鱼指数|水质|潮流） ──────────────

    const val TAB_WAVE = "风浪"
    const val TAB_WEATHER = "天气"
    const val TAB_SCORE = "钓鱼指数"
    const val TAB_WATER_QUALITY = "水质"
    const val TAB_TIDE = "潮流"

    // F-1.6 内陆降级 / F-1.7 陆地点标注（与 WaterBodyTabTitles 逐字一致）
    const val TAB_WAVE_INLAND = "湖面风与水温"
    const val TAB_TIDE_INLAND = "水位（无潮汐）"
    const val TAB_WAVE_LAND = "风浪（估算）"
    const val TAB_WATER_QUALITY_LAND = "水质（估算）"
    const val TAB_TIDE_LAND = "潮流（不可用）"

    const val NOTICE_LAND = "陆地点：水质与潮流为估算或不可用，仅供参考"
    const val NOTICE_UNKNOWN = "水体类型待判定：数据源恢复后自动更新"

    // ────────────── 免责声明（§9.6 / D11：共 5 条，第 5 条为 M6 新增「潮汐校正」） ──────────────

    const val DISCLAIMER_SHEET_TITLE = "免责声明"

    /** 1/5 潮位与潮流（原 WAVE/TIDE Tab 免责原文）。 */
    const val DISCLAIMER_WAVE_TIDE =
        "潮位与潮流来自 ~8 km 全球模型，官方声明不适用于近岸航行；本 App 不替代航路指南与官方潮汐表。"

    /** 2/5 天气（原 WEATHER Tab 免责原文）。 */
    const val DISCLAIMER_WEATHER =
        "雷暴、大风、大浪判定仅供参考。水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。"

    /** 3/5 钓鱼指数（原 SCORE Tab 免责原文）。 */
    const val DISCLAIMER_SCORE =
        "指数基于气象与天文因子的经验加权估算，不构成渔获保证。"

    /** 4/5 水质（原 WATER_QUALITY Tab 免责原文）。 */
    const val DISCLAIMER_WATER_QUALITY =
        "中国境内多数点位水质为模型估算值，非实测，不作为用水、饮用或接触安全依据。"

    /** 5/5 潮汐校正（D11 新增）：现场校潮属用户自记经验修正，非官方数据。 */
    const val DISCLAIMER_TIDE_CORRECTION =
        "「现场校潮」为用户自记高潮观测对模型滞后的经验修正（lag 平移），仅用于相对时刻参考；" +
            "校正不改变数据来源与精度，仍非官方潮汐表。"

    /** 免责全文（Sheet 渲染顺序 1..5；D11 锁：恒 5 条）。 */
    val DISCLAIMER_ITEMS: List<String> = listOf(
        DISCLAIMER_WAVE_TIDE,
        DISCLAIMER_WEATHER,
        DISCLAIMER_SCORE,
        DISCLAIMER_WATER_QUALITY,
        DISCLAIMER_TIDE_CORRECTION,
    )

    /** 常驻免责入口按钮文案（D11）。 */
    const val DISCLAIMER_ENTRY = "免责声明"

    // ────────────── 置信徽标（§8.2-4；ConfidenceBadge.Kind 标签串源） ──────────────

    const val BADGE_MEASURED = "实测"
    const val BADGE_SATELLITE = "卫星"
    const val BADGE_MODEL = "模型"
    const val BADGE_ESTIMATED = "估算"
    const val BADGE_UNCALIBRATED = "未校正"
    const val BADGE_LOW_CONFIDENCE = "低置信"

    /** TalkBack 读法模板（N-7）。 */
    fun confidenceDescription(label: String): String = "数据置信：$label"

    // ────────────── F-3.7 数据来源署名（M6 批次 3 部分口径；:app 详情容器透出） ──────────────

    /** 天气/海洋模型静态署名（Open-Meteo 底层模型；DTO 级模型名/网格分辨率透出登记 v1.1）。 */
    const val MODEL_ATTRIBUTION =
        "天气 · Open-Meteo（ICON / ECMWF 模型） · 海洋 · Open-Meteo Marine / Copernicus Marine"

    // ────────────── 水体类型徽标与收藏信息卡片（OPT-V11-15 / P2 CHG-37） ──────────────

    /** 水体类型徽标文案（[com.tidespot.core.domain.model.WaterBodyType] 展示名）。 */
    const val WATER_BODY_OCEAN = "海域"
    const val WATER_BODY_NEARSHORE = "近岸"
    const val WATER_BODY_INLAND = "内陆水域"
    const val WATER_BODY_LAND = "陆地"
    const val WATER_BODY_UNKNOWN = "待判定"

    /** 收藏信息卡片（OPT-V11-15）：仅读库，字段缺值一律 `—`（禁 0 填充，§5.0）。 */
    const val INFO_CARD_COORDINATE_PREFIX = "坐标"
    const val INFO_CARD_ELEVATION_PREFIX = "海拔"
    const val INFO_CARD_LAST_FETCHED_PREFIX = "最近拉取"
    const val INFO_CARD_GROUP_PREFIX = "分组"
    const val INFO_CARD_WATER_BODY_PREFIX = "水体"

    /** 坐标精度来源说明（口径：选点坐标即 WGS84 原始值，存 6 位小数；不做偏移纠偏）。 */
    const val INFO_CARD_ACCURACY_NOTE =
        "坐标为选点原始 WGS84 值，保留 6 位小数（≈0.11 m），未做偏移纠偏"
}