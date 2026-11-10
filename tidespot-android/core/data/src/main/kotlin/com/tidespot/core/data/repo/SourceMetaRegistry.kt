package com.tidespot.core.data.repo

/**
 * F-3.7 数据来源元数据静态映射表（OPT-V11-04 / CHG-27）。
 *
 * 口径（SRS §5 / OPT-V11-04）：
 * - **模型名 / 网格分辨率 / 更新粒度**取本表（按数据源维度），Screen 层禁硬编码；
 * - Open-Meteo 响应不含"源端生成时刻"→ 展示以「更新粒度 + 本机拉取 HH:mm」呈现（CONFLICT-03：禁编造源更新时间）；
 * - 值为数据源技术元数据（非用户语言敏感文案；中文产量在 UI 层与「本机拉取」拼接）。
 */
object SourceMetaRegistry {

    /** 单源元数据三元组。 */
    data class SourceMeta(
        /** 模型名（多源择优时列全，如 "ECMWF WAM / GFS Wave"）。 */
        val model: String,
        /** 网格分辨率描述。 */
        val resolution: String,
        /** 更新粒度（如「逐小时」）。 */
        val granularity: String,
    )

    /**
     * 按数据源维度查表（`hourly_metric.source` 的语义维度，见 [DataSource]）。
     *
     * - WEATHER：Open-Meteo 聚合天气（Best Match 在 ECMWF IFS / GFS 间择优）→ 分辨率 9–25 km、逐小时；
     * - MARINE：Open-Meteo Marine 聚合海浪（ECMWF WAM / GFS Wave 择优）→ 9/16 km、逐小时。
     */
    fun forSource(source: DataSource): SourceMeta = when (source) {
        DataSource.WEATHER -> SourceMeta(
            model = "Open-Meteo Best Match（ECMWF IFS / GFS）",
            resolution = "9–25 km",
            granularity = "逐小时",
        )
        DataSource.MARINE -> SourceMeta(
            model = "ECMWF WAM / GFS Wave",
            resolution = "9 / 16 km",
            granularity = "逐小时",
        )
    }

    /** 来源行片段（"模型 · 分辨率 · 粒度"）；Screen 层再拼「本机拉取 HH:mm」。 */
    fun line(source: DataSource): String {
        val m = forSource(source)
        return "${m.model} · ${m.resolution} · ${m.granularity}"
    }
}