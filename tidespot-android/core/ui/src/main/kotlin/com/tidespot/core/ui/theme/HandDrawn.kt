package com.tidespot.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.R
import kotlin.math.min
import kotlin.random.Random

/**
 * 手绘 A · 复古户外的「质感层」——把设计稿里 SVG 滤镜表达的纸张颗粒、套印错位、
 * 非平滑描边，用 Compose 的无状态 Modifier 近似还原。
 *
 * 设计来源：design-handdrawn/generator/engine.py 的 svg_defs() / grain_overlay()
 *   - feTurbulence fractalNoise seed=11、grain=0.16 → paper_grain_*.png tile（由
 *     design-handdrawn/generator/gen_android_assets.py 生成，可复现）
 *   - feDisplacementMap 描边毛边 → handDrawnOutline() 的确定性抖动轮廓
 *   - 套印错位色块 → printMisregister()
 *
 * 边界（重要）：
 *  - 本文件只提供绘制能力，不含任何皮肤配色常量；颜色一律由调用方从 MaterialTheme 取，
 *    因此皮肤 / 水体 / 明暗三轴依然正交（见 SKIN-DESIGN-SPEC.md）。
 *  - 全部为纯绘制 Modifier，不消费 pointer input、不持有状态、不改变测量尺寸，
 *    对交互与布局零影响。
 *  - 不触碰 ScoreBands.kt / ConfidenceBadge.kt 的冻结语义色，也不重定义任何文案。
 */

/**
 * 纸纹平铺画笔。tile 放 drawable-nodpi（96×96，不随密度缩放），
 * 用 ImageShader 的 Repeated 模式无缝平铺；浅色用深墨颗粒、深色用暖白颗粒。
 */
@Composable
fun rememberPaperGrainBrush(darkTheme: Boolean): ShaderBrush {
    val bitmap: ImageBitmap = ImageBitmap.imageResource(
        if (darkTheme) R.drawable.paper_grain_dark else R.drawable.paper_grain_light,
    )
    return remember(bitmap) {
        ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * 全屏纸张颗粒（CHG-93）：画在**内容之下**，而非盖在所有内容之上。
 * 旧实现用 drawWithContent 把颗粒覆盖卡片与文字，导致文字发糊、实心卡片被压灰
 * （SKIN-DEV-STANDARD 反模式 #11 同源问题）。改 drawBehind 后颗粒只作用于纸底，
 * 卡片与文字保持清晰；颗粒 alpha 上限 0.16，运行时再由 grainStrength 收敛。
 * 纯绘制节点不拦截触摸、不消费 pointer input、不改变测量尺寸。
 */
fun Modifier.paperGrain(brush: ShaderBrush, strength: Float = 1f): Modifier =
    this.drawBehind {
        drawRect(brush = brush, alpha = strength)
    }

/**
 * 套印错位：在主体背后画一个向右下偏移的低透明度色块，模拟丝网版画没对准的第二版。
 * 主体（Surface / Card）会盖住中间，只在右下边缘露出一点错位边。
 */
fun Modifier.printMisregister(
    color: Color,
    offsetX: Dp = 3.dp,
    offsetY: Dp = 3.dp,
    corner: Dp = 16.dp,
    alpha: Float = 0.42f,
): Modifier = this.drawBehind {
    val ox = offsetX.toPx()
    val oy = offsetY.toPx()
    val w = size.width - ox
    val h = size.height - oy
    if (w <= 0f || h <= 0f) return@drawBehind
    drawRoundRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(ox, oy),
        size = Size(w, h),
        cornerRadius = CornerRadius(corner.toPx().coerceAtMost(min(w, h) / 2f)),
    )
}

/**
 * 手绘描边：沿圆角矩形边界采样出一组带确定性抖动的多边形点，连成不规则闭合轮廓。
 * passes=2 表示「手工描了两遍」，两条线略微错位，比单条平滑线更接近版画毛边。
 * 抖动由固定 seed 生成，重组不会闪烁。
 */
fun Modifier.handDrawnOutline(
    color: Color,
    strokeWidth: Dp = 1.4.dp,
    corner: Dp = 12.dp,
    amplitude: Dp = 1.1.dp,
    seed: Int = 7,
    passes: Int = 2,
): Modifier = this.drawWithContent {
    drawContent()
    val sw = strokeWidth.toPx()
    val amp = amplitude.toPx()
    val cornerPx = corner.toPx()
    // 先画一条稳定实墨底线，保证纸卡有 2.2dp 的清晰轮廓；
    // 再叠两遍低透明度抖动线，只负责「手工描边」的毛边，不承担可读性。
    val insetPx = 1f
    drawRoundRect(
        color = color.copy(alpha = 0.86f),
        topLeft = Offset(insetPx, insetPx),
        size = Size(size.width - insetPx * 2f, size.height - insetPx * 2f),
        cornerRadius = CornerRadius(cornerPx),
        style = Stroke(width = sw),
    )
    repeat(passes) { pass ->
        val points = jitteredOutline(size, cornerPx, amp, Random(seed + pass * 31))
        drawPath(
            path = points,
            color = color.copy(alpha = if (pass == 0) 0.30f else 0.16f),
            style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** 生成抖动轮廓路径；四角用三点切角近似圆角，逐点加小幅确定性偏移。 */
private fun DrawScope.jitteredOutline(
    size: Size,
    corner: Float,
    amp: Float,
    rnd: Random,
): Path {
    val inset = 1f
    val l = inset
    val t = inset
    val r = size.width - inset
    val b = size.height - inset
    val c = corner.coerceAtMost(min(r - l, b - t) / 2f).coerceAtLeast(4f)
    val steps = 12

    val outline = mutableListOf<Offset>()
    // 上边
    for (i in 0..steps) outline += Offset(l + c + (r - l - 2f * c) * i / steps, t)
    // 右上切角
    outline += Offset(r - c * 0.30f, t + c * 0.08f)
    outline += Offset(r - c * 0.08f, t + c * 0.30f)
    // 右边
    for (i in 1..steps) outline += Offset(r, t + c + (b - t - 2f * c) * i / steps)
    // 右下切角
    outline += Offset(r - c * 0.08f, b - c * 0.30f)
    outline += Offset(r - c * 0.30f, b - c * 0.08f)
    // 下边（反向）
    for (i in 0..steps) outline += Offset(r - c - (r - l - 2f * c) * i / steps, b)
    // 左下切角
    outline += Offset(l + c * 0.30f, b - c * 0.08f)
    outline += Offset(l + c * 0.08f, b - c * 0.30f)
    // 左边（反向）
    for (i in 1..steps) outline += Offset(l, b - c - (b - t - 2f * c) * i / steps)
    // 左上切角
    outline += Offset(l + c * 0.08f, t + c * 0.30f)
    outline += Offset(l + c * 0.30f, t + c * 0.08f)

    return Path().apply {
        outline.forEachIndexed { i, p ->
            val j = Offset(
                p.x + (rnd.nextFloat() * 2f - 1f) * amp,
                p.y + (rnd.nextFloat() * 2f - 1f) * amp,
            )
            if (i == 0) moveTo(j.x, j.y) else lineTo(j.x, j.y)
        }
        close()
    }
}
