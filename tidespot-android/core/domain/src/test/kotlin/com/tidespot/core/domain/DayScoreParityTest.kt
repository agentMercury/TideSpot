package com.tidespot.core.domain

import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.domain.model.FishScoreResult
import com.tidespot.core.domain.window.DayScoreCalculator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 日综合分双端对拍（200 组向量）。
 *
 * 向量由 Web `computeDayScore` **实际运行输出**生成，保留完整 double 精度
 * （不用 toFixed，那会截断精度导致假失败）。含三类：
 * - `nohazard_*`（120 组）：全部小时 >10，测算法主体
 * - `hazard_*`（40 组）：含 ≤10 小时，测安全封顶
 * - `highlight_*`（40 组）：少数高峰 + 中等底，测高光加成
 *
 * 容差 1e-9——两端都是 IEEE 754 double，同样运算顺序应逐位一致。
 * 若失败说明算法漂移，**先查清再改，不许放宽容差**。
 */
class DayScoreParityTest {

    private val tz = 8 * 3600
    private val dayStart = 1794844800L

    private fun results(hourly: List<Int>): List<FishScoreResult> =
        hourly.mapIndexed { h, v ->
            FishScoreResult(
                timeEpochSec = dayStart + h * 3600L,
                total = v,
                factors = emptyList(),
                vetoReasons = emptyList(),
                confidence = Confidence.MODEL,
            )
        }

    @Test
    fun `200 组随机向量与 Web 完全一致`() {
        // nohazard_0
        assertEquals(71.17142857142858, DayScoreCalculator.compute(results(listOf(19, 43, 28, 15, 68, 80, 34, 23, 40, 100, 19, 80, 49, 78, 84, 92, 37, 33, 68, 30, 66, 54, 38, 99)), tz)!!, 1e-9, "nohazard_0")
        // nohazard_1
        assertEquals(67.48571428571428, DayScoreCalculator.compute(results(listOf(70, 88, 38, 21, 40, 81, 90, 27, 70, 98, 46, 46, 54, 48, 67, 54, 29, 82, 86, 46, 72, 82, 93, 19)), tz)!!, 1e-9, "nohazard_1")
        // nohazard_2
        assertEquals(74.42857142857143, DayScoreCalculator.compute(results(listOf(97, 90, 63, 74, 27, 29, 53, 41, 83, 68, 46, 95, 82, 88, 72, 86, 65, 24, 57, 88, 17, 21, 58, 59)), tz)!!, 1e-9, "nohazard_2")
        // nohazard_3
        assertEquals(68.22857142857143, DayScoreCalculator.compute(results(listOf(23, 83, 58, 90, 11, 11, 17, 17, 23, 88, 82, 84, 36, 15, 36, 99, 84, 60, 45, 46, 36, 85, 78, 58)), tz)!!, 1e-9, "nohazard_3")
        // nohazard_4
        assertEquals(62.45294117647059, DayScoreCalculator.compute(results(listOf(15, 62, 57, 14, 87, 60, 26, 13, 98, 44, 32, 70, 86, 45, 84, 13, 89, 70, 35, 76, 32, 49, 62, 31)), tz)!!, 1e-9, "nohazard_4")
        // nohazard_5
        assertEquals(71.17142857142856, DayScoreCalculator.compute(results(listOf(49, 20, 57, 73, 97, 35, 70, 100, 56, 63, 72, 86, 79, 71, 91, 13, 87, 39, 19, 34, 80, 50, 15, 88)), tz)!!, 1e-9, "nohazard_5")
        // nohazard_6
        assertEquals(70.22521008403362, DayScoreCalculator.compute(results(listOf(18, 44, 77, 37, 87, 45, 32, 77, 25, 79, 72, 95, 30, 53, 79, 15, 95, 98, 17, 36, 16, 78, 68, 48)), tz)!!, 1e-9, "nohazard_6")
        // nohazard_7
        assertEquals(68.55378151260504, DayScoreCalculator.compute(results(listOf(24, 80, 64, 83, 50, 23, 91, 41, 16, 44, 82, 73, 82, 18, 85, 52, 18, 82, 88, 43, 88, 16, 82, 66)), tz)!!, 1e-9, "nohazard_7")
        // nohazard_8
        assertEquals(68.34285714285714, DayScoreCalculator.compute(results(listOf(75, 14, 69, 87, 56, 12, 84, 14, 99, 84, 13, 56, 17, 69, 74, 85, 40, 95, 61, 72, 83, 66, 71, 15)), tz)!!, 1e-9, "nohazard_8")
        // nohazard_9
        assertEquals(70.44957983193277, DayScoreCalculator.compute(results(listOf(35, 30, 59, 34, 66, 90, 55, 36, 24, 19, 12, 71, 53, 31, 67, 100, 96, 78, 22, 14, 19, 94, 91, 45)), tz)!!, 1e-9, "nohazard_9")
        // nohazard_10
        assertEquals(69.53865546218486, DayScoreCalculator.compute(results(listOf(39, 26, 31, 72, 18, 89, 50, 68, 62, 23, 30, 82, 12, 22, 68, 87, 95, 34, 28, 60, 100, 63, 82, 94)), tz)!!, 1e-9, "nohazard_10")
        // nohazard_11
        assertEquals(75.82857142857142, DayScoreCalculator.compute(results(listOf(89, 98, 46, 78, 52, 37, 77, 20, 19, 96, 18, 35, 37, 95, 51, 56, 58, 28, 32, 94, 94, 97, 69, 27)), tz)!!, 1e-9, "nohazard_11")
        // nohazard_12
        assertEquals(60.554621848739494, DayScoreCalculator.compute(results(listOf(80, 93, 93, 21, 15, 30, 99, 37, 31, 100, 83, 14, 49, 13, 84, 65, 58, 45, 62, 24, 39, 59, 51, 38)), tz)!!, 1e-9, "nohazard_12")
        // nohazard_13
        assertEquals(66.35294117647058, DayScoreCalculator.compute(results(listOf(11, 64, 64, 56, 29, 97, 73, 45, 34, 34, 94, 69, 65, 92, 50, 51, 32, 65, 44, 75, 13, 72, 86, 55)), tz)!!, 1e-9, "nohazard_13")
        // nohazard_14
        assertEquals(68.34033613445378, DayScoreCalculator.compute(results(listOf(23, 18, 51, 89, 43, 82, 36, 77, 70, 36, 14, 28, 72, 87, 64, 89, 62, 31, 67, 66, 80, 24, 83, 69)), tz)!!, 1e-9, "nohazard_14")
        // nohazard_15
        assertEquals(81.09411764705882, DayScoreCalculator.compute(results(listOf(26, 84, 63, 15, 29, 78, 98, 97, 65, 62, 40, 69, 84, 82, 91, 45, 72, 12, 37, 29, 76, 89, 77, 86)), tz)!!, 1e-9, "nohazard_15")
        // nohazard_16
        assertEquals(66.5126050420168, DayScoreCalculator.compute(results(listOf(65, 65, 11, 15, 49, 44, 65, 86, 93, 39, 17, 33, 69, 34, 68, 96, 34, 84, 20, 77, 27, 20, 24, 17)), tz)!!, 1e-9, "nohazard_16")
        // nohazard_17
        assertEquals(66.16974789915966, DayScoreCalculator.compute(results(listOf(59, 19, 50, 19, 25, 23, 87, 42, 96, 53, 70, 65, 80, 30, 83, 48, 69, 14, 42, 60, 45, 84, 59, 33)), tz)!!, 1e-9, "nohazard_17")
        // nohazard_18
        assertEquals(76.42521008403361, DayScoreCalculator.compute(results(listOf(80, 85, 78, 56, 27, 88, 69, 89, 65, 76, 69, 81, 95, 58, 64, 53, 91, 100, 40, 45, 56, 41, 96, 85)), tz)!!, 1e-9, "nohazard_18")
        // nohazard_19
        assertEquals(66.4235294117647, DayScoreCalculator.compute(results(listOf(80, 41, 32, 51, 15, 37, 13, 95, 65, 11, 21, 60, 72, 92, 98, 41, 61, 67, 74, 24, 40, 35, 43, 100)), tz)!!, 1e-9, "nohazard_19")
        // nohazard_20
        assertEquals(62.88235294117648, DayScoreCalculator.compute(results(listOf(63, 99, 82, 97, 36, 74, 96, 56, 75, 39, 22, 23, 26, 59, 79, 34, 42, 100, 29, 74, 48, 30, 80, 77)), tz)!!, 1e-9, "nohazard_20")
        // nohazard_21
        assertEquals(58.337815126050415, DayScoreCalculator.compute(results(listOf(29, 91, 18, 53, 76, 91, 87, 45, 27, 18, 43, 48, 98, 39, 70, 51, 20, 30, 26, 51, 27, 50, 89, 65)), tz)!!, 1e-9, "nohazard_21")
        // nohazard_22
        assertEquals(66.31428571428572, DayScoreCalculator.compute(results(listOf(32, 37, 90, 56, 34, 68, 80, 39, 68, 89, 26, 94, 96, 20, 51, 79, 31, 90, 57, 25, 43, 69, 31, 74)), tz)!!, 1e-9, "nohazard_22")
        // nohazard_23
        assertEquals(63.403361344537814, DayScoreCalculator.compute(results(listOf(90, 59, 70, 91, 89, 29, 12, 60, 84, 22, 36, 42, 78, 83, 88, 39, 44, 43, 23, 50, 48, 47, 39, 52)), tz)!!, 1e-9, "nohazard_23")
        // nohazard_24
        assertEquals(60.9016806722689, DayScoreCalculator.compute(results(listOf(18, 46, 12, 68, 26, 54, 53, 98, 52, 27, 50, 48, 76, 51, 57, 53, 36, 70, 31, 65, 81, 54, 21, 73)), tz)!!, 1e-9, "nohazard_24")
        // nohazard_25
        assertEquals(67.0, DayScoreCalculator.compute(results(listOf(93, 83, 51, 94, 30, 14, 94, 12, 62, 56, 83, 77, 45, 33, 91, 63, 83, 45, 76, 12, 56, 21, 28, 52)), tz)!!, 1e-9, "nohazard_25")
        // nohazard_26
        assertEquals(80.91344537815127, DayScoreCalculator.compute(results(listOf(82, 13, 17, 61, 89, 50, 80, 58, 38, 70, 12, 78, 98, 65, 38, 96, 100, 91, 14, 47, 28, 97, 33, 87)), tz)!!, 1e-9, "nohazard_26")
        // nohazard_27
        assertEquals(57.36974789915966, DayScoreCalculator.compute(results(listOf(100, 12, 33, 76, 99, 21, 92, 32, 53, 52, 75, 15, 71, 24, 94, 26, 87, 67, 53, 32, 22, 11, 99, 59)), tz)!!, 1e-9, "nohazard_27")
        // nohazard_28
        assertEquals(68.81428571428572, DayScoreCalculator.compute(results(listOf(49, 57, 36, 34, 79, 81, 81, 13, 64, 33, 13, 79, 80, 22, 13, 32, 75, 55, 74, 92, 52, 93, 90, 27)), tz)!!, 1e-9, "nohazard_28")
        // nohazard_29
        assertEquals(61.96638655462185, DayScoreCalculator.compute(results(listOf(66, 35, 36, 60, 89, 47, 26, 59, 94, 39, 17, 63, 64, 77, 34, 54, 100, 36, 86, 16, 29, 59, 37, 98)), tz)!!, 1e-9, "nohazard_29")
        // nohazard_30
        assertEquals(74.78991596638654, DayScoreCalculator.compute(results(listOf(83, 48, 91, 37, 41, 82, 91, 98, 25, 44, 90, 27, 28, 80, 27, 70, 12, 85, 97, 25, 40, 18, 62, 54)), tz)!!, 1e-9, "nohazard_30")
        // nohazard_31
        assertEquals(63.66386554621849, DayScoreCalculator.compute(results(listOf(45, 29, 14, 38, 72, 75, 64, 27, 28, 84, 21, 32, 71, 48, 44, 26, 40, 65, 72, 34, 96, 98, 81, 95)), tz)!!, 1e-9, "nohazard_31")
        // nohazard_32
        assertEquals(72.8563025210084, DayScoreCalculator.compute(results(listOf(63, 23, 41, 62, 87, 82, 18, 78, 57, 98, 12, 35, 25, 63, 65, 94, 16, 86, 68, 84, 62, 100, 38, 43)), tz)!!, 1e-9, "nohazard_32")
        // nohazard_33
        assertEquals(70.82857142857144, DayScoreCalculator.compute(results(listOf(91, 40, 78, 12, 72, 93, 18, 89, 74, 43, 26, 59, 52, 43, 59, 75, 83, 65, 95, 29, 81, 37, 74, 56)), tz)!!, 1e-9, "nohazard_33")
        // nohazard_34
        assertEquals(65.8016806722689, DayScoreCalculator.compute(results(listOf(20, 83, 100, 54, 57, 71, 88, 17, 76, 81, 71, 17, 54, 61, 29, 73, 83, 62, 13, 84, 12, 72, 96, 16)), tz)!!, 1e-9, "nohazard_34")
        // nohazard_35
        assertEquals(70.82857142857144, DayScoreCalculator.compute(results(listOf(69, 45, 17, 71, 21, 46, 95, 12, 39, 30, 63, 85, 91, 43, 55, 85, 59, 99, 68, 17, 37, 97, 93, 15)), tz)!!, 1e-9, "nohazard_35")
        // nohazard_36
        assertEquals(62.55042016806723, DayScoreCalculator.compute(results(listOf(27, 82, 58, 12, 90, 66, 30, 40, 86, 35, 38, 47, 34, 17, 58, 99, 70, 64, 86, 37, 67, 28, 14, 92)), tz)!!, 1e-9, "nohazard_36")
        // nohazard_37
        assertEquals(67.95798319327731, DayScoreCalculator.compute(results(listOf(68, 71, 49, 12, 46, 30, 53, 42, 15, 47, 32, 82, 78, 65, 43, 65, 59, 22, 29, 96, 70, 93, 75, 33)), tz)!!, 1e-9, "nohazard_37")
        // nohazard_38
        assertEquals(75.97310924369748, DayScoreCalculator.compute(results(listOf(25, 54, 39, 63, 94, 53, 87, 78, 62, 81, 59, 77, 57, 44, 24, 54, 98, 71, 82, 52, 67, 81, 12, 75)), tz)!!, 1e-9, "nohazard_38")
        // nohazard_39
        assertEquals(67.11932773109244, DayScoreCalculator.compute(results(listOf(46, 23, 83, 50, 19, 65, 66, 61, 88, 13, 30, 36, 83, 79, 16, 50, 27, 74, 63, 98, 27, 95, 65, 53)), tz)!!, 1e-9, "nohazard_39")
        // nohazard_40
        assertEquals(63.9453781512605, DayScoreCalculator.compute(results(listOf(36, 89, 83, 56, 76, 43, 87, 23, 35, 35, 67, 88, 97, 27, 81, 17, 18, 20, 88, 11, 27, 19, 28, 50)), tz)!!, 1e-9, "nohazard_40")
        // nohazard_41
        assertEquals(68.91428571428571, DayScoreCalculator.compute(results(listOf(90, 13, 43, 45, 37, 43, 86, 81, 42, 72, 22, 54, 61, 70, 27, 11, 13, 76, 80, 85, 84, 11, 76, 74)), tz)!!, 1e-9, "nohazard_41")
        // nohazard_42
        assertEquals(61.21008403361345, DayScoreCalculator.compute(results(listOf(77, 72, 89, 49, 55, 39, 45, 33, 41, 66, 89, 19, 64, 64, 11, 21, 71, 13, 24, 55, 85, 100, 84, 70)), tz)!!, 1e-9, "nohazard_42")
        // nohazard_43
        assertEquals(64.90924369747898, DayScoreCalculator.compute(results(listOf(19, 29, 96, 13, 81, 85, 77, 90, 15, 31, 26, 56, 53, 96, 13, 75, 52, 11, 26, 13, 73, 22, 36, 54)), tz)!!, 1e-9, "nohazard_43")
        // nohazard_44
        assertEquals(62.39495798319327, DayScoreCalculator.compute(results(listOf(65, 65, 15, 55, 79, 28, 89, 53, 64, 61, 76, 71, 21, 17, 22, 30, 51, 22, 49, 76, 93, 60, 41, 58)), tz)!!, 1e-9, "nohazard_44")
        // nohazard_45
        assertEquals(57.98991596638655, DayScoreCalculator.compute(results(listOf(61, 48, 47, 29, 99, 69, 18, 18, 25, 90, 28, 42, 11, 61, 32, 55, 19, 39, 13, 51, 99, 88, 56, 27)), tz)!!, 1e-9, "nohazard_45")
        // nohazard_46
        assertEquals(78.52941176470588, DayScoreCalculator.compute(results(listOf(83, 99, 68, 43, 35, 60, 98, 12, 89, 97, 54, 29, 89, 82, 96, 15, 30, 82, 73, 57, 13, 92, 60, 24)), tz)!!, 1e-9, "nohazard_46")
        // nohazard_47
        assertEquals(66.19327731092437, DayScoreCalculator.compute(results(listOf(95, 78, 25, 87, 81, 44, 93, 34, 23, 68, 80, 67, 68, 63, 79, 62, 51, 85, 74, 66, 23, 33, 55, 88)), tz)!!, 1e-9, "nohazard_47")
        // nohazard_48
        assertEquals(72.28991596638656, DayScoreCalculator.compute(results(listOf(80, 65, 37, 67, 27, 42, 50, 87, 82, 72, 49, 51, 30, 93, 92, 74, 61, 16, 99, 46, 42, 30, 57, 89)), tz)!!, 1e-9, "nohazard_48")
        // nohazard_49
        assertEquals(65.48991596638655, DayScoreCalculator.compute(results(listOf(30, 67, 15, 56, 45, 64, 77, 40, 72, 41, 40, 44, 45, 26, 56, 94, 22, 74, 28, 68, 99, 72, 93, 68)), tz)!!, 1e-9, "nohazard_49")
        // nohazard_50
        assertEquals(65.1453781512605, DayScoreCalculator.compute(results(listOf(56, 24, 84, 65, 19, 60, 24, 12, 92, 21, 56, 16, 94, 65, 67, 84, 75, 39, 63, 87, 44, 54, 93, 71)), tz)!!, 1e-9, "nohazard_50")
        // nohazard_51
        assertEquals(62.6890756302521, DayScoreCalculator.compute(results(listOf(79, 98, 65, 77, 49, 48, 17, 93, 46, 93, 46, 55, 78, 30, 60, 81, 35, 28, 32, 41, 38, 58, 31, 56)), tz)!!, 1e-9, "nohazard_51")
        // nohazard_52
        assertEquals(70.74285714285715, DayScoreCalculator.compute(results(listOf(65, 42, 18, 83, 66, 86, 18, 63, 33, 39, 90, 49, 88, 89, 69, 61, 88, 89, 53, 12, 46, 27, 19, 89)), tz)!!, 1e-9, "nohazard_52")
        // nohazard_53
        assertEquals(73.01176470588236, DayScoreCalculator.compute(results(listOf(44, 16, 47, 99, 60, 34, 93, 94, 20, 12, 93, 98, 28, 13, 93, 26, 49, 87, 95, 60, 85, 66, 70, 62)), tz)!!, 1e-9, "nohazard_53")
        // nohazard_54
        assertEquals(61.66218487394958, DayScoreCalculator.compute(results(listOf(42, 11, 43, 21, 17, 42, 57, 83, 23, 51, 25, 13, 71, 22, 92, 69, 65, 64, 58, 57, 64, 71, 12, 91)), tz)!!, 1e-9, "nohazard_54")
        // nohazard_55
        assertEquals(68.88907563025211, DayScoreCalculator.compute(results(listOf(69, 32, 49, 32, 23, 12, 40, 48, 18, 99, 92, 61, 54, 98, 90, 37, 12, 29, 77, 53, 70, 52, 31, 46)), tz)!!, 1e-9, "nohazard_55")
        // nohazard_56
        assertEquals(70.17142857142858, DayScoreCalculator.compute(results(listOf(16, 14, 42, 97, 11, 97, 85, 61, 44, 38, 79, 28, 36, 36, 50, 89, 63, 13, 92, 91, 52, 44, 21, 45)), tz)!!, 1e-9, "nohazard_56")
        // nohazard_57
        assertEquals(72.61848739495798, DayScoreCalculator.compute(results(listOf(30, 84, 88, 51, 80, 95, 63, 54, 78, 66, 92, 36, 36, 32, 64, 55, 51, 39, 55, 99, 64, 88, 91, 98)), tz)!!, 1e-9, "nohazard_57")
        // nohazard_58
        assertEquals(62.73109243697479, DayScoreCalculator.compute(results(listOf(86, 89, 31, 41, 63, 35, 15, 26, 73, 80, 35, 71, 80, 47, 15, 95, 44, 94, 51, 29, 40, 46, 52, 75)), tz)!!, 1e-9, "nohazard_58")
        // nohazard_59
        assertEquals(71.53277310924369, DayScoreCalculator.compute(results(listOf(37, 87, 69, 94, 52, 57, 34, 100, 88, 83, 20, 25, 39, 93, 12, 70, 69, 61, 37, 85, 42, 16, 24, 67)), tz)!!, 1e-9, "nohazard_59")
        // nohazard_60
        assertEquals(66.2344537815126, DayScoreCalculator.compute(results(listOf(65, 21, 78, 67, 58, 73, 44, 19, 89, 24, 91, 40, 29, 27, 35, 96, 51, 98, 63, 18, 13, 82, 64, 39)), tz)!!, 1e-9, "nohazard_60")
        // nohazard_61
        assertEquals(59.29747899159664, DayScoreCalculator.compute(results(listOf(24, 64, 62, 51, 18, 67, 21, 39, 56, 16, 88, 39, 97, 45, 69, 32, 31, 13, 87, 30, 51, 63, 18, 78)), tz)!!, 1e-9, "nohazard_61")
        // nohazard_62
        assertEquals(68.74117647058823, DayScoreCalculator.compute(results(listOf(21, 60, 81, 14, 20, 93, 91, 51, 44, 19, 86, 14, 77, 77, 77, 20, 88, 12, 43, 93, 28, 11, 45, 55)), tz)!!, 1e-9, "nohazard_62")
        // nohazard_63
        assertEquals(64.58403361344538, DayScoreCalculator.compute(results(listOf(72, 87, 21, 17, 45, 78, 53, 21, 26, 95, 49, 33, 67, 94, 52, 79, 76, 64, 38, 18, 63, 34, 83, 80)), tz)!!, 1e-9, "nohazard_63")
        // nohazard_64
        assertEquals(71.5764705882353, DayScoreCalculator.compute(results(listOf(88, 53, 60, 85, 65, 26, 71, 82, 86, 65, 94, 21, 31, 81, 88, 41, 38, 11, 70, 31, 85, 80, 51, 41)), tz)!!, 1e-9, "nohazard_64")
        // nohazard_65
        assertEquals(60.65882352941176, DayScoreCalculator.compute(results(listOf(68, 30, 52, 29, 74, 55, 60, 24, 86, 24, 83, 16, 96, 31, 75, 22, 56, 53, 81, 47, 62, 59, 92, 52)), tz)!!, 1e-9, "nohazard_65")
        // nohazard_66
        assertEquals(66.28655462184874, DayScoreCalculator.compute(results(listOf(37, 56, 80, 51, 63, 63, 64, 77, 85, 77, 72, 84, 46, 48, 21, 100, 25, 32, 64, 28, 19, 19, 75, 83)), tz)!!, 1e-9, "nohazard_66")
        // nohazard_67
        assertEquals(74.37142857142857, DayScoreCalculator.compute(results(listOf(38, 25, 26, 33, 28, 17, 86, 96, 72, 65, 42, 71, 24, 65, 55, 82, 76, 37, 99, 56, 83, 53, 84, 95)), tz)!!, 1e-9, "nohazard_67")
        // nohazard_68
        assertEquals(63.048739495798316, DayScoreCalculator.compute(results(listOf(49, 60, 73, 100, 39, 58, 14, 15, 89, 65, 21, 99, 88, 31, 73, 30, 30, 61, 44, 96, 76, 36, 60, 14)), tz)!!, 1e-9, "nohazard_68")
        // nohazard_69
        assertEquals(66.42857142857143, DayScoreCalculator.compute(results(listOf(65, 52, 53, 94, 17, 52, 79, 11, 53, 28, 31, 97, 76, 52, 55, 52, 37, 88, 75, 51, 59, 79, 65, 40)), tz)!!, 1e-9, "nohazard_69")
        // nohazard_70
        assertEquals(71.76470588235294, DayScoreCalculator.compute(results(listOf(53, 53, 57, 24, 70, 32, 98, 49, 72, 77, 63, 80, 89, 37, 22, 88, 48, 82, 31, 34, 93, 83, 80, 64)), tz)!!, 1e-9, "nohazard_70")
        // nohazard_71
        assertEquals(69.05714285714285, DayScoreCalculator.compute(results(listOf(45, 100, 40, 71, 67, 28, 41, 44, 31, 27, 72, 85, 81, 27, 96, 47, 33, 91, 62, 41, 95, 88, 22, 35)), tz)!!, 1e-9, "nohazard_71")
        // nohazard_72
        assertEquals(71.60000000000001, DayScoreCalculator.compute(results(listOf(47, 11, 80, 55, 53, 70, 74, 90, 72, 14, 38, 97, 86, 52, 52, 26, 96, 59, 76, 49, 61, 78, 63, 55)), tz)!!, 1e-9, "nohazard_72")
        // nohazard_73
        assertEquals(56.88235294117647, DayScoreCalculator.compute(results(listOf(14, 66, 83, 44, 27, 62, 18, 45, 29, 12, 95, 31, 35, 93, 77, 24, 98, 46, 25, 16, 70, 45, 72, 34)), tz)!!, 1e-9, "nohazard_73")
        // nohazard_74
        assertEquals(51.88823529411765, DayScoreCalculator.compute(results(listOf(28, 25, 56, 24, 77, 31, 71, 34, 48, 43, 45, 19, 39, 46, 55, 86, 42, 52, 74, 40, 61, 20, 77, 39)), tz)!!, 1e-9, "nohazard_74")
        // nohazard_75
        assertEquals(61.39075630252101, DayScoreCalculator.compute(results(listOf(76, 65, 41, 98, 38, 88, 54, 19, 99, 23, 74, 79, 39, 94, 23, 38, 23, 54, 49, 14, 85, 48, 72, 45)), tz)!!, 1e-9, "nohazard_75")
        // nohazard_76
        assertEquals(49.01764705882353, DayScoreCalculator.compute(results(listOf(54, 57, 87, 93, 70, 78, 44, 68, 54, 36, 55, 24, 63, 27, 36, 25, 50, 23, 41, 28, 62, 15, 26, 99)), tz)!!, 1e-9, "nohazard_76")
        // nohazard_77
        assertEquals(61.420168067226896, DayScoreCalculator.compute(results(listOf(46, 91, 69, 52, 100, 85, 17, 46, 85, 33, 29, 33, 74, 57, 90, 51, 71, 13, 30, 24, 71, 79, 11, 24)), tz)!!, 1e-9, "nohazard_77")
        // nohazard_78
        assertEquals(65.07058823529411, DayScoreCalculator.compute(results(listOf(43, 91, 11, 54, 31, 57, 45, 31, 70, 83, 44, 27, 17, 79, 24, 47, 54, 60, 79, 77, 81, 20, 50, 31)), tz)!!, 1e-9, "nohazard_78")
        // nohazard_79
        assertEquals(65.32605042016806, DayScoreCalculator.compute(results(listOf(92, 59, 54, 20, 78, 51, 19, 27, 11, 47, 75, 19, 34, 62, 54, 90, 84, 21, 48, 86, 64, 91, 53, 36)), tz)!!, 1e-9, "nohazard_79")
        // nohazard_80
        assertEquals(64.20756302521008, DayScoreCalculator.compute(results(listOf(19, 29, 33, 37, 83, 70, 31, 77, 45, 46, 12, 88, 14, 60, 63, 100, 37, 49, 83, 48, 40, 75, 11, 72)), tz)!!, 1e-9, "nohazard_80")
        // nohazard_81
        assertEquals(67.2, DayScoreCalculator.compute(results(listOf(27, 56, 24, 91, 34, 75, 18, 91, 18, 61, 78, 76, 87, 40, 30, 97, 25, 28, 46, 46, 16, 74, 38, 25)), tz)!!, 1e-9, "nohazard_81")
        // nohazard_82
        assertEquals(58.76806722689076, DayScoreCalculator.compute(results(listOf(71, 71, 97, 46, 14, 43, 64, 52, 40, 16, 24, 69, 56, 23, 41, 97, 20, 70, 81, 72, 61, 32, 37, 81)), tz)!!, 1e-9, "nohazard_82")
        // nohazard_83
        assertEquals(73.97226890756302, DayScoreCalculator.compute(results(listOf(46, 31, 98, 65, 42, 66, 86, 78, 97, 58, 70, 85, 22, 33, 100, 72, 64, 55, 55, 17, 70, 32, 48, 41)), tz)!!, 1e-9, "nohazard_83")
        // nohazard_84
        assertEquals(73.11428571428571, DayScoreCalculator.compute(results(listOf(32, 16, 17, 98, 37, 65, 45, 21, 52, 70, 32, 91, 82, 56, 80, 71, 65, 26, 78, 77, 96, 46, 68, 71)), tz)!!, 1e-9, "nohazard_84")
        // nohazard_85
        assertEquals(59.1764705882353, DayScoreCalculator.compute(results(listOf(35, 57, 51, 96, 35, 66, 90, 42, 11, 30, 15, 30, 90, 43, 97, 46, 44, 18, 15, 93, 15, 38, 84, 11)), tz)!!, 1e-9, "nohazard_85")
        // nohazard_86
        assertEquals(54.61176470588235, DayScoreCalculator.compute(results(listOf(59, 27, 23, 34, 50, 83, 94, 14, 82, 84, 27, 41, 11, 13, 17, 53, 52, 15, 52, 31, 27, 85, 93, 23)), tz)!!, 1e-9, "nohazard_86")
        // nohazard_87
        assertEquals(61.694117647058825, DayScoreCalculator.compute(results(listOf(82, 63, 92, 69, 68, 50, 71, 37, 69, 29, 93, 67, 61, 88, 46, 14, 13, 59, 75, 57, 52, 40, 17, 53)), tz)!!, 1e-9, "nohazard_87")
        // nohazard_88
        assertEquals(81.83529411764707, DayScoreCalculator.compute(results(listOf(36, 21, 23, 38, 48, 20, 85, 63, 90, 72, 66, 60, 39, 43, 93, 89, 94, 19, 83, 77, 53, 86, 78, 84)), tz)!!, 1e-9, "nohazard_88")
        // nohazard_89
        assertEquals(63.63025210084034, DayScoreCalculator.compute(results(listOf(61, 31, 46, 11, 43, 11, 80, 64, 88, 42, 54, 43, 51, 98, 34, 64, 63, 52, 36, 77, 39, 11, 66, 46)), tz)!!, 1e-9, "nohazard_89")
        // nohazard_90
        assertEquals(72.67394957983193, DayScoreCalculator.compute(results(listOf(88, 72, 81, 95, 42, 47, 57, 98, 79, 42, 30, 75, 87, 84, 32, 97, 95, 38, 47, 60, 55, 13, 17, 60)), tz)!!, 1e-9, "nohazard_90")
        // nohazard_91
        assertEquals(64.63025210084034, DayScoreCalculator.compute(results(listOf(91, 39, 93, 97, 28, 41, 28, 47, 94, 94, 44, 28, 66, 62, 95, 63, 41, 27, 93, 11, 42, 48, 91, 29)), tz)!!, 1e-9, "nohazard_91")
        // nohazard_92
        assertEquals(65.10084033613445, DayScoreCalculator.compute(results(listOf(37, 98, 27, 57, 91, 14, 97, 37, 77, 12, 27, 65, 96, 74, 23, 38, 67, 61, 71, 68, 11, 100, 11, 84)), tz)!!, 1e-9, "nohazard_92")
        // nohazard_93
        assertEquals(75.4, DayScoreCalculator.compute(results(listOf(20, 39, 70, 52, 88, 63, 99, 21, 45, 25, 68, 97, 80, 93, 95, 40, 12, 77, 71, 56, 22, 55, 96, 85)), tz)!!, 1e-9, "nohazard_93")
        // nohazard_94
        assertEquals(73.05882352941177, DayScoreCalculator.compute(results(listOf(100, 66, 55, 65, 71, 60, 62, 54, 25, 35, 45, 98, 89, 91, 71, 24, 55, 14, 72, 100, 59, 38, 15, 65)), tz)!!, 1e-9, "nohazard_94")
        // nohazard_95
        assertEquals(54.87647058823529, DayScoreCalculator.compute(results(listOf(11, 41, 11, 85, 20, 89, 78, 14, 45, 32, 14, 49, 52, 37, 12, 78, 25, 41, 95, 48, 26, 100, 58, 100)), tz)!!, 1e-9, "nohazard_95")
        // nohazard_96
        assertEquals(60.92436974789916, DayScoreCalculator.compute(results(listOf(50, 37, 61, 46, 46, 49, 78, 85, 44, 78, 21, 58, 41, 89, 49, 41, 28, 14, 84, 27, 31, 71, 55, 61)), tz)!!, 1e-9, "nohazard_96")
        // nohazard_97
        assertEquals(77.29411764705883, DayScoreCalculator.compute(results(listOf(26, 47, 41, 57, 32, 32, 93, 38, 76, 57, 96, 39, 72, 88, 13, 82, 30, 100, 56, 84, 89, 81, 74, 85)), tz)!!, 1e-9, "nohazard_97")
        // nohazard_98
        assertEquals(57.57731092436974, DayScoreCalculator.compute(results(listOf(32, 79, 30, 63, 98, 28, 77, 37, 90, 39, 38, 88, 62, 55, 21, 40, 54, 50, 64, 36, 70, 15, 58, 79)), tz)!!, 1e-9, "nohazard_98")
        // nohazard_99
        assertEquals(57.93277310924369, DayScoreCalculator.compute(results(listOf(90, 93, 87, 42, 70, 36, 30, 57, 24, 46, 61, 87, 33, 62, 85, 67, 62, 62, 16, 27, 44, 67, 79, 80)), tz)!!, 1e-9, "nohazard_99")
        // nohazard_100
        assertEquals(81.15294117647058, DayScoreCalculator.compute(results(listOf(77, 39, 14, 96, 24, 83, 33, 75, 85, 91, 98, 80, 66, 52, 97, 18, 32, 70, 78, 84, 27, 51, 38, 27)), tz)!!, 1e-9, "nohazard_100")
        // nohazard_101
        assertEquals(77.34285714285714, DayScoreCalculator.compute(results(listOf(97, 67, 80, 23, 74, 12, 95, 80, 64, 32, 48, 37, 93, 91, 87, 60, 67, 94, 73, 52, 71, 25, 57, 65)), tz)!!, 1e-9, "nohazard_101")
        // nohazard_102
        assertEquals(58.2436974789916, DayScoreCalculator.compute(results(listOf(50, 32, 52, 19, 33, 13, 17, 32, 76, 64, 60, 17, 72, 20, 76, 53, 78, 70, 45, 88, 49, 23, 19, 95)), tz)!!, 1e-9, "nohazard_102")
        // nohazard_103
        assertEquals(56.17647058823529, DayScoreCalculator.compute(results(listOf(67, 29, 57, 32, 82, 73, 94, 22, 93, 33, 50, 43, 25, 40, 23, 31, 36, 35, 38, 29, 89, 57, 59, 47)), tz)!!, 1e-9, "nohazard_103")
        // nohazard_104
        assertEquals(56.27647058823529, DayScoreCalculator.compute(results(listOf(70, 40, 69, 81, 13, 72, 15, 59, 46, 30, 72, 30, 16, 69, 36, 21, 13, 66, 78, 35, 84, 86, 82, 73)), tz)!!, 1e-9, "nohazard_104")
        // nohazard_105
        assertEquals(59.33025210084033, DayScoreCalculator.compute(results(listOf(23, 45, 62, 71, 51, 42, 60, 14, 41, 35, 48, 70, 85, 30, 32, 96, 38, 97, 55, 31, 27, 19, 23, 66)), tz)!!, 1e-9, "nohazard_105")
        // nohazard_106
        assertEquals(65.41848739495798, DayScoreCalculator.compute(results(listOf(42, 27, 36, 53, 22, 27, 37, 95, 11, 66, 95, 98, 11, 41, 31, 18, 37, 25, 85, 20, 86, 12, 30, 89)), tz)!!, 1e-9, "nohazard_106")
        // nohazard_107
        assertEquals(73.54285714285714, DayScoreCalculator.compute(results(listOf(67, 70, 71, 64, 57, 58, 56, 13, 62, 42, 93, 57, 84, 86, 82, 54, 62, 83, 85, 37, 67, 41, 40, 96)), tz)!!, 1e-9, "nohazard_107")
        // nohazard_108
        assertEquals(57.304201680672264, DayScoreCalculator.compute(results(listOf(80, 32, 95, 82, 40, 49, 94, 47, 63, 60, 25, 11, 56, 34, 42, 23, 54, 99, 14, 68, 87, 34, 62, 19)), tz)!!, 1e-9, "nohazard_108")
        // nohazard_109
        assertEquals(60.06386554621849, DayScoreCalculator.compute(results(listOf(97, 40, 77, 74, 31, 20, 38, 92, 77, 29, 46, 11, 41, 64, 17, 76, 58, 13, 30, 97, 46, 75, 98, 89)), tz)!!, 1e-9, "nohazard_109")
        // nohazard_110
        assertEquals(72.209243697479, DayScoreCalculator.compute(results(listOf(96, 87, 25, 80, 34, 44, 76, 20, 47, 25, 52, 47, 85, 100, 100, 85, 11, 39, 37, 59, 13, 42, 72, 89)), tz)!!, 1e-9, "nohazard_110")
        // nohazard_111
        assertEquals(60.67226890756303, DayScoreCalculator.compute(results(listOf(82, 20, 44, 78, 32, 77, 21, 36, 62, 32, 27, 87, 60, 54, 83, 66, 23, 30, 34, 98, 50, 64, 92, 24)), tz)!!, 1e-9, "nohazard_111")
        // nohazard_112
        assertEquals(67.0, DayScoreCalculator.compute(results(listOf(98, 41, 48, 41, 94, 28, 97, 62, 78, 55, 18, 53, 88, 79, 19, 24, 98, 32, 11, 71, 86, 24, 70, 63)), tz)!!, 1e-9, "nohazard_112")
        // nohazard_113
        assertEquals(71.68067226890756, DayScoreCalculator.compute(results(listOf(36, 86, 55, 46, 98, 88, 32, 42, 61, 97, 96, 97, 53, 54, 60, 27, 28, 30, 41, 40, 12, 42, 24, 48)), tz)!!, 1e-9, "nohazard_113")
        // nohazard_114
        assertEquals(62.52521008403362, DayScoreCalculator.compute(results(listOf(96, 15, 95, 31, 65, 18, 62, 18, 98, 49, 46, 88, 17, 55, 15, 87, 32, 34, 98, 88, 35, 54, 41, 98)), tz)!!, 1e-9, "nohazard_114")
        // nohazard_115
        assertEquals(60.91008403361345, DayScoreCalculator.compute(results(listOf(87, 32, 63, 56, 93, 71, 78, 56, 24, 89, 50, 66, 26, 81, 55, 73, 15, 66, 31, 35, 68, 58, 30, 62)), tz)!!, 1e-9, "nohazard_115")
        // nohazard_116
        assertEquals(69.63109243697478, DayScoreCalculator.compute(results(listOf(74, 54, 53, 99, 51, 23, 74, 97, 36, 58, 77, 20, 30, 33, 92, 27, 86, 48, 98, 37, 89, 94, 11, 71)), tz)!!, 1e-9, "nohazard_116")
        // nohazard_117
        assertEquals(64.63025210084034, DayScoreCalculator.compute(results(listOf(85, 59, 85, 70, 31, 29, 62, 74, 91, 29, 92, 98, 29, 11, 47, 55, 45, 38, 94, 72, 51, 41, 49, 58)), tz)!!, 1e-9, "nohazard_117")
        // nohazard_118
        assertEquals(68.65714285714286, DayScoreCalculator.compute(results(listOf(96, 22, 36, 58, 15, 86, 54, 58, 58, 15, 14, 75, 82, 86, 11, 83, 12, 15, 73, 72, 83, 68, 28, 83)), tz)!!, 1e-9, "nohazard_118")
        // nohazard_119
        assertEquals(72.0890756302521, DayScoreCalculator.compute(results(listOf(60, 38, 77, 49, 87, 46, 66, 26, 38, 20, 63, 42, 73, 29, 17, 46, 80, 60, 98, 89, 82, 84, 11, 22)), tz)!!, 1e-9, "nohazard_119")
        // hazard_0
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(90, 84, 19, 50, 54, 48, 7, 6, 81, 41, 61, 13, 6, 98, 94, 27, 74, 95, 8, 83, 14, 70, 85, 32)), tz)!!, 1e-9, "hazard_0")
        // hazard_1
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(53, 84, 59, 27, 78, 81, 59, 22, 45, 90, 13, 61, 84, 32, 6, 56, 18, 92, 9, 40, 24, 3, 37, 12)), tz)!!, 1e-9, "hazard_1")
        // hazard_2
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(16, 25, 44, 45, 40, 33, 99, 35, 45, 67, 60, 56, 6, 69, 42, 59, 37, 95, 14, 67, 23, 41, 47, 5)), tz)!!, 1e-9, "hazard_2")
        // hazard_3
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(22, 9, 72, 88, 68, 4, 17, 47, 10, 98, 58, 19, 40, 44, 70, 86, 64, 81, 87, 47, 67, 84, 66, 42)), tz)!!, 1e-9, "hazard_3")
        // hazard_4
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(21, 43, 35, 35, 26, 48, 83, 22, 71, 66, 17, 12, 14, 35, 44, 1, 41, 26, 10, 53, 42, 7, 74, 34)), tz)!!, 1e-9, "hazard_4")
        // hazard_5
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(74, 79, 22, 13, 78, 1, 54, 85, 93, 67, 34, 33, 90, 63, 21, 0, 37, 21, 87, 63, 5, 71, 94, 57)), tz)!!, 1e-9, "hazard_5")
        // hazard_6
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(44, 42, 54, 5, 0, 72, 12, 87, 67, 11, 57, 54, 54, 31, 29, 4, 84, 9, 57, 76, 70, 64, 20, 42)), tz)!!, 1e-9, "hazard_6")
        // hazard_7
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(67, 17, 62, 20, 56, 36, 54, 35, 22, 6, 49, 52, 12, 1, 7, 47, 55, 91, 97, 19, 47, 60, 19, 95)), tz)!!, 1e-9, "hazard_7")
        // hazard_8
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(27, 62, 68, 91, 94, 74, 7, 3, 81, 4, 35, 20, 34, 73, 59, 33, 11, 1, 63, 70, 51, 52, 96, 2)), tz)!!, 1e-9, "hazard_8")
        // hazard_9
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(63, 88, 86, 25, 59, 64, 2, 92, 85, 53, 79, 5, 77, 80, 78, 4, 75, 59, 85, 88, 29, 72, 83, 12)), tz)!!, 1e-9, "hazard_9")
        // hazard_10
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(61, 47, 86, 76, 20, 96, 80, 11, 2, 61, 29, 25, 53, 67, 1, 21, 15, 1, 6, 2, 4, 78, 73, 53)), tz)!!, 1e-9, "hazard_10")
        // hazard_11
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(74, 22, 96, 48, 18, 17, 94, 88, 18, 44, 84, 88, 91, 3, 59, 41, 22, 0, 31, 100, 11, 36, 32, 88)), tz)!!, 1e-9, "hazard_11")
        // hazard_12
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(37, 57, 33, 22, 98, 87, 9, 9, 26, 77, 43, 4, 1, 95, 39, 41, 81, 20, 29, 72, 18, 2, 54, 72)), tz)!!, 1e-9, "hazard_12")
        // hazard_13
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(96, 58, 67, 2, 74, 39, 18, 23, 77, 3, 2, 26, 38, 61, 17, 17, 62, 85, 76, 13, 59, 10, 40, 27)), tz)!!, 1e-9, "hazard_13")
        // hazard_14
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(1, 93, 94, 71, 48, 13, 28, 30, 67, 40, 94, 78, 6, 25, 50, 38, 88, 84, 72, 60, 24, 0, 51, 90)), tz)!!, 1e-9, "hazard_14")
        // hazard_15
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(29, 72, 12, 31, 4, 12, 65, 34, 68, 8, 15, 74, 68, 91, 42, 86, 41, 17, 82, 73, 3, 68, 19, 21)), tz)!!, 1e-9, "hazard_15")
        // hazard_16
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(2, 72, 88, 2, 35, 72, 74, 18, 84, 48, 75, 15, 81, 83, 37, 78, 63, 85, 26, 27, 76, 43, 88, 43)), tz)!!, 1e-9, "hazard_16")
        // hazard_17
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(63, 48, 26, 57, 68, 42, 25, 84, 2, 59, 53, 5, 31, 0, 7, 48, 1, 55, 58, 7, 84, 51, 68, 98)), tz)!!, 1e-9, "hazard_17")
        // hazard_18
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(80, 26, 86, 63, 92, 97, 7, 77, 5, 5, 85, 69, 7, 68, 69, 17, 96, 16, 69, 12, 37, 28, 41, 58)), tz)!!, 1e-9, "hazard_18")
        // hazard_19
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(63, 49, 17, 6, 39, 74, 29, 41, 74, 91, 69, 5, 38, 68, 5, 70, 83, 44, 6, 18, 19, 32, 62, 0)), tz)!!, 1e-9, "hazard_19")
        // hazard_20
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(68, 27, 67, 20, 34, 37, 85, 45, 14, 99, 13, 0, 71, 24, 71, 3, 97, 40, 75, 55, 8, 57, 84, 90)), tz)!!, 1e-9, "hazard_20")
        // hazard_21
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(15, 14, 48, 34, 82, 61, 17, 82, 89, 64, 96, 70, 2, 1, 86, 79, 32, 55, 6, 87, 51, 25, 8, 42)), tz)!!, 1e-9, "hazard_21")
        // hazard_22
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(2, 77, 90, 35, 80, 12, 74, 7, 79, 88, 49, 98, 6, 35, 14, 69, 90, 60, 73, 100, 70, 54, 100, 69)), tz)!!, 1e-9, "hazard_22")
        // hazard_23
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(90, 3, 38, 48, 90, 3, 19, 65, 61, 94, 5, 14, 85, 60, 15, 74, 67, 9, 70, 78, 50, 10, 90, 63)), tz)!!, 1e-9, "hazard_23")
        // hazard_24
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(74, 2, 12, 40, 10, 52, 82, 39, 94, 71, 45, 11, 18, 91, 57, 98, 34, 48, 67, 15, 79, 80, 82, 47)), tz)!!, 1e-9, "hazard_24")
        // hazard_25
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(31, 55, 3, 71, 17, 53, 37, 19, 76, 65, 30, 35, 39, 64, 21, 9, 61, 74, 64, 72, 5, 59, 74, 35)), tz)!!, 1e-9, "hazard_25")
        // hazard_26
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(2, 39, 77, 46, 7, 6, 5, 67, 38, 4, 94, 92, 95, 3, 19, 87, 11, 75, 50, 64, 1, 2, 25, 40)), tz)!!, 1e-9, "hazard_26")
        // hazard_27
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(20, 71, 85, 3, 63, 60, 37, 50, 96, 31, 70, 77, 5, 38, 65, 28, 48, 84, 16, 59, 51, 80, 15, 3)), tz)!!, 1e-9, "hazard_27")
        // hazard_28
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(46, 60, 45, 81, 81, 53, 80, 63, 15, 69, 85, 55, 19, 22, 49, 32, 92, 33, 39, 7, 100, 45, 96, 91)), tz)!!, 1e-9, "hazard_28")
        // hazard_29
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(87, 30, 23, 34, 85, 13, 57, 53, 43, 21, 60, 24, 57, 49, 93, 2, 55, 98, 73, 4, 72, 96, 98, 46)), tz)!!, 1e-9, "hazard_29")
        // hazard_30
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(19, 49, 1, 10, 16, 99, 32, 96, 47, 9, 18, 67, 69, 35, 48, 32, 48, 64, 15, 60, 42, 54, 97, 92)), tz)!!, 1e-9, "hazard_30")
        // hazard_31
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(91, 1, 31, 72, 50, 77, 49, 78, 93, 85, 32, 28, 34, 30, 44, 58, 94, 50, 91, 73, 11, 9, 13, 35)), tz)!!, 1e-9, "hazard_31")
        // hazard_32
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(95, 42, 29, 48, 87, 79, 85, 77, 23, 63, 51, 46, 4, 57, 85, 39, 4, 90, 6, 54, 14, 67, 89, 25)), tz)!!, 1e-9, "hazard_32")
        // hazard_33
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(21, 21, 51, 70, 26, 7, 8, 48, 42, 9, 99, 44, 21, 80, 48, 51, 97, 47, 26, 13, 87, 27, 0, 63)), tz)!!, 1e-9, "hazard_33")
        // hazard_34
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(27, 31, 28, 37, 0, 21, 100, 27, 39, 98, 6, 0, 65, 88, 54, 65, 5, 60, 60, 50, 95, 24, 20, 45)), tz)!!, 1e-9, "hazard_34")
        // hazard_35
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(47, 3, 48, 69, 66, 75, 77, 11, 24, 52, 65, 79, 14, 37, 72, 61, 88, 15, 61, 24, 63, 78, 43, 61)), tz)!!, 1e-9, "hazard_35")
        // hazard_36
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(39, 87, 5, 72, 75, 65, 26, 58, 4, 2, 87, 44, 29, 2, 49, 52, 49, 17, 86, 29, 38, 95, 2, 100)), tz)!!, 1e-9, "hazard_36")
        // hazard_37
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(18, 81, 28, 97, 44, 11, 40, 56, 90, 7, 63, 89, 72, 65, 28, 90, 61, 69, 17, 4, 28, 41, 70, 17)), tz)!!, 1e-9, "hazard_37")
        // hazard_38
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(26, 95, 51, 54, 65, 27, 77, 45, 68, 8, 96, 27, 63, 88, 91, 43, 49, 31, 89, 92, 32, 29, 76, 58)), tz)!!, 1e-9, "hazard_38")
        // hazard_39
        assertEquals(35.0, DayScoreCalculator.compute(results(listOf(31, 32, 55, 6, 45, 39, 35, 41, 49, 60, 39, 50, 36, 59, 53, 49, 93, 8, 15, 76, 34, 48, 28, 88)), tz)!!, 1e-9, "hazard_39")
        // highlight_0
        assertEquals(77.82857142857144, DayScoreCalculator.compute(results(listOf(60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 60, 75, 90, 80, 90, 82, 87, 60, 60, 60, 60)), tz)!!, 1e-9, "highlight_0")
        // highlight_1
        assertEquals(82.34285714285714, DayScoreCalculator.compute(results(listOf(59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 85, 89, 94, 100, 75, 92, 59, 59, 59, 59)), tz)!!, 1e-9, "highlight_1")
        // highlight_2
        assertEquals(62.95294117647059, DayScoreCalculator.compute(results(listOf(59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 87, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59)), tz)!!, 1e-9, "highlight_2")
        // highlight_3
        assertEquals(59.15294117647059, DayScoreCalculator.compute(results(listOf(46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 89, 89, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46)), tz)!!, 1e-9, "highlight_3")
        // highlight_4
        assertEquals(68.25882352941176, DayScoreCalculator.compute(results(listOf(66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 66, 82, 66, 66, 66, 66, 66, 66, 66)), tz)!!, 1e-9, "highlight_4")
        // highlight_5
        assertEquals(80.57142857142857, DayScoreCalculator.compute(results(listOf(65, 65, 65, 65, 65, 65, 81, 86, 76, 78, 89, 99, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65)), tz)!!, 1e-9, "highlight_5")
        // highlight_6
        assertEquals(54.694117647058825, DayScoreCalculator.compute(results(listOf(42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 76, 91, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42)), tz)!!, 1e-9, "highlight_6")
        // highlight_7
        assertEquals(68.27394957983194, DayScoreCalculator.compute(results(listOf(40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 100, 80, 76, 78, 88, 40, 40, 40)), tz)!!, 1e-9, "highlight_7")
        // highlight_8
        assertEquals(59.64705882352941, DayScoreCalculator.compute(results(listOf(52, 52, 52, 52, 52, 52, 52, 76, 78, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52)), tz)!!, 1e-9, "highlight_8")
        // highlight_9
        assertEquals(74.83529411764705, DayScoreCalculator.compute(results(listOf(61, 61, 61, 61, 61, 61, 94, 75, 98, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61)), tz)!!, 1e-9, "highlight_9")
        // highlight_10
        assertEquals(58.305882352941175, DayScoreCalculator.compute(results(listOf(45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 84, 93, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45, 45)), tz)!!, 1e-9, "highlight_10")
        // highlight_11
        assertEquals(76.51428571428572, DayScoreCalculator.compute(results(listOf(42, 42, 42, 42, 42, 42, 42, 42, 42, 96, 97, 88, 75, 76, 98, 42, 42, 42, 42, 42, 42, 42, 42, 42)), tz)!!, 1e-9, "highlight_11")
        // highlight_12
        assertEquals(81.0924369747899, DayScoreCalculator.compute(results(listOf(65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 65, 87, 99, 96, 87, 65, 65, 65, 65, 65, 65)), tz)!!, 1e-9, "highlight_12")
        // highlight_13
        assertEquals(60.258823529411764, DayScoreCalculator.compute(results(listOf(40, 40, 40, 40, 40, 82, 84, 77, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40)), tz)!!, 1e-9, "highlight_13")
        // highlight_14
        assertEquals(70.11764705882352, DayScoreCalculator.compute(results(listOf(68, 68, 68, 68, 68, 68, 68, 83, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68, 68)), tz)!!, 1e-9, "highlight_14")
        // highlight_15
        assertEquals(56.96470588235294, DayScoreCalculator.compute(results(listOf(54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 75, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54)), tz)!!, 1e-9, "highlight_15")
        // highlight_16
        assertEquals(75.22689075630251, DayScoreCalculator.compute(results(listOf(58, 58, 58, 58, 58, 58, 58, 58, 58, 58, 58, 58, 77, 88, 94, 86, 58, 58, 58, 58, 58, 58, 58, 58)), tz)!!, 1e-9, "highlight_16")
        // highlight_17
        assertEquals(82.91428571428571, DayScoreCalculator.compute(results(listOf(61, 61, 61, 61, 61, 61, 98, 89, 77, 87, 100, 94, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61)), tz)!!, 1e-9, "highlight_17")
        // highlight_18
        assertEquals(63.11764705882353, DayScoreCalculator.compute(results(listOf(61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 61, 76, 61, 61, 61, 61, 61, 61, 61)), tz)!!, 1e-9, "highlight_18")
        // highlight_19
        assertEquals(68.16806722689076, DayScoreCalculator.compute(results(listOf(42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 42, 75, 76, 93, 99, 42, 42, 42, 42, 42, 42, 42)), tz)!!, 1e-9, "highlight_19")
        // highlight_20
        assertEquals(53.35294117647059, DayScoreCalculator.compute(results(listOf(47, 47, 47, 47, 47, 47, 92, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47)), tz)!!, 1e-9, "highlight_20")
        // highlight_21
        assertEquals(61.90588235294118, DayScoreCalculator.compute(results(listOf(46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 97, 99, 46, 46, 46, 46, 46, 46, 46, 46)), tz)!!, 1e-9, "highlight_21")
        // highlight_22
        assertEquals(81.25714285714287, DayScoreCalculator.compute(results(listOf(64, 64, 64, 64, 64, 64, 64, 64, 96, 86, 87, 84, 89, 84, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64)), tz)!!, 1e-9, "highlight_22")
        // highlight_23
        assertEquals(72.8235294117647, DayScoreCalculator.compute(results(listOf(55, 55, 55, 55, 55, 55, 55, 55, 76, 87, 75, 100, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55)), tz)!!, 1e-9, "highlight_23")
        // highlight_24
        assertEquals(58.38823529411765, DayScoreCalculator.compute(results(listOf(46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 95, 78, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46)), tz)!!, 1e-9, "highlight_24")
        // highlight_25
        assertEquals(82.0, DayScoreCalculator.compute(results(listOf(69, 69, 69, 69, 69, 69, 69, 69, 69, 81, 75, 94, 99, 75, 79, 69, 69, 69, 69, 69, 69, 69, 69, 69)), tz)!!, 1e-9, "highlight_25")
        // highlight_26
        assertEquals(68.7764705882353, DayScoreCalculator.compute(results(listOf(50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50, 100, 85, 79, 50, 50, 50, 50, 50, 50, 50, 50, 50)), tz)!!, 1e-9, "highlight_26")
        // highlight_27
        assertEquals(69.7764705882353, DayScoreCalculator.compute(results(listOf(51, 51, 51, 51, 51, 51, 51, 51, 51, 89, 88, 90, 51, 51, 51, 51, 51, 51, 51, 51, 51, 51, 51, 51)), tz)!!, 1e-9, "highlight_27")
        // highlight_28
        assertEquals(80.65714285714286, DayScoreCalculator.compute(results(listOf(48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 48, 95, 95, 97, 95, 91, 100, 48, 48, 48, 48, 48)), tz)!!, 1e-9, "highlight_28")
        // highlight_29
        assertEquals(80.94285714285715, DayScoreCalculator.compute(results(listOf(46, 46, 46, 46, 46, 46, 88, 76, 100, 97, 100, 84, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46)), tz)!!, 1e-9, "highlight_29")
        // highlight_30
        assertEquals(69.78151260504202, DayScoreCalculator.compute(results(listOf(43, 43, 43, 43, 43, 89, 98, 84, 90, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43, 43)), tz)!!, 1e-9, "highlight_30")
        // highlight_31
        assertEquals(74.62184873949579, DayScoreCalculator.compute(results(listOf(47, 47, 47, 47, 47, 47, 47, 47, 47, 97, 90, 99, 99, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47)), tz)!!, 1e-9, "highlight_31")
        // highlight_32
        assertEquals(45.08235294117647, DayScoreCalculator.compute(results(listOf(40, 40, 40, 40, 40, 40, 76, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40)), tz)!!, 1e-9, "highlight_32")
        // highlight_33
        assertEquals(74.28991596638656, DayScoreCalculator.compute(results(listOf(52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 92, 82, 91, 76, 85, 52, 52, 52)), tz)!!, 1e-9, "highlight_33")
        // highlight_34
        assertEquals(53.63529411764706, DayScoreCalculator.compute(results(listOf(47, 47, 47, 47, 47, 47, 47, 47, 94, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47, 47)), tz)!!, 1e-9, "highlight_34")
        // highlight_35
        assertEquals(56.65882352941176, DayScoreCalculator.compute(results(listOf(52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52, 85, 52, 52, 52, 52, 52, 52, 52, 52, 52, 52)), tz)!!, 1e-9, "highlight_35")
        // highlight_36
        assertEquals(80.57142857142857, DayScoreCalculator.compute(results(listOf(55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 100, 93, 88, 91, 75, 82, 55, 55, 55, 55, 55, 55)), tz)!!, 1e-9, "highlight_36")
        // highlight_37
        assertEquals(74.6218487394958, DayScoreCalculator.compute(results(listOf(54, 54, 54, 54, 54, 54, 54, 54, 54, 54, 93, 82, 97, 90, 54, 54, 54, 54, 54, 54, 54, 54, 54, 54)), tz)!!, 1e-9, "highlight_37")
        // highlight_38
        assertEquals(58.24705882352941, DayScoreCalculator.compute(results(listOf(55, 55, 55, 55, 55, 55, 78, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55, 55)), tz)!!, 1e-9, "highlight_38")
        // highlight_39
        assertEquals(58.694117647058825, DayScoreCalculator.compute(results(listOf(46, 46, 46, 46, 46, 46, 100, 75, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46, 46)), tz)!!, 1e-9, "highlight_39")
    }
}
