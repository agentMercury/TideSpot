package com.tidespot.core.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tidespot.core.ui.R

/**
 * CHG-93 / 路径 B：全局统一的「复古票据卡」视觉骨架。
 *
 * 客户 2026-11-15 裁决「路径 B」——结构层一次性对齐手绘 A 设计稿，作为**所有皮肤共用**的
 * 全局骨架；换肤仍只换 token（`TideSpotSkin` 不加 layout 字段，`SKIN-DESIGN-SPEC.md` 的
 * 「结构/文案/交互/冻结契约全部共用」不变）。颜色一律从 `MaterialTheme.colorScheme` 取，
 * 描边/套印的开关与强度从 `LocalSkin.current.texture` 取，因此 D 皮肤仍是自己的视觉语言。
 *
 * 设计来源：`A-vintage-outdoor/screens/settings.png` 等稿的
 * 「2.2px 藏蓝描边 + 2dp/3dp 偏移阴影 + 非对称圆角 + 虚线分隔 + 小鱼标题」。
 * 旧实现里这些效果只在 `DetailScreen` 三处调用，其余三页是裸 M3 Card。
 *
 * 边界：全部是绘制 / 布局装饰，**不新增功能入口、不改交互语义、不新增文案**，
 * 因此不触发双端契约 N-4，也不影响 36 项入口红线。
 */

/**
 * 卡片质感：偏移套印阴影 + 手绘双线描边。颜色随当前皮肤与水体的 ColorScheme 变化。
 * 只在 `texture` 打开对应开关时挂载，D 皮肤可自行关闭。
 */
@Composable
fun Modifier.vintageCard(corner: Dp = 16.dp): Modifier {
    val texture = LocalSkin.current.texture
    val scheme = MaterialTheme.colorScheme
    var result: Modifier = this
    if (texture.printMisregister) {
        result = result.printMisregister(scheme.tertiary, corner = corner)
    }
    if (texture.handDrawnOutline) {
        result = result.handDrawnOutline(
            color = scheme.primary.copy(alpha = 0.78f),
            strokeWidth = 2.2.dp,
            corner = corner,
        )
    }
    return result
}

/** 虚线分隔（设计稿的票据 perforation；替代 M3 HorizontalDivider 的实线）。 */
@Composable
fun VintageDashedDivider(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
    Canvas(modifier.height(1.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 9f)),
        )
    }
}

/** 版块标题：小鱼母题 + 衬线标题 + 右侧延伸虚线。 */
@Composable
fun VintageSectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_fish_vintage),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(width = 22.dp, height = 12.dp),
        )
        Text(text, style = MaterialTheme.typography.titleMedium)
        VintageDashedDivider(Modifier.weight(1f))
    }
}

/** 独立票据卡：非对称圆角（取皮肤 `shapes.medium`）+ 描边 + 套印 + 虚线标题。 */
@Composable
fun VintageSectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().vintageCard(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (title != null) VintageSectionTitle(title)
            content()
        }
    }
}

/** 页头：衬线大标题 + 品牌副标题 + 虚线收口。副标题由调用方传数据值，不新增文案资源。 */
@Composable
fun VintagePageHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VintageDashedDivider(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
