package com.tidespot.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tidespot.core.data.remote.TideSpotUrls
import com.tidespot.core.data.water.CredentialCheck
import com.tidespot.core.ui.R as UiR
import kotlinx.coroutines.launch

/**
 * Copernicus 凭据版块（CHG-25 P0-1 批次 B；设置页「数据源凭据 · Copernicus」）。
 *
 * - 输入校验走 [CopernicusFormLogic] 纯函数（错误码枚举），网络校验走注入的 [onCheckAndSave]；
 * - 四态反馈（文案全部来自字符串资源，中/英双语）：Valid 已保存生效 / Invalid 凭据错误 /
 *   Unreachable 网络问题 / Missing 未配置；
 * - 凭据仅存 App 私有目录、不上传（文案明示）；注册免费账号含链接（URL 单点 [TideSpotUrls]）；
 * - 交互带 contentDescription（N-7；TalkBack 文案随 P2 i18n 全量外移统一）。
 */
@Composable
fun CopernicusCredentialSection(
    configured: Boolean,
    onCheckAndSave: suspend (username: String, password: String) -> CredentialCheck,
    onClear: suspend () -> Unit,
    onConfiguredChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<SectionMessage?>(null) }
    val scope = rememberCoroutineScope()
    val usernameInputDesc = stringResource(R.string.ts_settings_copernicus_username_input)
    val passwordInputDesc = stringResource(R.string.ts_settings_copernicus_password_input)
    val checkSaveDesc = stringResource(R.string.ts_settings_copernicus_check_save_desc)
    val clearDesc = stringResource(R.string.ts_settings_copernicus_clear_desc)
    val passwordToggleDesc = stringResource(
        if (showPassword) R.string.ts_settings_copernicus_password_hide
        else R.string.ts_settings_copernicus_password_show,
    )

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(UiR.string.ts_copernicus_section_title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(
                if (configured) UiR.string.ts_copernicus_configured else UiR.string.ts_copernicus_not_configured,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text(stringResource(UiR.string.ts_copernicus_username_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = usernameInputDesc
            },
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(UiR.string.ts_copernicus_password_label)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().semantics {
                contentDescription = passwordInputDesc
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !checking,
                onClick = {
                    val error = CopernicusFormLogic.validate(username, password)
                    if (error != null) {
                        message = SectionMessage.FormError(error)
                        return@Button
                    }
                    scope.launch {
                        checking = true
                        val (u, p) = CopernicusFormLogic.normalize(username, password)
                        message = when (val r = onCheckAndSave(u, p)) {
                            CredentialCheck.Valid -> {
                                onConfiguredChanged(true)
                                SectionMessage.Saved
                            }
                            CredentialCheck.Missing -> SectionMessage.Empty
                            is CredentialCheck.Invalid -> SectionMessage.Invalid(r.reason)
                            is CredentialCheck.Unreachable -> SectionMessage.Unreachable(r.reason)
                        }
                        checking = false
                    }
                },
                modifier = Modifier.semantics { contentDescription = checkSaveDesc },
            ) {
                Text(
                    stringResource(
                        if (checking) UiR.string.ts_copernicus_checking else UiR.string.ts_copernicus_check_save,
                    ),
                )
            }
            OutlinedButton(
                enabled = !checking,
                onClick = {
                    scope.launch {
                        onClear()
                        onConfiguredChanged(false)
                        message = SectionMessage.Cleared
                    }
                },
                modifier = Modifier.semantics { contentDescription = clearDesc },
            ) { Text(stringResource(UiR.string.ts_copernicus_clear)) }
            OutlinedButton(
                onClick = { showPassword = !showPassword },
                modifier = Modifier.semantics { contentDescription = passwordToggleDesc },
            ) {
                Text(stringResource(if (showPassword) UiR.string.ts_copernicus_hide else UiR.string.ts_copernicus_show))
            }
        }
        message?.let { m ->
            val text = when (m) {
                SectionMessage.Saved -> stringResource(UiR.string.ts_copernicus_result_valid)
                SectionMessage.Empty -> stringResource(UiR.string.ts_copernicus_result_missing)
                is SectionMessage.Invalid -> stringResource(UiR.string.ts_copernicus_result_invalid, m.reason)
                is SectionMessage.Unreachable -> stringResource(UiR.string.ts_copernicus_result_unreachable, m.reason)
                SectionMessage.Cleared -> stringResource(UiR.string.ts_copernicus_cleared)
                is SectionMessage.FormError -> stringResource(m.error.messageRes())
            }
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
        Text(stringResource(UiR.string.ts_copernicus_privacy), style = MaterialTheme.typography.labelSmall)
        Text(
            stringResource(UiR.string.ts_copernicus_register, TideSpotUrls.COPERNICUS_REGISTER_URL),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** 版块内消息状态（文案渲染时才映射资源——i18n 规范）。 */
private sealed interface SectionMessage {
    data object Saved : SectionMessage
    data object Empty : SectionMessage
    data class Invalid(val reason: String) : SectionMessage
    data class Unreachable(val reason: String) : SectionMessage
    data object Cleared : SectionMessage
    data class FormError(val error: CopernicusFormLogic.FormError) : SectionMessage
}

private fun CopernicusFormLogic.FormError.messageRes(): Int = when (this) {
    CopernicusFormLogic.FormError.EMPTY_USERNAME -> UiR.string.ts_copernicus_err_username
    CopernicusFormLogic.FormError.USERNAME_TOO_LONG -> UiR.string.ts_copernicus_err_username_long
    CopernicusFormLogic.FormError.EMPTY_PASSWORD -> UiR.string.ts_copernicus_err_password
    CopernicusFormLogic.FormError.PASSWORD_TOO_LONG -> UiR.string.ts_copernicus_err_password_long
}