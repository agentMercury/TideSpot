package com.tidespot.core.ui

import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.zone.SpotZones
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SpotZones 锁（OPT-M5-03 / D14；CHG-16①）：
 * tzOffsetSec → ZoneOffset.ofTotalSeconds 唯一口径；null = 系统默认 + 渲染处注明。
 */
class SpotZonesTest {

    @Test
    fun tzOffsetSec_convertsToExactOffset() {
        assertEquals(ZoneOffset.ofTotalSeconds(32400), SpotZones.zoneForSpot(32400)) // JST +9
        assertEquals(ZoneOffset.ofTotalSeconds(-18000), SpotZones.zoneForSpot(-18000)) // UTC-5
        assertEquals(ZoneOffset.UTC, SpotZones.zoneForSpot(0))
        assertEquals(ZoneOffset.ofTotalSeconds(19800), SpotZones.zoneForSpot(19800)) // +5:30 非整时
    }

    @Test
    fun nullTzOffset_fallsBackToDeviceDefault_notFixedOffset() {
        assertEquals(ZoneId.systemDefault(), SpotZones.zoneForSpot(null))
    }

    @Test
    fun zoneNote_nullExplainsDeviceConvention_writtenTzNeedsNoNote() {
        assertEquals(SpotZones.deviceZoneNote(), SpotZones.zoneNote(null))
        assertNull(SpotZones.zoneNote(32400))
    }

    @Test
    fun spotOverload_consistentWithRawOverload() {
        val spot = FavoriteSpot(id = 1L, name = "大阪湾", lat = 34.6, lon = 135.4, tzOffsetSec = 32400)
        assertEquals(ZoneOffset.ofTotalSeconds(32400), SpotZones.zoneForSpot(spot))
        val legacy = FavoriteSpot(id = 2L, name = "旧数据点", lat = 30.0, lon = 122.0)
        assertEquals(ZoneId.systemDefault(), SpotZones.zoneForSpot(legacy))
        assertTrue(SpotZones.zoneForSpot(spot) != SpotZones.zoneForSpot(legacy))
    }
}
