# TrafficWatch

个人自用 Android 流量监测 App，面向 Android 14 设备。它记录本机 App 的蜂窝流量，支持双卡手动标签和当前上网卡归属；热点流量作为“热点共享”独立展示，不归入任何具体 App。

## 当前功能

- 监测开关：开启后以前台服务每分钟采样一次，关闭后停止采样。
- 双卡标签：手机号、运营商、卡槽编号由用户手动录入。
- 当前上网卡归属：授权 `READ_PHONE_STATE` 后自动读取默认数据卡；未授权时按设置中的 fallback 卡槽归属。
- App 流量：通过 Android `NetworkStatsManager` 读取 UID/App 维度蜂窝流量。
- 热点总流量：系统提供 tethering UID 时显示为“热点共享”。
- 图表：总览饼图、树状视图、最近一小时折线、最近一周折线、最近十二个月月度条形。
- 本地存储：SQLite 保存在设备本地。
- 长期更新：项目内固定签名后，可用新版 APK 覆盖安装并保留原数据。

## 需要的权限

- 使用情况访问：必须手动在系统设置里开启，否则无法读取其他 App 流量统计。
- 通知：用于前台服务常驻通知。
- 读取手机状态：用于识别当前默认上网卡；不用于读取手机号，手机号由用户手动输入。
- 查询已安装应用：用于把系统 UID 映射成可读的应用名称。

## 设计边界

- 不监测 VPN 内部明细。
- 不拆分热点下每台设备的流量，只显示热点总量。
- 记录从 App 开启监测后开始；安装前的历史数据不作为可靠来源。
- 双卡归属按采样时的默认数据卡记录，极少数双卡同时走数据的场景不做运营商级精确拆分。

## 更新方式

不要卸载旧版。直接安装新版 APK 覆盖旧版即可保留本地 SQLite 数据：

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

`.signing/trafficwatch-stable.jks` 是长期更新的签名文件，丢失后新包将无法覆盖旧包。建议单独备份这个文件。

## 本地构建工具

Android 构建工具放在用户级目录，不放进项目仓库：

- JDK 17: `/home/loo/.local/share/android-toolchain/jdk`
- Android SDK: `/home/loo/.local/share/android-toolchain/android-sdk`
- Gradle: `/home/loo/.local/share/android-toolchain/gradle-8.10.2`
- 签名文件: `/home/loo/.local/share/android-signing/trafficwatch-stable.jks`

`~/.local/bin` 中已经提供 `gradle`、`adb`、`sdkmanager`、`avdmanager` 命令。新开一个 WSL 终端后可以直接调用。

## 版本号规则

版本号使用 `主版本.次版本.修订版本`：

- 主版本：出现不兼容变化、需要重新配置或可能影响旧数据时增加，例如 `2.0.0`。
- 次版本：新增主要功能但保持兼容时增加，例如新增桌面小组件、App 独立趋势图，从 `1.0.x` 到 `1.1.0`。
- 修订版本：修复问题、优化体验、调整动画或文案时增加，例如从 `1.1.0` 到 `1.1.1`。

`versionCode` 每次发布都加 1，这是 Android 判断新旧包的内部编号；`versionName` 是你看到的可读版本号。
