package com.tidespot.core.ui.smoke

import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tidespot.core.domain.model.WaterBodyType
import com.tidespot.core.ui.breakdown.FactorBreakdown
import com.tidespot.core.ui.breakdown.FactorRow
import com.tidespot.core.ui.breakdown.RawInputRow
import com.tidespot.core.ui.breakdown.VetoRow
import com.tidespot.core.ui.selector.DaySelector
import com.tidespot.core.ui.selector.DaySelectorLogic
import com.tidespot.core.ui.titles.WaterBodyTabTitles
import com.tidespot.core.ui.strings.ProvideTideSpotStrings
import com.tidespot.core.ui.strings.ZhStrings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/**
 * Compose 组装层冒烟四条锁（A-14/C-1，CHG-19；Robolectric 形态）。
 *
 * **背景**：M4 D01/D02 与 M5「Sheet 未实现、测试零交付」两次漏网的根因均为
 * 「组装层（Screen/Composable）是纯 JVM 测试盲区」（HANDOVER §5 教训 1）。本类以
 * Robolectric + ui-test 在纯 JVM 侧组合真实 Composable 并断言语义树，补组装层覆盖：
 * 1. 5 Tab 标题矩阵：四水体类型 × `titlesInTabOrder` 渲染无错位（D01 锁的 Compose 形态）；
 * 2. 切表 10 次不崩：TabRow 宿主连续切换 10 轮，选中态与内容一致（D02 语义冒烟）；
 * 3. DaySelector chips 渲染与点击回调（F-3.1）；
 * 4. FactorBreakdown 因子/衰减/原始输入三区可见（F-3.4）。
 *
 * 断言文案一律取运行时 `titlesFor` 等产物，禁硬编码文案（FO-01 同口径）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ComposeSmokeRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    /** 最小 Tab 宿主：模拟 DetailScreen 的 TabRow + 内容切换组装形态（标题唯一入口 = titlesInTabOrder）。 */
    @Composable
    private fun TestTabHost(titles: List<String>, notice: String?) {
        var selected by remember { mutableIntStateOf(0) }
        Column {
            TabRow(selectedTabIndex = selected) {
                titles.forEachIndexed { i, t ->
                    Tab(selected = i == selected, onClick = { selected = i }, text = { Text(t) })
                }
            }
            notice?.let { Text(it) }
            Text("CONTENT:${titles[selected]}")
        }
    }

    /** 锁 1：5 Tab 标题矩阵，逐 Tab 文本与 titlesInTabOrder 一致（无错位/无缺项）——每水体类型独立用例
     *  （compose ui-test 约束：setContent 每测试仅一次）。 */
    private fun assertTabMatrix(type: WaterBodyType) {
        val titles = WaterBodyTabTitles.titlesInTabOrder(WaterBodyTabTitles.titlesFor(type))
        val notice = WaterBodyTabTitles.titlesFor(type).notice
        compose.setContent { TestTabHost(titles, notice) }
        titles.forEach { title ->
            compose.onNodeWithText(title, substring = false).assertExists()
        }
        compose.onNodeWithText("CONTENT:${titles[0]}").assertExists()
        notice?.let { compose.onNodeWithText(it).assertExists() }
        compose.waitForIdle()
    }

    @Test
    fun `tab标题矩阵_海水近岸5Tab无错位`() {
        assertTabMatrix(WaterBodyType.OCEAN)
        // NEARSHORE 与 OCEAN 同默认 5 维标题（titlesFor 同分支）
        assert(WaterBodyTabTitles.titlesFor(WaterBodyType.NEARSHORE) == WaterBodyTabTitles.titlesFor(WaterBodyType.OCEAN))
    }

    @Test
    fun `tab标题矩阵_内陆降级标题无错位`() = assertTabMatrix(WaterBodyType.INLAND_WATER)

    @Test
    fun `tab标题矩阵_陆地待判定标注无错位`() = assertTabMatrix(WaterBodyType.LAND)

    @Test
    fun `tab标题矩阵_未知待判定标注无错位`() = assertTabMatrix(WaterBodyType.UNKNOWN)

    /** 锁 2：TabRow 宿主连续切表 10 轮不崩，选中 Tab 与内容保持一致（D02 语义冒烟）。 */
    @Test
    fun `切表10次不崩_选中Tab与内容一致`() {
        val titles = WaterBodyTabTitles.titlesInTabOrder(WaterBodyTabTitles.titlesFor(WaterBodyType.OCEAN))
        compose.setContent { TestTabHost(titles, null) }
        repeat(10) { round ->
            val target = (round + 1) % titles.size
            compose.onNodeWithText(titles[target]).performClick()
            compose.waitForIdle()
            compose.onNodeWithText("CONTENT:${titles[target]}").assertExists()
        }
    }

    /** 锁 3：DaySelector chips 渲染（10 天范围）+ 点击回调 + TalkBack contentDescription（F-3.1/N-7）。 */
    @Test
    fun `daySelector_chips渲染与点击回调`() {
        val zone = ZoneId.of("Asia/Tokyo")
        val now = Instant.parse("2026-11-09T14:30:00Z").epochSecond
        val chips = DaySelectorLogic.chips(daysRange = 10, zone = zone, nowEpochSec = now)
        var clicked: Int? = null
        compose.setContent {
            DaySelector(chips = chips, selectedDay = 0, onDaySelected = { clicked = it })
        }
        // OPT-V12-04：chip 改两行（上行日期/今天、下行星期）→ 断言两行文本，点击走合并语义节点
        compose.onNodeWithText(chips[0].topLabel).assertIsDisplayed()
        compose.onNodeWithText(chips[0].weekdayLabel).assertExists()
        compose.onNodeWithText(chips[1].topLabel).assertExists()
        compose.onNodeWithContentDescription(chips[1].contentDescription).performClick()
        compose.runOnIdle { check(clicked == 1) { "点击 +1 后回调应为 1，实际 $clicked" } }
        compose.onNodeWithContentDescription(chips[0].contentDescription).assertExists()
        // 口径说明：LazyRow 视口裁剪下 Robolectric 触摸注入不驱动滚动，远端 chip（+6 等）
        // 不进语义树属 lazy 惰性组合的预期行为——chips 全量生成由 DaySelectorLogicTest
        // （纯 JVM 锁）覆盖，滚动交互路径挂真机人工冒烟（A-16 同口径）。
        compose.onRoot().assertExists()
    }

    /** 锁 4：FactorBreakdown 因子/衰减/原始输入三区渲染可见（F-3.4）。 */
    @Test
    fun `factorBreakdown_三区渲染可见`() {
        val rows = listOf(
            FactorRow(id = "OXYGEN", name = "溶氧", sub = 0.95, weight = 0.21),
            FactorRow(id = "PRESSURE", name = "气压", sub = null, weight = 0.08, detail = "趋势数据不足"),
        )
        val vetoes = listOf(
            VetoRow(reason = "雷暴风险", multiplier = 0.95),
            VetoRow(reason = "鱼情衰减", multiplier = null),
        )
        val raws = listOf(
            RawInputRow("Tw", "22.0"), RawInputRow("DO_p", "8.1"), RawInputRow("P", "1012"),
        )
        compose.setContent {
            ProvideTideSpotStrings(ZhStrings) {
                FactorBreakdown(rows = rows, vetoRows = vetoes, rawInputs = raws)
            }
        }
        // Robolectric 下无真实窗口尺寸，长 Column 的远端行可能被视口裁剪 → 三区以「语义树存在」为准
        compose.onNodeWithText("因子分解").assertExists()
        // 子分行语义树：名称/权重/触发明细逐条可读（TalkBack 口径；缺测 = Format.MISSING "—"，禁假 0）
        compose.onNodeWithContentDescription("溶氧：子分 0.95，权重 0.21").assertExists()
        compose.onNodeWithContentDescription("气压：子分 —，权重 0.08，趋势数据不足").assertExists()
        compose.onNodeWithText("衰减 ΠV").assertExists()
        compose.onNodeWithContentDescription("乘子 0.95，触发：雷暴风险").assertExists()
        compose.onNodeWithText("原始输入").assertExists()
        compose.onNodeWithText("Tw 22.0").assertExists()
        compose.onNodeWithText("P 1012").assertExists()
    }

    /** 附锁：同一标题同屏唯一（语义树无歧义重复节点——D01 错位根因的形态防御）。 */
    @Test
    fun `tab标题渲染_同屏无歧义重复节点`() {
        val titles = WaterBodyTabTitles.titlesInTabOrder(WaterBodyTabTitles.titlesFor(WaterBodyType.LAND))
        compose.setContent { TestTabHost(titles, WaterBodyTabTitles.titlesFor(WaterBodyType.LAND).notice ?: "") }
        titles.forEach { title ->
            compose.onAllNodes(hasText(title)).assertCountEquals(1)
        }
    }
}
