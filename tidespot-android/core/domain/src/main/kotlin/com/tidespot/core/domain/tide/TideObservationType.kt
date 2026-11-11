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
 *
 * OPT-V11-13：**枚举不再携带中文 label**（本文件名零字面量）；展示名走
 * `DomainCopy.observationTypeLabel(type)`（默认中文 [com.tidespot.core.domain.strings.ZhDomainCopy]）。
 */
@Suppress("EnumEntryName")
enum class TideObservationType {
    HIGH,
    LOW,
    RISING_START,
    FALLING_START,
}