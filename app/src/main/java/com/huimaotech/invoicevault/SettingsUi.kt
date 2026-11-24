package com.huimaotech.invoicevault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsHome(email: String, vm: VaultVm, onOpen: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    SettingsWidthContainer {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("设置", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text("管理数据、备份与应用信息", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSection("数据与备份") {
                SettingsRow(Icons.Rounded.CloudSync, "WebDAV", SettingsSummary.webDav(vm.webDavConfig.serverUrl, vm.webDavLastStatus), SettingsSemanticColor.WEBDAV_BLUE) { onOpen(SettingsRoute.WEBDAV) }
                HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(Icons.Rounded.Email, "导出与邮件", SettingsSummary.email(email), SettingsSemanticColor.EXPORT_TEAL) { onOpen(SettingsRoute.EXPORT_EMAIL) }
            }
            SettingsSection("应用") {
                SettingsRow(Icons.Rounded.Security, "隐私与数据", "本地优先、权限与联网说明", SettingsSemanticColor.PRIVACY_PURPLE) { onOpen(SettingsRoute.PRIVACY) }
                HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(Icons.Rounded.Info, "关于发票匣", "版本 ${BuildConfig.VERSION_NAME}", SettingsSemanticColor.ABOUT_ORANGE) { onOpen(SettingsRoute.ABOUT) }
            }
            SettingsSection("开发者与支持") {
                SettingsRow(Icons.Rounded.Code, "GitHub", ExternalLinks.host(ExternalLinks.GITHUB), SettingsSemanticColor.GITHUB_BLUE, external = true) { safeOpenExternal(context, ExternalLinks.GITHUB) { vm.message = it } }
                HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(Icons.Rounded.Language, "官方网站", ExternalLinks.host(ExternalLinks.WEBSITE), SettingsSemanticColor.WEBSITE_TEAL, external = true) { safeOpenExternal(context, ExternalLinks.WEBSITE) { vm.message = it } }
                HorizontalDivider(Modifier.padding(start = 88.dp), color = MaterialTheme.colorScheme.outlineVariant)
                SponsorSettingsRow { safeOpenExternal(context, ExternalLinks.SPONSOR) { vm.message = it } }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingsWidthContainer(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 760.dp).padding(horizontal = 20.dp, vertical = 18.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(title, Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) { Column(content = content) }
    }
}

private data class SemanticIconColors(val container: Color, val content: Color)

@Composable
private fun semanticIconColors(semantic: SettingsSemanticColor): SemanticIconColors {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    return when (semantic) {
        SettingsSemanticColor.WEBDAV_BLUE, SettingsSemanticColor.GITHUB_BLUE -> if (dark) SemanticIconColors(Color(0xFF163B65), Color(0xFFA8C7FA)) else SemanticIconColors(Color(0xFFD7E6FF), Color(0xFF185ABC))
        SettingsSemanticColor.EXPORT_TEAL, SettingsSemanticColor.WEBSITE_TEAL -> if (dark) SemanticIconColors(Color(0xFF123F3B), Color(0xFF80D5CC)) else SemanticIconColors(Color(0xFFC9F2EB), Color(0xFF006B60))
        SettingsSemanticColor.PRIVACY_PURPLE -> if (dark) SemanticIconColors(Color(0xFF432A64), Color(0xFFD7BAFF)) else SemanticIconColors(Color(0xFFEBDDFF), Color(0xFF7041A3))
        SettingsSemanticColor.ABOUT_ORANGE, SettingsSemanticColor.SPONSOR_ORANGE -> if (dark) SemanticIconColors(Color(0xFF593315), Color(0xFFFFC48A)) else SemanticIconColors(Color(0xFFFFE0C2), Color(0xFF9A4E00))
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, title: String, summary: String, semantic: SettingsSemanticColor, external: Boolean = false, onClick: () -> Unit) {
    val colors = semanticIconColors(semantic)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = colors.container) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(28.dp), tint = colors.content) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Icon(if (external) Icons.Rounded.OpenInNew else Icons.Rounded.ChevronRight, if (external) "在外部打开" else null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SponsorSettingsRow(onClick: () -> Unit) {
    val colors = semanticIconColors(SettingsSemanticColor.SPONSOR_ORANGE)
    Surface(
        Modifier.fillMaxWidth().padding(10.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = colors.container
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Rounded.Favorite, null, Modifier.size(30.dp), tint = colors.content)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Sponsor", style = MaterialTheme.typography.titleMedium, color = colors.content)
                Text("支持持续开发 · ${ExternalLinks.host(ExternalLinks.SPONSOR)}", style = MaterialTheme.typography.bodyMedium, color = colors.content.copy(alpha = .82f))
            }
            Icon(Icons.Rounded.OpenInNew, "在外部打开", tint = colors.content)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportEmailScreen(email: String, onEmail: (String) -> Unit, onExport: () -> Unit, onShare: () -> Unit, onBack: () -> Unit) {
    var draft by rememberSaveable(email) { mutableStateOf(email) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("导出与邮件") }, navigationIcon = { BackButton(onBack) }) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                SettingsSection("默认收件人") {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it; onEmail(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("默认收件邮箱") },
                            supportingText = { Text("可留空；打开邮件应用后仍可修改收件人") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                    }
                }
                SettingsSection("导出") {
                    ActionRow(Icons.Rounded.FolderZip, "导出 ZIP", "保存 CSV 与全部原文件", onExport)
                    HorizontalDivider(Modifier.padding(start = 80.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    ActionRow(Icons.Rounded.AttachEmail, "生成附件并打开邮件", "创建 ZIP 并交给系统邮件或分享应用", onShare)
                }
                SettingsSection("说明") {
                    Text(
                        "导出文件由你选择保存位置。发票匣只会创建附件并打开系统选择界面，不会在后台发送邮件。若目标应用不可用或文件创建失败，会显示错误而不会退出。",
                        Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Rounded.ChevronRight, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDataScreen(onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("隐私与数据") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PrivacyBlock(Icons.Rounded.PhoneAndroid, "本地存储", "发票原件、识别结果和数据库默认保存在本机应用专属空间。卸载应用会删除这些数据，请按需导出或备份。")
                PrivacyBlock(Icons.Rounded.CloudSync, "WebDAV 主动联网", "应用仅在你配置 WebDAV 并主动测试、备份、刷新或恢复时访问对应服务器；不提供后台自动同步。")
                PrivacyBlock(Icons.Rounded.EnhancedEncryption, "凭据加密", "WebDAV 用户名和密码使用 Android Keystore 保护的加密密钥保存；清除 WebDAV 配置会同时移除本机密钥。")
                PrivacyBlock(Icons.Rounded.VisibilityOff, "无广告与分析", "应用未集成广告或用户行为分析 SDK。端侧文字识别不要求上传发票内容。")
                PrivacyBlock(Icons.Rounded.AdminPanelSettings, "权限用途", "网络权限仅用于用户主动操作的 WebDAV。导入与导出通过 Android 存储访问框架（SAF）选择文件，不申请读取全部文件权限。")
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun PrivacyBlock(icon: ImageVector, title: String, body: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer) { Icon(icon, null, Modifier.padding(12.dp).size(28.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onBack, Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Rounded.ArrowBack, "返回") }
}
