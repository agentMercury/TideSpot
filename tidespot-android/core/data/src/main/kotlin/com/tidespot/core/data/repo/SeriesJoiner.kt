package com.tidespot.core.data.repo

import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather

/** 按时间戳 join 后的小时切片：两侧均可空（天气 past_days=7 更长，海洋 past_days=1 更短）。 */
data class HourSlice(
    val timeEpochSec: Long,
    val weather: HourlyWeather?,
    val marine: HourlyMarine?,
)

/**
 * 两条序列按时间戳 join（SRS §5.0 [已验证踩坑]）。
 *
 * 天气 `past_days=7`（408h）与海洋 `past_days=1`（264h）起始时间不同，
 * **禁止按下标对齐**——必须以 epoch 秒为键做合并。输出为时间戳并集、升序。
 */
object SeriesJoiner {

    fun join(
        weather: List<HourlyWeather>,
        marine: List<HourlyMarine>,
    ): List<HourSlice> {
        val w = weather.associateBy { it.timeEpochSec }
        val m = marine.associateBy { it.timeEpochSec }
        return (w.keys + m.keys).distinct().sorted().map { t ->
            HourSlice(t, w[t], m[t])
        }
    }
}
