# HMCL Desktop

这是由 **XDRi.Club** 独立维护、基于 [HMCL 开源项目](https://github.com/HMCL-dev/HMCL)的桌面启动器发行项目。本仓库只代表本发行项目，不代表 HMCL 上游维护团队。

**这不是 Minecraft 官方产品，未获 Mojang 或 Microsoft 批准，也不与其存在关联。**

[项目网站](https://jnlk-cn.github.io/HMCL-Website/) · [English](README.md) · [账号与启动验证](docs/ACCOUNT-ACCESS.md) · [隐私说明](PRIVACY.md)

每次启动游戏都需要在线验证微软账户、Minecraft：Java 版游戏资格及匹配的正版角色。登录历史、缓存角色和旧配置不能替代验证。没有资格、认证过期、接口拒绝、响应异常或网络故障时停止启动，不回退到离线模式。

此发行项目仅支持微软账户登录；游戏访问始终需要最新的正版资格验证。

微软登录链路只请求 Microsoft、Xbox 和 Minecraft 的官方认证端点。实际可用性还取决于应用是否获准使用 Minecraft Services；出现登录页或设备代码不代表取得游戏 API 访问资格。

Minecraft 版本清单、游戏文件、资源索引和资源只从 Mojang 官方 HTTPS 来源下载，不回退到社区游戏镜像。第三方加载器与附加内容仍可使用 BMCLAPI、MCIM 及其原始来源。发行包只包含启动器、启动器依赖、对应源码及校验值，不包含游戏本体、Minecraft 游戏资源或个人账号配置。

遥测、公网 IP 地区查询及启动器自动更新已禁用。用户主动安装和更新游戏的功能保留。可重复使用的游戏凭据启动脚本不予导出；每次启动都要经过 HMCL 的认证流程。

程序保留 HMCL 的界面布局和 **3.17.0.362** 版本号，软件名称为 **HMCL Desktop**，以遵守上游要求区分修改版的附加条款。构建不冒充上游官方签名或官方发行。[源码](https://github.com/jnlk-cn/HMCL-Desktop)和 [Release 下载](https://github.com/jnlk-cn/HMCL-Desktop/releases/latest)均公开提供。

## 构建

需要 JDK 21+ 和 Python 3.10+：

```powershell
.\Build.ps1 -JavaHome 'C:\Path\To\jdk-21'
```

脚本执行编译、三个模块的测试、适用的代码规范检查和打包。输出位于 `HMCL/build/libs`、`dist`。Windows 启动器便携包包含经上游锁定哈希校验的 JavaFX，不包含 Java 运行时或游戏文件。账号、缓存和本地验收目录不纳入 Git 或发行包。

## 开源协议

本发行项目及其独立维护的修改采用 **GNU GPL 第 3 版，或由你选择的后续版本**，并保留[上游依据 GPLv3 第 7 条规定的附加条款](ADDITIONAL-TERMS.md)。第三方组件遵循各自的许可证。[LICENSE](LICENSE) 保留完整 GPL 原文。

再分发时应保留版权和法律声明，按上游条款区分修改版本，并在分发二进制时依照适用 GPL 条款提供对应源码。每份 Release 均提供匹配的源码、许可证声明和 SHA-256 校验值。

来源见 [UPSTREAM.md](UPSTREAM.md) 与 [config/upstream.json](config/upstream.json)，本发行项目的修改见 [CHANGES.md](CHANGES.md)。
