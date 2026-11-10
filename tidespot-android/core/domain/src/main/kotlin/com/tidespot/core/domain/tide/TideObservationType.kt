package com.tidespot.core.domain.tide

/**
 * 现场校潮观测类型（OPT-V11-07 / P1，**枚举只增**）。
 *
 * 四类（规格原文）：`高潮 / 低潮 / 我看到开始涨潮 / 我看到开始退潮`。
 * 匹配口径（[TideCalibrator.fitManual]）：
 * - [HIGH] / [FALLING_START] → 与模型**高潮**时刻匹配（涨→落翻转点即开始退潮）；
 * - [LOW] / [RISING_START] → 与模型**低潮**时刻匹配（落→涨翻转点即开始涨潮）。
 *
 * 历史数据兼容：v1 之前的观测无类型（`TIDE_OBSERVATION`），拟合时按「任意极值最近匹配」处理。
 */
enum class TideObservationType(val label: String) {
    HIGH("高潮"),
    LOW("低潮"),
    RISING_START("我开始涨潮"),
    FALLING_START("我开始退潮"),
}