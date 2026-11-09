package com.tidespot.core.ui.badge

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tidespot.core.domain.model.Confidence
import com.tidespot.core.ui.strings.TideSpotStrings

/**
 * 数值置信徽标（§8.2-4）：六档六色且带文字，不靠颜色单独编码（N-7）。
 * domain `Confidence` 仅 5 值；「未校正/低置信」为 UI 语义档：
 * - [Kind.UNCALIBRATED]：潮位校正层未生效（§9.6 免责：「未校正点位显示"未校正"」）或
 *   domain UNAVAILABLE（置信未知，最接近的 UI 语义为未校正灰档，不虚构更高级置信）；
 * - [Kind.LOW_CONFIDENCE]：§6.7 因子剔除后有效权重 <0.6 时由评分侧标注。
 */
enum class Kind(val label: String, val argb: Long, val onArgb: Long) {
    MEASURED(TideSpotStrings.BADGE_MEASURED, 0xFF1565C0, 0xFFFFFFFF),
    SATELLITE(TideSpotStrings.BADGE_SATELLITE, 0xFF00695C, 0xFFFFFFFF),
    MODEL(TideSpotStrings.BADGE_MODEL, 0xFF6A1B9A, 0xFFFFFFFF),
    ESTIMATED(TideSpotStrings.BADGE_ESTIMATED, 0xFFEF6C00, 0xFF000000),
    UNCALIBRATED(TideSpotStrings.BADGE_UNCALIBRATED, 0xFF757575, 0xFFFFFFFF),
    LOW_CONFIDENCE(TideSpotStrings.BADGE_LOW_CONFIDENCE, 0xFFC62828, 0xFFFFFFFF),
    ;

    companion object {
        /** domain Confidence → UI 徽标档位（UNAVAILABLE → 未校正灰档，不虚构置信）。 */
        fun fromConfidence(confidence: Confidence): Kind = when (confidence) {
            Confidence.MEASURED -> MEASURED
            Confidence.SATELLITE -> SATELLITE
            Confidence.MODEL -> MODEL
            Confidence.ESTIMATED -> ESTIMATED
            Confidence.UNAVAILABLE -> UNCALIBRATED
        }
    }
}

/** 徽标组件：色块 + 文字（文字为必要语义，TalkBack 读全「置信：实测」等）。 */
@Composable
fun ConfidenceBadge(kind: Kind, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.semantics { contentDescription = TideSpotStrings.confidenceDescription(kind.label) },
        color = Color(kind.argb),
        contentColor = Color(kind.onArgb),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = kind.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

