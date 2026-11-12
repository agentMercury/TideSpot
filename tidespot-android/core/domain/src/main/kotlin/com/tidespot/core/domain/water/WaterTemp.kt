package com.tidespot.core.domain.water

/**
 * 水温融合 Tw（SRS §6.1，先算——DO 与适温依赖它）。
 *
 * 6.1.1 海洋点：SST 实测优先。
 * 6.1.2 内陆点（无实测）：气温滞后 + 衰减模型——以参照实现 fishscore.mjs 第 6 节为黄金基线：
 *       近 31 h（k=0..30）气温按 w=1/(1+i−k) 加权平均 Tlag，Tw = 0.70·Tlag + 0.30·airTemp(h)。
 *       （SRS §6.1.2 的 K=0.93 指数衰减与该参照形式同族；M1 对拍以参照实现为准。）
 * （FO-15/M6 CHG-16：6.1.3 用户实测覆盖混合函数与 6.1.2 Copernicus Lakes 日值备选函数
 *  全仓零引用已删除，登记台账；需要时按 §6.1 原式重新只增恢复。）
 */
object WaterTempFusion {

    /** 滞后窗口（小时数，含当前小时）。 */
    const val WINDOW_HOURS: Int = 30

    /** 模型/气温混合系数 α（缺湖泊数据时）。 */
    const val ALPHA: Double = 0.70

    /**
     * 单点水温融合。
     * [airTemps] 逐小时气温序列（按时间升序，可与完整评分序列同长）；
     * [index] 当前小时下标；[sstC] 海表温度（非 null 即直接采用，§6.1.1）；
     * [airTempNow] 当前小时气温。
     * 返回 null 仅当窗口内气温全缺失且无 SST（§6.7 缺值语义）。
     */
    fun fuse(airTemps: List<Double?>, index: Int, airTempNow: Double?, sstC: Double?): Double? {
        if (sstC != null) return sstC
        var sum = 0.0
        var wSum = 0.0
        var any = false
        for (k in maxOf(0, index - WINDOW_HOURS)..index) {
            val t = airTemps.getOrNull(k) ?: continue
            val w = 1.0 / (1.0 + index - k)
            sum += t * w
            wSum += w
            any = true
        }
        if (!any) return null
        val tlag = sum / wSum
        val now = airTempNow ?: tlag
        return ALPHA * tlag + (1 - ALPHA) * now
    }

}
