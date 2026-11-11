package com.tidespot.feature.favorites.favorites

/**
 * OPT-V12-07（CHG-57）：收藏标签预设（纯函数；编辑/批量对话框共用）。
 *
 * 口径（`OPT_REQUIREMENTS_V11.md` §7 OPT-V12-07）：
 * - 预设 chips 点选即**加入**当前标签集合，再次点选**取消**（与手输自由文本并存）；
 * - 写入前 trim、去空；同值不重复（**忽略大小写与首尾空格**）；
 * - **不自动翻译**存量英文标签（如 `night` 原样保留，不静默改写用户数据）；
 * - 批量「追加/替换」语义不变（合并由 `FavoritesBatchLogic.mergeTags` 负责）。
 */
object TagPresets {

    /** 高频标签预设（默认集合；后续可在设置页扩展，规格见 OPT-V12-07 需求 1）。 */
    val DEFAULT: List<String> = listOf("夜钓", "礁区", "船钓", "库湾", "防波堤", "闸口", "入海口")

    /** 比较口径：忽略大小写与首尾空格（GWT：`Night`/` night ` 与预设同名视为同一项）。 */
    private fun same(a: String, b: String): Boolean = a.trim().equals(b.trim(), ignoreCase = true)

    /** 当前标签集合是否已包含该预设（chip 选中态；不区分大小写/空格）。 */
    fun isSelected(tags: List<String>, preset: String): Boolean = tags.any { same(it, preset) }

    /**
     * 在原始输入串上追加/移除一个预设，返回新的原始串（`、` 分隔）。
     *
     * 保持「单真相源 = 输入框文本」：chip 与手输共享同一字符串，避免双状态不同步。
     * 已存在（忽略大小写/空格）→ 移除该等价项；否则追加（trim 后）。
     */
    fun toggleRaw(raw: String, preset: String): String {
        val current = FavoritesSortLogic.parseTags(raw)
        val exists = current.any { same(it, preset) }
        val next = if (exists) {
            current.filterNot { same(it, preset) }
        } else {
            current + preset.trim()
        }
        return next.joinToString("、")
    }

    /** 依据当前原始串派生 chip 选中态。 */
    fun selectedInRaw(raw: String, preset: String): Boolean =
        isSelected(FavoritesSortLogic.parseTags(raw), preset)
}