package com.tidespot.core.ui.breakdown

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.domain.algorithm.FactorId
import com.tidespot.core.ui.band.ScoreBand
import com.tidespot.core.ui.format.Format

/** 因子行（F-3.4：名称/权重/得分/贡献条）。 */
data class FactorRow(
    /** domain FactorScore.id（字符串，如 "OXYGEN"）。 */
    val id: String,
    /** 中文名（溶氧/气压/Solunar/…，§6.4）。 */
    val name: String,
    /** 0–1 子分（未启用/缺测 → null，从分母剔除的因子不出现假 0 分）。 */
    val sub: Double?,
    /** 权重 w_k（缺测降级后的有效权重）。 */
    val weight: Double,
    /** 触发明细（如「阵风 40 km/h」），可为 null。 */
    val detail: String? = null,
)

/** ΠV 明细行（F-3.4：触发项与乘子值）。 */
data class VetoRow(
    /** 触发项描述（vetoReasons 原文）。 */
    val reason: String,
    /** 乘子值 V（0–1；domain 不透出时由调用侧重算，null → 仅显示触发项）。 */
    val multiplier: Double? = null,
)

/** 原始输入行（§8.2-3：Tw、DO_p、P、ΔP3、ΔP24、风/阵风、雨、云、WMO、浪、潮位、dh/dt、流速…）。 */
data class RawInputRow(val label: String, val value: String)

/** 因子中文名映射（§6.4 因子表行名；F-3.4 验收文案）。 */
fun factorLabel(id: String): String = try {
    when (FactorId.valueOf(id)) {
    FactorId.OXYGEN -> "溶氧"
    FactorId.PRESSURE -> "气压"
    FactorId.SOLUNAR -> "Solunar"
    FactorId.TIDE -> "潮流"
    FactorId.WIND -> "风"
    FactorId.WEATHER -> "天气"
    FactorId.DAYPART -> "时段"
    FactorId.TEMP_MATCH -> "适温"
    FactorId.COMFORT -> "舒适"
    FactorId.WATER_QUALITY -> "水质"
    }
} catch (_: IllegalArgumentException) {
    id
}

/**
 * 因子分解视图（F-3.4 / §8.2-3）：
 * ① 子分行：`溶氧 .95×0.21 …`（子分×权重 + 贡献条）
 * ② ΠV 明细：`V=0.95（阵风 40 km/h）`（无触发时不显示本区）
 * ③ 原始输入区：逐项 label + 原值（§8.2-3 清单由调用方组装）
 */
@Composable
fun FactorBreakdown(
    rows: List<FactorRow>,
    vetoRows: List<VetoRow>,
    rawInputs: List<RawInputRow>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(8.dp)) {
        Text("因子分解", style = MaterialTheme.typography.titleSmall)
        rows.forEach { row ->
            val subText = row.sub?.let { Format.value(it, 2) } ?: Format.MISSING
            val desc = "${row.name}：子分 $subText，权重 ${Format.value(row.weight, 2)}" +
                (row.detail?.let { "，$it" } ?: "")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .semantics { contentDescription = desc },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                Text(
                    "$subText ×${Format.value(row.weight, 2)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(96.dp),
                )
                // 贡献条：sub×weight / 最大可能贡献（宽度按占比，得分缺测 → 无条，禁画 0）
                val contribution = row.sub?.times(row.weight)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (contribution != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = contribution.coerceIn(0.0, 1.0).toFloat())
                                .height(8.dp)
                                .background(Color(ScoreBand.GREAT.argb)), // D14/19：色值收敛 ScoreBand token（单源，禁双源漂移）
                        )
                    }
                }
            }
            row.detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 64.dp))
            }
        }

        if (vetoRows.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("衰减 ΠV", style = MaterialTheme.typography.titleSmall)
            vetoRows.forEach { veto ->
                val multiplierText = veto.multiplier?.let { Format.value(it, 2) } ?: Format.MISSING
                Text(
                    "V=$multiplierText（${veto.reason}）",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .semantics { contentDescription = "乘子 $multiplierText，触发：${veto.reason}" },
                )
            }
        }

        if (rawInputs.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text("原始输入", style = MaterialTheme.typography.titleSmall)
            rawInputs.chunked(3).forEach { chunk ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    chunk.forEach { input ->
                        Text(
                            "${input.label} ${input.value}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}


