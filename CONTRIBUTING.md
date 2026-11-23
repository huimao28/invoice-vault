# 贡献指南

感谢参与 InvoiceVault。

1. Issue、测试夹具、截图和日志中不得包含真实发票、姓名、公司税号、银行账号、邮箱、WebDAV 地址或凭据。
2. 回归数据必须为完全虚构的合成内容，并清楚标注用途。
3. 修改业务逻辑时补充对应单元测试；提交前运行：

```bash
./gradlew testDebugUnitTest lintRelease assembleRelease
```

4. 不提交 `local.properties`、构建产物、签名文件或密码配置。
5. 提交应保持范围清晰，并说明行为变化与验证方法。

参与本项目即表示同意按 Apache License 2.0 提交贡献。
