package com.tidespot.core.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.R
import com.tidespot.core.ui.band.ScoreBand
import com.tidespot.core.ui.format.Format
import com.tidespot.core.ui.strings.localTideSpotStrings
import kotlin.random.Random

/** 图表模式：LINE=折线（数值维度）/ BAR=柱（钓鱼指数，5 档色带 + ≥80 窗口横条，§8.2-2）。 */
enum class HourChartMode { LINE, BAR }

/**
 * 手绘笔触：把一条线段细分成若干点并加确定性抖动，模拟版画毛边。
 * 只作用于绘制路径；数值、断点、柱色、窗口段全部沿用原逻辑。固定 seed 保证重组不闪烁。
 */
private fun DrawScope.sketchyLine(
    from: Offset,
    to: Offset,
    color: Color,
    strokeWidth: Float,
    seed: Int,
    amplitude: Float = 1.1f,
    segments: Int = 4,
) {
    val rnd = Random(seed)
    val path = Path().apply {
        for (i in 0..segments) {
            val t = i.toFloat() / segments
            val base = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
            val x = base.x + (rnd.nextFloat() - 0.5f) * 2f * amplitude
            val y = base.y + (rnd.nextFloat() - 0.5f) * 2f * amplitude
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * 24 点逐小时图表（§7.1 Compose Canvas 自绘，禁第三方图表库；§8.2-2）。
 *
 * 红线：
 * - **null 断线不连线**（LINE）：断段由 [ChartMath.splitSegments] 决定，跨 null 绝不连线、绝不画 0；
 * - **null 不画柱**（BAR）：缺测小时无柱体（不是 0 高度柱）；
 * - y 轴自适应带单位（[ChartMath.yAxis]，单位由 [unit] 提供）；
 * - 点选小时浮层（[selectedHour] + [onHourSelected]，再点同一小时取消）；
 * - BAR 模式：柱色 = 5 档色带（§6.3），[windowHours]（≥80 段）叠「窗口」横条。
 *
 * 可测逻辑全部在 [ChartMath]（null 断点/断段/窗口段单测），本组件只做绘制。
 */
@Composable
fun HourChart(
    values: List<Double?>,
    modifier: Modifier = Modifier,
    mode: HourChartMode = HourChartMode.LINE,
    unit: String = "",
    selectedHour: Int? = null,
    onHourSelected: (Int?) -> Unit = {},
    windowHours: Set<Int> = emptySet(),
    /** 浮层数值文本（调用方可注入换算后的显示格式；缺省按源值 1 位小数 + 单位）。 */
    tooltipFormatter: (Double?) -> String = { Format.valueUnit(it, unit) },
    /**
     * D14 x 轴当地小时标签（按选点时区注入；index 0..n-1 → 当地 0–23 小时数）。
     * null = 沿用旧下标标注（零行为漂移；有日切片的调用方一律传当地小时）。
     */
    localHours: List<Int>? = null,
    /** 手绘 A：开启后网格线 / 折线走抖动路径、柱体叠手绘描边；默认关闭，其他页零变化。 */
    handDrawn: Boolean = false,
) {
    // D04：BAR 型（钓鱼指数）固定 0–100 轴，不随当日数据缩放；LINE 仍自适应
    val axis = remember(values, mode) {
        if (mode == HourChartMode.BAR) ChartMath.barYAxis() else ChartMath.yAxis(values)
    }
    val segments = remember(values) { ChartMath.splitSegments(values) }
    val windowSegments = remember(windowHours, values.size) {
        ChartMath.windowSegments(windowHours, values.size)
    }
    val lineColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.outline
    val tooltipBg = MaterialTheme.colorScheme.inverseSurface
    val tooltipFg = MaterialTheme.colorScheme.inverseOnSurface
    val axisTextStyle = MaterialTheme.typography.labelSmall
    val tooltipTextStyle = MaterialTheme.typography.labelMedium
    val axisLabelStyle = axisTextStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val tooltipLabelStyle = tooltipTextStyle.copy(color = tooltipFg)
    val textMeasurer = rememberTextMeasurer()
    val strings = localTideSpotStrings()
    // OPT-V115-01 #2（ts_window_bar）：「窗口」横条文字标签（段宽足够时绘制，不足则仅保留细条）
    val windowLabel = stringResource(R.string.ts_window_bar)
    val windowLabelLayout = remember(windowLabel, axisLabelStyle) {
        textMeasurer.measure(windowLabel, axisLabelStyle)
    }

    val semantic = remember(values, mode, unit, selectedHour, strings) {
        val validCount = values.count { it != null }
        val missingCount = values.size - validCount
        val sel = selectedHour?.let {
            strings.chartHourTooltipSuffix(it + 1, tooltipFormatter(values.getOrNull(it)))
        } ?: ""
        val base = when (mode) {
            HourChartMode.LINE ->
                strings.chartHourDescription(unit, validCount) +
                    (if (missingCount > 0) strings.chartMissingSuffix(missingCount) else "")
            HourChartMode.BAR ->
                strings.chartScoreDescription +
                    (if (windowHours.isNotEmpty()) strings.chartWindowSuffix(windowHours.size) else "")
        }
        strings.chartHint(base, sel)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = semantic }
            .pointerInput(values, selectedHour) {
                detectTapGestures { ofs ->
                    if (values.isEmpty()) return@detectTapGestures
                    val h = (ofs.x / size.width * values.size).toInt().coerceIn(0, values.size - 1)
                    onHourSelected(if (h == selectedHour) null else h)
                }
            },
    ) {
        val leftPad = 34.dp.toPx()
        val bottomPad = 18.dp.toPx()
        val chartW = size.width - leftPad - 8.dp.toPx()
        val chartH = size.height - bottomPad - 8.dp.toPx()
        val n = values.size.coerceAtLeast(1)
        fun xOf(hour: Int): Float = leftPad + chartW * (hour + 0.5f) / n
        fun yOf(v: Double): Float = 8.dp.toPx() + chartH * (1f - axis.normalized(v).toFloat())

        // y 轴 + 刻度
        axis.ticks.forEachIndexed { tickIndex, tick ->
            val y = yOf(tick)
            val gridStart = Offset(leftPad, y)
            val gridEnd = Offset(size.width - 8.dp.toPx(), y)
            if (handDrawn) {
                sketchyLine(gridStart, gridEnd, axisColor, 1f, seed = 100 + tickIndex, amplitude = 0.9f, segments = 6)
            } else {
                drawLine(axisColor, gridStart, gridEnd, strokeWidth = 1f)
            }
            drawText(
                textMeasurer,
                text = Format.value(tick, 1),
                topLeft = Offset(0f, y - 8.dp.toPx()),
                style = axisLabelStyle as TextStyle,
            )
        }
        // x 轴小时标注（每 3 小时；D14：优先当地小时标签，未注入时回退下标）
        for (h in 0 until n step 3) {
            val hourLabel = localHours?.getOrNull(h)?.toString() ?: "$h"
            drawText(
                textMeasurer,
                text = hourLabel,
                topLeft = Offset(xOf(h) - 8.dp.toPx(), size.height - bottomPad + 2.dp.toPx()),
                style = axisLabelStyle as TextStyle,
            )
        }

        when (mode) {
            HourChartMode.LINE -> {
                // null 断线不连线：只在段内（连续非 null）绘制折线
                segments.forEach { seg ->
                    val pts = ChartMath.segmentPoints(values, seg)
                    for (i in 0 until pts.size - 1) {
                        val (h1, v1) = pts[i]
                        val (h2, v2) = pts[i + 1]
                        val start = Offset(xOf(h1), yOf(v1))
                        val end = Offset(xOf(h2), yOf(v2))
                        if (handDrawn) {
                            sketchyLine(start, end, lineColor, 3f, seed = 200 + h1, amplitude = 1.3f, segments = 4)
                        } else {
                            drawLine(
                                color = lineColor,
                                start = start,
                                end = end,
                                strokeWidth = 3f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                    pts.forEach { (h, v) ->
                        drawCircle(lineColor, radius = 3f, center = Offset(xOf(h), yOf(v)))
                    }
                }
            }
            HourChartMode.BAR -> {
                // 柱：非 null 才画（缺测小时无柱体，禁止 0 高度柱）；柱色 = 5 档色带
                values.forEachIndexed { h, v ->
                    if (v != null) {
                        val score = v.toInt().coerceIn(0, 100)
                        val barW = chartW / n * 0.7f
                        val top = yOf(v)
                        val barLeft = xOf(h) - barW / 2
                        val barRight = barLeft + barW
                        val barBottom = 8.dp.toPx() + chartH
                        drawRect(
                            color = Color(ScoreBand.colorOf(score)),
                            topLeft = Offset(barLeft, top),
                            size = Size(barW, barBottom - top),
                        )
                        if (handDrawn) {
                            // 仅在冻结柱色之上叠一条手绘描边，不改变柱体数值 / 颜色 / 缺测逻辑
                            val rnd = Random(300 + h)
                            fun jx(v: Float) = v + (rnd.nextFloat() - 0.5f) * 2.4f
                            fun jy(v: Float) = v + (rnd.nextFloat() - 0.5f) * 2.4f
                            drawPath(
                                path = Path().apply {
                                    moveTo(jx(barLeft), jy(top))
                                    lineTo(jx(barRight), jy(top))
                                    lineTo(jx(barRight), jy(barBottom))
                                    lineTo(jx(barLeft), jy(barBottom))
                                    close()
                                },
                                color = axisColor.copy(alpha = 0.55f),
                                style = Stroke(width = 1.4f, join = StrokeJoin.Round),
                            )
                        }
                    }
                }
                // ≥80 段叠「窗口」横条（OPT-V115-01 #2：段宽足够时在条内绘制 ts_window_bar 标签）
                windowSegments.forEach { seg ->
                    val x1 = xOf(seg.startHour) - chartW / n / 2
                    val x2 = xOf(seg.endHourInclusive) + chartW / n / 2
                    val labelW = windowLabelLayout.size.width
                    val fits = (x2 - x1) >= labelW + 8f
                    val stripH = if (fits) 14.dp.toPx() else 4.dp.toPx()
                    drawRect(
                        color = Color(ScoreBand.EXPLOSIVE.argb), // D14/19：色值收敛 ScoreBand token（单源）
                        topLeft = Offset(x1, 2.dp.toPx()),
                        size = Size(x2 - x1, stripH),
                    )
                    if (fits) {
                        drawText(
                            windowLabelLayout,
                            color = Color.White,
                            topLeft = Offset(
                                x1 + (x2 - x1 - labelW) / 2f,
                                2.dp.toPx() + (stripH - windowLabelLayout.size.height) / 2f,
                            ),
                        )
                    }
                }
            }
        }

        // 点选浮层
        if (selectedHour != null) {
            val v = values.getOrNull(selectedHour)
            val text = if (v == null) strings.noDataHour else tooltipFormatter(v)
            val measured = textMeasurer.measure(text, tooltipLabelStyle)
            val bx = (xOf(selectedHour) - measured.size.width / 2f)
                .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
            val by = 8.dp.toPx()
            drawRect(
                color = tooltipBg,
                topLeft = Offset(bx, by),
                size = Size(measured.size.width + 12f, measured.size.height + 8f),
            )
            drawText(measured, topLeft = Offset(bx + 6f, by + 4f))
        }
    }
}


