# 发票匣（InvoiceVault）

本地优先的 Android 发票归档、识别、检索与备份工具。应用 ID：`com.minis.invoicevault`。

> [!IMPORTANT]
> 发票通常包含姓名、公司、税号、账号等敏感信息。InvoiceVault 默认在设备本地处理和保存数据；只有用户主动配置 WebDAV、导出或分享时，数据才会离开应用私有目录。

## 功能

- 导入 PDF 与图片发票，提取号码、日期、购销方、税号和金额
- 中文 OCR、PDF 文本层与电子发票 XML 解析
- 本地 Room 数据库、重复检测、搜索、分类与原件预览
- CSV/完整归档导出，支持指定邮件应用分享
- WebDAV 加密凭据、完整备份、校验与恢复
- 支持浅色/深色界面及大屏布局

## 截图

正式截图将在首个公开 Release 后补充。

<!-- screenshots/overview.png -->
<!-- screenshots/detail.png -->
<!-- screenshots/settings.png -->

## 支持格式

- PDF（优先读取文本层，必要时 OCR）
- JPEG、PNG、WebP 等 Android 可解码图片
- 电子发票 XML

识别结果可能受票面布局、扫描质量和 OCR 模型影响；重要字段请人工核对。本项目不是财税、会计或报销合规服务。

## 构建

要求 JDK 17、Android SDK Platform 34、Build Tools 35.0.0。项目因本地 ARM 构建工具限制保持 `compileSdk 34`，但清单目标为 `targetSdk 37`。

```bash
printf 'sdk.dir=/path/to/android-sdk\n' > local.properties
./gradlew testDebugUnitTest assembleDebug
```

### Release 签名

签名材料不得提交。默认从 `~/.invoice-vault/signing.properties` 读取，也可用环境变量 `INVOICE_VAULT_SIGNING_PROPERTIES` 指向外部文件：

```properties
storeFile=/absolute/path/to/release.keystore
storePassword=CHANGE_ME
keyAlias=invoice-vault-release
keyPassword=CHANGE_ME
```

```bash
./gradlew lintRelease assembleRelease bundleRelease
```

若配置文件不存在，Gradle 不加载发布签名；正式分发前必须使用独立私钥签名并验证。

## WebDAV

在“设置 → WebDAV”中填写 HTTPS 服务地址、用户名、应用专用密码和远端目录。凭据使用 Android Keystore 保护；备份包包含数据库和用户原件，应将 WebDAV 账户视为敏感资产。仅在明确了解风险时使用 HTTP。

## 隐私与安全

- 默认不上传发票或识别文本
- 数据库及原件位于应用私有存储
- WebDAV、导出、分享均由用户主动触发
- 卸载应用可能删除本地数据，请先完成可信备份
- 开源仓库仅包含完全虚构的合成测试数据

## 限制

- 不保证所有地区、版式或加密 PDF 均可识别
- OCR 与字段解析可能出错，不能替代人工复核
- WebDAV 兼容性取决于服务端实现
- 备份密码、服务器权限与终端安全由用户自行管理

## 下载

稳定版本请从 GitHub **Releases** 页面下载 `InvoiceVault-v1.0.0.apk`，并核对同页 `SHA256SUMS.txt`。不要从不可信第三方来源安装。

## 参与贡献

请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。提交 Issue 时不得附带真实发票、税号、账号、邮箱、WebDAV 凭据或其他个人信息。

## 许可

Copyright 2026 Kobby Li

以 [Apache License 2.0](LICENSE) 开源。第三方组件遵循其各自许可。
