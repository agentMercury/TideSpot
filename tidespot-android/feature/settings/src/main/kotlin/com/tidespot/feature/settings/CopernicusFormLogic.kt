package com.tidespot.feature.settings

/**
 * Copernicus 凭据表单纯逻辑（CHG-25 P0-1 批次 B）。
 *
 * 只做本地校验（trim / 非空 / 长度上限），不发网络请求；返回**错误码枚举**（禁文案硬编码，
 * 文案由组装层经字符串资源映射——i18n 规范）。
 */
object CopernicusFormLogic {

    /** 用户名/密码长度上限（Copernicus 账号无超长需求；防御 UI 粘贴异常）。 */
    const val MAX_LEN = 128

    /** 表单校验错误码（null 语义 = 合法，用 [validate] 返回值区分）。 */
    enum class FormError {
        EMPTY_USERNAME,
        USERNAME_TOO_LONG,
        EMPTY_PASSWORD,
        PASSWORD_TOO_LONG,
    }

    /**
     * 返回 null = 合法；否则返回错误码。
     * 口径：首尾空格剥除后判空（用户名）；密码**不 trim**（首尾空格可能是密码一部分），只判空白。
     */
    fun validate(username: String, password: String): FormError? = when {
        username.isBlank() -> FormError.EMPTY_USERNAME
        username.trim().length > MAX_LEN -> FormError.USERNAME_TOO_LONG
        password.isBlank() -> FormError.EMPTY_PASSWORD
        password.length > MAX_LEN -> FormError.PASSWORD_TOO_LONG
        else -> null
    }

    /** 提交前规范化（用户名 trim；密码原样）。 */
    fun normalize(username: String, password: String): Pair<String, String> =
        username.trim() to password
}