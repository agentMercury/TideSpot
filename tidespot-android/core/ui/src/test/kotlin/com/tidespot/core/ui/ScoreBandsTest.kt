package com.tidespot.core.ui

import com.tidespot.core.ui.band.BandTitles
import com.tidespot.core.ui.band.ScoreBand
import kotlin.test.Test
import kotlin.test.assertEquals

/** 5 档色带映射（§6.3 冻结色值；边界含端点）。 */
class ScoreBandsTest {

    @Test
    fun bandBoundaries_matchSpec() {
        // §6.3：≥80 爆护窗口 | 65–79 很适合 | 50–64 可以 | 35–49 较差 | <35 别去
        assertEquals(ScoreBand.EXPLOSIVE, ScoreBand.of(80))
        assertEquals(ScoreBand.EXPLOSIVE, ScoreBand.of(100))
        assertEquals(ScoreBand.GREAT, ScoreBand.of(79))
        assertEquals(ScoreBand.GREAT, ScoreBand.of(65))
        assertEquals(ScoreBand.OK, ScoreBand.of(64))
        assertEquals(ScoreBand.OK, ScoreBand.of(50))
        assertEquals(ScoreBand.POOR, ScoreBand.of(49))
        assertEquals(ScoreBand.POOR, ScoreBand.of(35))
        assertEquals(ScoreBand.BAD, ScoreBand.of(34))
        assertEquals(ScoreBand.BAD, ScoreBand.of(0))
    }

    @Test
    fun bandColors_matchFrozenHexValues() {
        assertEquals(0xFF1B5E20, ScoreBand.colorOf(85))
        assertEquals(0xFF43A047, ScoreBand.colorOf(70))
        assertEquals(0xFFFDD835, ScoreBand.colorOf(55))
        assertEquals(0xFFFB8C00, ScoreBand.colorOf(40))
        assertEquals(0xFFE53935, ScoreBand.colorOf(10))
    }

    @Test
    fun bandLabels_provideTextNotColorOnly() {
        // N-7：档位同时有文字
        assertEquals("爆护窗口", BandTitles.fromDomainTier(90))
        assertEquals("很适合", BandTitles.fromDomainTier(70))
        assertEquals("可以", BandTitles.fromDomainTier(55))
        assertEquals("较差", BandTitles.fromDomainTier(40))
        assertEquals("别去", BandTitles.fromDomainTier(10))
    }
}
