package com.tidespot.core.data.dto

import kotlinx.serialization.Serializable

/**
 * 逆地理 DTO（SRS §5.8 四级降级链；RG-A = BigDataCloud / RG-B = Photon；RG-C 不接入 CHG-06）。
 * 规则同 [OpenMeteoDtos]：源缺值一律可空、`ignoreUnknownKeys`、禁止 0 填充（CONTRACT 冻结点 4）。
 */

/** RG-A：`GET api.bigdatacloud.net/data/reverse-geocode-client?localityLanguage=zh`（免 Key，§5.8）。 */
@Serializable
data class BigDataCloudReverseDto(
    val countryName: String? = null,
    val countryCode: String? = null,
    /** 省/州级（如 `浙江省`；实测繁简混排：`山東省`，显示前须 T2S 归一，§5.8 坑 1）。 */
    val principalSubdivision: String? = null,
    /** 市（如 `杭州市`）。 */
    val city: String? = null,
    /** 区县或海区名（实测：`淳安县` / 开阔海面 `菲律宾海` / `渤海`，§5.8 坑 2）。 */
    val locality: String? = null,
    val plusCode: String? = null,
)

/**
 * RG-B：`GET photon.komoot.io/reverse?lat=&lon=&limit=1&layer=locality`（免 Key；OSM 派生 N-6）。
 * 陷阱（§5.8 坑 3 / B7b）：湖心/远海实测 200 + `features:[]` —— 「无聚落」是正常态，
 * 判空降级、不得当错误重试；`layer` 只接受单值 `locality`；禁止传 `lang` 参数。
 */
@Serializable
data class PhotonReverseDto(
    val features: List<PhotonFeatureDto>? = null,
)

@Serializable
data class PhotonFeatureDto(
    val properties: PhotonPropertiesDto? = null,
)

@Serializable
data class PhotonPropertiesDto(
    /** 聚落/街区名（实测岸边：`骑龙巷街区`）。 */
    val name: String? = null,
    /** 类型（实测 `locality`）。 */
    val type: String? = null,
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
)
