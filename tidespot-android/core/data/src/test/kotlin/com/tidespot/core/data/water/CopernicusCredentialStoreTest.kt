package com.tidespot.core.data.water

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * CHG-25 P0-1 批次 A 锁测试：[FileCopernicusCredentialStore] 纯 JVM 语义。
 *
 * 覆盖契约（CONTRACT「v1.1 子契约 P0-1」）：
 * 1. save → load 往返（username trim、password 保真）；
 * 2. clear 幂等（文件不存在也成功）；
 * 3. 损坏文件 / 空值字段 → load 返回 null（不得抛——未配置是正常态）；
 * 4. 空白凭据 save 拒绝（require 语义）；
 * 5. 原子写：覆盖保存后旧值不残留。
 */
class CopernicusCredentialStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(fileName: String = "copernicus.properties"): FileCopernicusCredentialStore =
        FileCopernicusCredentialStore(File(tmp.root, fileName))

    @Test
    fun `保存与读取往返_用户名去空格密码保真`() = runTest {
        val s = store()
        s.save(CopernicusCredential(username = "  user@example.com  ", password = " p@ss w0rd! "))
        val loaded = s.load()
        assertEquals("user@example.com", loaded?.username)
        assertEquals(" p@ss w0rd! ", loaded?.password, "密码不得 trim（首尾空格可能是密码的一部分）")
    }

    @Test
    fun `清除幂等_未保存也可调用`() = runTest {
        val s = store()
        s.clear()
        s.clear()
        assertNull(s.load())
        s.save(CopernicusCredential("u", "p"))
        s.clear()
        assertNull(s.load())
    }

    @Test
    fun `损坏文件与空值字段_读取返回null不抛`() = runTest {
        val s = store("broken.properties")
        File(tmp.root, "broken.properties").writeText("这不是 properties 内容 ::: ��")
        assertNull(s.load(), "损坏文件 → null（未配置态）")

        val empty = store("empty.properties")
        File(tmp.root, "empty.properties").writeText("username=\npassword=\n")
        assertNull(empty.load(), "空值字段 → null")
    }

    @Test
    fun `空白凭据拒绝保存`() = runTest {
        val s = store()
        val error = runCatching { s.save(CopernicusCredential("  ", "p")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException, "空白凭据必须拒绝，实际 $error")
        assertNull(s.load(), "拒绝保存后不得留下文件")
    }

    @Test
    fun `覆盖保存_旧值不残留`() = runTest {
        val s = store()
        s.save(CopernicusCredential("old-user", "old-pass"))
        s.save(CopernicusCredential("new-user", "new-pass"))
        val loaded = s.load()!!
        assertEquals("new-user", loaded.username)
        assertEquals("new-pass", loaded.password)
        assertEquals(0, File(tmp.root, "copernicus.properties.tmp").let { if (it.exists()) 1 else 0 })
    }
}