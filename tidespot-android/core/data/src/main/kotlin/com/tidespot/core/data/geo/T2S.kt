package com.tidespot.core.data.geo

/**
 * 简繁归一（SRS §5.8 坑 1：RG-A 对中国点层级简繁不一致 —— 实测 `山東省/青島市/嶗山區`、
 * 太湖点 `江蘇省苏州市` 省繁市简混排）。显示前必须过一次 T2S，同一界面不得混排简繁。
 *
 * 内置常用字映射表（覆盖中国省市区/河湖海区名常用字即可，SRS 口径）；
 * 单字映射天然幂等；不收录词组，保持单一入口 [toSimplified]。
 */
object T2S {

    /** 「繁 简」交替对（空格分隔，仅列出两侧不同的字）。 */
    private const val PAIRS =
        "東 东 蘇 苏 廣 广 島 岛 灣 湾 區 区 縣 县 鎮 镇 鄉 乡 嶗 崂 臺 台 龍 龙 馬 马 門 门 關 关 " +
            "橋 桥 莊 庄 濟 济 寧 宁 慶 庆 興 兴 華 华 義 义 陽 阳 陰 阴 溫 温 漢 汉 無 无 揚 扬 鹽 盐 " +
            "蕪 芜 銅 铜 黃 黄 贛 赣 營 营 澤 泽 臨 临 錦 锦 蘆 芦 滄 沧 張 张 鄲 郸 瀋 沈 爾 尔 濱 滨 " +
            "烏 乌 魯 鲁 齊 齐 銀 银 蘭 兰 瓊 琼 峽 峡 庫 库 壩 坝 溝 沟 嶼 屿 灘 滩 鏡 镜 連 连 倫 伦 " +
            "貝 贝 騰 腾 賽 赛 納 纳 凱 凯 撫 抚 遼 辽 陝 陕 內 内 肅 肃 雲 云 貴 贵 煙 烟 濰 潍 廈 厦 " +
            "頭 头 亞 亚 韓 韩 鮮 鲜 賓 宾 對 对 遠 远 聞 闻 饒 饶 詔 诏 羅 罗 膠 胶 萊 莱 榮 荣 倉 仓 " +
            "吳 吴 崑 昆 萬 万 邁 迈 欽 钦 靈 灵 錢 钱 閩 闽 諸 诸 餘 余 蒼 苍 順 顺 縉 缙 樂 乐 嶺 岭 " +
            "嶴 岙 媽 妈 聖 圣"

    private val TRAD_TO_SIMP: Map<Char, Char> = PAIRS
        .split(' ')
        .filter { it.isNotEmpty() }
        .chunked(2) { pair -> pair[0].first() to pair[1].first() }
        .toMap()

    /** 繁→简归一（幂等：简体/无映射字符原样返回；单一函数入口）。 */
    fun toSimplified(s: String): String =
        if (s.isEmpty()) s
        else buildString(s.length) { for (c in s) append(TRAD_TO_SIMP[c] ?: c) }
}
