package com.tidespot.feature.settings

import com.tidespot.feature.settings.CopernicusFormLogic.FormError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CHG-25 P0-1 批次 B 锁测试：[CopernicusFormLogic] 表单纯函数（错误码枚举口径）。
 * 口径：用户名 trim 后判空、密码不 trim（首尾空格可能是密码一部分）、长度上限防御。
 */
class CopernicusFormLogicTest {

    @Test
    fun `合法输入_返回null`() {
        assertNull(CopernicusFormLogic.validate("user@example.com", "p@ss w0rd"))
        assertNull(CopernicusFormLogic.validate("  user  ", "pass"), "用户名首尾空格应被容忍")
        assertNull(CopernicusFormLogic.validate("u", " password with spaces "))
    }

    @Test
    fun `空用户名_错误码`() {
        assertEquals(FormError.EMPTY_USERNAME, CopernicusFormLogic.validate("", "pass"))
        assertEquals(FormError.EMPTY_USERNAME, CopernicusFormLogic.validate("   ", "pass"))
    }

    @Test
    fun `空密码_错误码`() {
        assertEquals(FormError.EMPTY_PASSWORD, CopernicusFormLogic.validate("user", ""))
        assertEquals(FormError.EMPTY_PASSWORD, CopernicusFormLogic.validate("user", "   "))
    }

    @Test
    fun `超长_错误码`() {
        val long = "a".repeat(CopernicusFormLogic.MAX_LEN + 1)
        assertEquals(FormError.USERNAME_TOO_LONG, CopernicusFormLogic.validate(long, "pass"))
        assertEquals(FormError.PASSWORD_TOO_LONG, CopernicusFormLogic.validate("user", long))
    }

    @Test
    fun `规范化_用户名trim密码保真`() {
        val (u, p) = CopernicusFormLogic.normalize("  user@example.com ", " pass ")
        assertEquals("user@example.com", u)
        assertEquals(" pass ", p, "密码不得 trim")
    }
}