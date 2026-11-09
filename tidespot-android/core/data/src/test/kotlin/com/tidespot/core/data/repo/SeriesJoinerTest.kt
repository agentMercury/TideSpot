package com.tidespot.core.data.repo

import com.tidespot.core.domain.model.HourlyMarine
import com.tidespot.core.domain.model.HourlyWeather
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** §5.0：两条序列禁止下标对齐，必须按时间戳 join。 */
class SeriesJoinerTest {

    @Test
    fun `按时间戳并集 join_两侧可空`() {
        val weather = listOf(
            HourlyWeather(timeEpochSec = 100),
            HourlyWeather(timeEpochSec = 200),
            HourlyWeather(timeEpochSec = 300),
        )
        val marine = listOf(
            HourlyMarine(timeEpochSec = 200, waveHeightM = 0.42),
            HourlyMarine(timeEpochSec = 300, waveHeightM = 0.50),
            HourlyMarine(timeEpochSec = 400, waveHeightM = 0.61),
        )
        val slices = SeriesJoiner.join(weather, marine)

        assertEquals(listOf(100L, 200L, 300L, 400L), slices.map { it.timeEpochSec }, "时间戳并集、升序")
        assertNull(slices[0].marine, "天气有而海洋没有的小时：marine 侧为 null")
        assertNull(slices[3].weather, "海洋有而天气没有的小时：weather 侧为 null")
        assertEquals(0.42, slices[1].marine?.waveHeightM)
        assertNotNull(slices[1].weather)
    }

    @Test
    fun `下标对齐会被本测试揭穿`() {
        // weather[1] 与 marine[0] 若按下标对齐将错误配对——按时间戳才正确
        val weather = listOf(
            HourlyWeather(timeEpochSec = 100, temperatureC = 10.0),
            HourlyWeather(timeEpochSec = 200, temperatureC = 11.0),
        )
        val marine = listOf(
            HourlyMarine(timeEpochSec = 200, waveHeightM = 0.3),
        )
        val slices = SeriesJoiner.join(weather, marine)

        assertEquals(2, slices.size)
        assertNull(slices[0].marine, "100 点无海洋数据——下标对齐会给出 wave=0.3（§5.0 踩坑复现）")
        assertEquals(0.3, slices[1].marine?.waveHeightM)
        assertEquals(11.0, slices[1].weather?.temperatureC)
    }

    @Test
    fun `空序列安全`() {
        assertEquals(0, SeriesJoiner.join(emptyList(), emptyList()).size)
        assertEquals(1, SeriesJoiner.join(listOf(HourlyWeather(timeEpochSec = 1)), emptyList()).size)
    }
}
