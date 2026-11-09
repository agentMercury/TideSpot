package com.tidespot.core.domain.util

/**
 * Open Location Code（Plus Code）编码 —— 全工程**单一实现**（CHG-09⑤ / NEW-R3 / GAP-M3-10）。
 *
 * 用途：RG-D 本地兜底（SRS §5.8 第 4 级，离线可用、永不失败）+ 逆地理关闭时的本地编码（N-5/F-1.3）。
 * 历史背景：原 `:core:data` 版（Math.round，~75% 末位错码）与 `:feature:map` 版（浮点递除，0.8% 错码）
 * 均不符合官方规范，本批收敛为本类后删除。
 *
 * 与官方规范（openlocationcode 整数截断法）的逐条对应：
 * - 10 位编码 = 5 对字符，纬/经交替（先纬后经），第 8 位后插 `+`；
 * - 码表 20 字符 `23456789CFGHJMPQRVWX`（官方排除易混淆的 0/1/A/I/L/O/U）；
 * - 坐标预处理：纬度裁剪到 [-90,90]，经度归一到 [-180,180)（官方 normalizeLongitude 语义）；
 * - 各对的位值依次除 20：20° / 1° / 0.05° / 0.0025° / 0.000125°（末位即 10 位码分辨率）；
 * - **严格截断（floor）**：点落在哪一格就编码哪一格，禁止 round（round 会把位于格内
 *   后半段的点编入相邻格 → 末位错码）；
 * - 整数化：坐标 ×1e8 后取整为 Long（对 ≤7 位小数输入无损；×1e8 后概念上必为整数，
 *   `Math.round` 仅修复二进制浮点表示误差，幅值 <1 单位），随后**每一位抽取全部用 Long
 *   整除（floor）**，无任何 round。对 >7 位小数输入，行为定义为「就近量化到 1e-8° 后截断」。
 *
 * 黄金值（附录 B7b，以黄金值为准；先经脚本+手算双验证再固化）：
 * - encode(29.55, 118.95)   = `7PXWHX22+22`（千岛湖湖心）
 * - encode(29.605, 119.038) = `7PXXJ24Q+26`（岸边）
 * - encode(31.2, 120.3)     = `8Q326822+22`（官方 README 同型向量）
 *
 * 说明：极点（|lat|=90°）非本项目场景（中国近海/内陆选点），不做官方的 90° 特判。
 */
object OpenLocationCode {

    /** OLC 20 字符表（去掉易混淆的 0/1/A/I/L/O/U）。 */
    private const val ALPHABET = "23456789CFGHJMPQRVWX"

    /** 输出固定 10 位（8 位 + `+` + 2 位）。 */
    private const val CODE_LENGTH = 10

    /** 整数化缩放：×1e8（对 7 位小数输入无损）。 */
    private const val SCALE = 100_000_000L

    /** 各对的整数化位值（×1e8 尺度）：20° / 1° / 0.05° / 0.0025° / 0.000125°。 */
    private val PLACE_VALUES = longArrayOf(2_000_000_000L, 100_000_000L, 5_000_000L, 250_000L, 12_500L)

    /** 编码为 10 位 Plus Code（第 8 位后插 `+`）。 */
    fun encode(lat: Double, lon: Double): String {
        val latScaled = Math.round((lat.coerceIn(-90.0, 90.0) + 90.0) * SCALE)
        // 经度归一到 [0, 360)，再整体缩放取整（-241.0 ≡ 119.0 等 ±360 等价）
        val lonNorm = ((lon + 180.0) % 360.0 + 360.0) % 360.0
        val lonScaled = Math.round(lonNorm * SCALE)

        val sb = StringBuilder(CODE_LENGTH + 1)
        var lv = latScaled
        var ov = lonScaled
        for (place in PLACE_VALUES) {
            val dLat = (lv / place).toInt().coerceIn(0, ALPHABET.length - 1)
            val dLon = (ov / place).toInt().coerceIn(0, ALPHABET.length - 1)
            lv -= dLat * place
            ov -= dLon * place
            sb.append(ALPHABET[dLat]).append(ALPHABET[dLon])
            if (sb.length == 8) sb.append('+') // 8 位后插入加号
        }
        return sb.toString()
    }
}
