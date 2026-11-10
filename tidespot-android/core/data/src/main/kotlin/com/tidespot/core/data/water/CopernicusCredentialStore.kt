package com.tidespot.core.data.water

import java.io.File
import java.io.FileOutputStream
import java.util.Properties

/**
 * Copernicus Marine 运行期凭据（CHG-25 P0-1 ①）。
 *
 * **安全口径**：凭据只存在于两个位置——用户输入的内存对象与 App 私有目录文件
 * （`filesDir/copernicus.properties`，见 [FileCopernicusCredentialStore]）；
 * **禁止**进入 BuildConfig / 源码 / res 资源 / 日志（退出标准 = APK 解包字符串扫描零命中）。
 */
data class CopernicusCredential(
    val username: String,
    val password: String,
) {
    /** 空凭据（任一项空白）视同未配置——与 [CopernicusCredentialStore.load] 的 null 语义对齐。 */
    val isBlank: Boolean get() = username.isBlank() || password.isBlank()
}

/**
 * 运行期凭据存储抽象（CHG-25 批次 A）。
 *
 * 契约：
 * - [load] 不存在 / 文件损坏 / 任一字段空白 → 一律 `null`（**不得抛异常**，未配置是正常态）；
 * - [save] 只接受非空凭据；写入须原子（临时文件 + 替换），避免半写状态；
 * - [clear] 幂等（文件不存在也算成功）。
 */
interface CopernicusCredentialStore {

    suspend fun load(): CopernicusCredential?

    suspend fun save(credential: CopernicusCredential)

    suspend fun clear()
}

/**
 * [CopernicusCredentialStore] 的 java.io 实现：Properties 文件（username/password 两键）。
 *
 * - 纯 JVM 可测（构造注入 [File]，测试用临时目录）；
 * - `:app` 接线：`FileCopernicusCredentialStore(File(context.filesDir, "copernicus.properties"))`；
 * - 写入后尽力收紧文件权限（Android filesDir 本身即应用私有，这里属纵深防御；平台不支持时静默跳过）。
 */
class FileCopernicusCredentialStore(private val file: File) : CopernicusCredentialStore {

    override suspend fun load(): CopernicusCredential? = runCatching {
        if (!file.exists()) return@runCatching null
        val props = Properties()
        file.inputStream().use { props.load(it) }
        val username = props.getProperty(KEY_USERNAME)?.trim().orEmpty()
        val password = props.getProperty(KEY_PASSWORD).orEmpty()
        if (username.isBlank() || password.isBlank()) null else CopernicusCredential(username, password)
    }.getOrNull()

    override suspend fun save(credential: CopernicusCredential) {
        require(!credential.isBlank) { "凭据不得为空（用户名与密码均必填）" }
        file.parentFile?.mkdirs()
        val props = Properties().apply {
            setProperty(KEY_USERNAME, credential.username.trim())
            setProperty(KEY_PASSWORD, credential.password)
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use { props.store(it, null) }
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "凭据文件写入失败: ${file.absolutePath}" }
        }
        runCatching {
            file.setReadable(false, false)
            file.setReadable(true, true)
            file.setWritable(false, false)
            file.setWritable(true, true)
        }
    }

    override suspend fun clear() {
        runCatching { file.delete() }
        runCatching { File(file.parentFile, "${file.name}.tmp").delete() }
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
    }
}