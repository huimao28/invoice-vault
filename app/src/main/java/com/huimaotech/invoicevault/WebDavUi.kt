package com.huimaotech.invoicevault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavSettingsScreen(vm: VaultVm, onBack: () -> Unit) {
    val initial = vm.webDavConfig
    var serverUrl by rememberSaveable { mutableStateOf(initial.serverUrl) }
    var username by rememberSaveable { mutableStateOf(initial.username) }
    var password by rememberSaveable { mutableStateOf(initial.password) }
    var remoteDir by rememberSaveable { mutableStateOf(initial.remoteDir) }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var serverExpanded by rememberSaveable { mutableStateOf(initial.serverUrl.isBlank()) }
    var restore by remember { mutableStateOf<RemoteBackup?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val busy = vm.webDavBusy.isNotBlank()
    val configured = vm.webDavConfig.serverUrl.isNotBlank()
    val statusText = when {
        busy -> vm.webDavBusy + if (vm.webDavProgress.isBlank()) "" else " · ${vm.webDavProgress}"
        !configured -> "未配置"
        vm.webDavLastStatus.isNotBlank() -> vm.webDavLastStatus
        else -> "已配置，等待测试"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("WebDAV 备份") }, navigationIcon = { BackButton(onBack) }) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 760.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
                            if (busy) CircularProgressIndicator(Modifier.padding(14.dp).size(30.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 3.dp)
                            else Icon(if (configured) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, null, Modifier.padding(14.dp).size(30.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(if (configured) SettingsSummary.webDav(vm.webDavConfig.serverUrl) else "WebDAV 未配置", style = MaterialTheme.typography.titleLarge)
                            Text(statusText, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable { serverExpanded = !serverExpanded }.padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Dns, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp)); Text("服务器设置", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            Icon(if (serverExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (serverExpanded) "收起" else "展开")
                        }
                        if (serverExpanded) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(serverUrl, { serverUrl = it }, label = { Text("服务器 URL（仅 HTTPS）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(
                                    password, { password = it }, label = { Text("密码 / 应用专用密码") }, singleLine = true,
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = { IconButton({ showPassword = !showPassword }) { Icon(if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (showPassword) "隐藏密码" else "显示密码") } },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(remoteDir, { remoteDir = it }, label = { Text("远端目录") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Text("Basic 凭据只发送给证书有效的 HTTPS 服务器；应用不会关闭 TLS 校验。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button({ vm.saveWebDav(WebDavConfig(serverUrl, username, password, remoteDir)) }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("保存设置") }
                                    OutlinedButton({ vm.testWebDav() }, enabled = !busy && configured, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("测试连接") }
                                }
                                if (!configured) Text("保存后即可测试连接和备份。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("备份操作", style = MaterialTheme.typography.titleLarge)
                        Text("完整备份包含数据库与发票原件，仅在你点击时上传。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button({ vm.uploadWebDav() }, enabled = !busy && configured, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Rounded.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(if (vm.webDavBusy == "上传中") "正在备份 ${vm.webDavProgress}" else "立即备份") }
                        FilledTonalButton({ vm.refreshWebDav() }, enabled = !busy && configured, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("刷新远端列表") }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("远端备份", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column {
                            if (vm.remoteBackups.isEmpty()) Text(if (configured) "暂无列表。点击“刷新远端列表”查看备份。" else "配置服务器后可查看备份。", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            vm.remoteBackups.forEachIndexed { index, backup ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, end = 10.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(backup.name, style = MaterialTheme.typography.titleSmall); Text("${formatBytes(backup.size)}${if (backup.modified.isBlank()) "" else " · ${backup.modified}"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    TextButton({ restore = backup }, enabled = !busy) { Text("恢复") }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("危险操作", Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    OutlinedButton({ confirmClear = true }, enabled = !busy && (configured || username.isNotBlank() || password.isNotBlank()), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Rounded.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("清除 WebDAV 配置与本机密钥")
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }

    restore?.let { item ->
        AlertDialog(
            onDismissRequest = { restore = null }, icon = { Icon(Icons.Rounded.Warning, "警告") }, title = { Text("确认完整恢复？") },
            text = { Text("将下载并严格校验 ${item.name}，用其中的数据库和原件替换本地数据。操作前会创建本地安全快照，失败自动回滚；成功后必须重启应用。") },
            confirmButton = { Button({ restore = null; vm.restoreWebDav(item) }) { Text("确认恢复") } }, dismissButton = { TextButton({ restore = null }) { Text("取消") } }
        )
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false }, title = { Text("清除 WebDAV 配置？") },
        text = { Text("服务器地址、用户名、密码、远端目录和本机加密密钥都会被移除。远端已有备份不会被删除。") },
        confirmButton = { Button({ confirmClear = false; vm.clearWebDav(); serverUrl = ""; username = ""; password = ""; remoteDir = "invoice-vault" }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("清除") } },
        dismissButton = { TextButton({ confirmClear = false }) { Text("取消") } }
    )
}

internal fun formatBytes(n: Long) = when { n >= 1024 * 1024 -> "%.1f MB".format(n / 1048576.0); n >= 1024 -> "%.1f KB".format(n / 1024.0); else -> "$n B" }
