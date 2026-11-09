package com.tidespot.core.domain.water

/**
 * 水质代理 s_water（SRS §6.2j，参与水质 Tab，可选计入指数）。
 * 缺失语义：cloud/rain 缺失按中性 0 处理并保持"估算"置信；CHL 缺失走水温+光照推导。
 */
object WaterQualityProxy {

    /** 水质综合子分（[0,1]）。 */
    fun score(
        sOxygen: Double,
        cloudCoverPct: Double?,
        rain24hMm: Double?,
        chlMgm3: Double?,
        waterTempC: Double?,
    ): Double {
        val turbidity = com.tidespot.core.domain.algorithm.SubScores.turbidityIndex(
            cloudCoverPct, rain24hMm ?: 0.0,
        )
        val chl = com.tidespot.core.domain.algorithm.SubScores.chlorophyllIndex(chlMgm3, waterTempC, cloudCoverPct)
        return com.tidespot.core.domain.algorithm.SubScores.waterQuality(sOxygen, turbidity, chl)
    }
}
