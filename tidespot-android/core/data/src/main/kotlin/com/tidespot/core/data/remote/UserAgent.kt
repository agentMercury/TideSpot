package com.tidespot.core.data.remote

/**
 * SRS §5.0 / §12 / CONTRACT.md 冻结点 5：统一可识别 UA。
 * **禁止复用桌面浏览器通用 UA**（RG-* 与 Overpass 要求自报应用身份）。
 */
object TideSpotUserAgent {
    const val VERSION = "1.0"
    const val VALUE = "TideSpot/$VERSION (Android; single-user device; manual tap only)"
}
