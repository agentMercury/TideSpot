package com.tidespot.core.ui.zone

import com.tidespot.core.domain.model.FavoriteSpot
import com.tidespot.core.ui.strings.TideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 选点时区换算唯一入口（OPT-M5-03 / D14；CHG-16①）：
 * FavoriteSpot.tzOffsetSec → ZoneOffset.ofTotalSeconds。
 * null = 未回写（Open-Meteo timezone=auto 未到达/旧数据）→ 系统默认时区渲染，
 * 渲染处必须以 [zoneNote] 注明口径（「本机时区」）。
 *
 * OPT-V11-13：口径说明文案由 [TideSpotStrings] 注入（本文件名零中文字面量）。
 */
object SpotZones {

    /** tzOffsetSec → ZoneId；null → 设备默认时区（ZoneId.systemDefault）。 */
    fun zoneForSpot(tzOffsetSec: Int?): ZoneId =
        tzOffsetSec?.let { ZoneOffset.ofTotalSeconds(it) } ?: ZoneId.systemDefault()

    /** 便捷重载：整点消费 FavoriteSpot。 */
    fun zoneForSpot(spot: FavoriteSpot): ZoneId = zoneForSpot(spot.tzOffsetSec)

    /** null 时区的渲染口径说明（UI 文本与 TalkBack 共用）。 */
    fun deviceZoneNote(strings: TideSpotStrings = ZhStrings): String = strings.deviceZoneNote

    /** tzOffsetSec == null → 口径说明串；已回写 → null（无需注明）。 */
    fun zoneNote(tzOffsetSec: Int?, strings: TideSpotStrings = ZhStrings): String? =
        if (tzOffsetSec == null) deviceZoneNote(strings) else null
}