# 项目结构说明

本文档解释 QTV 当前原型的代码组织方式，以及从 `qtv.json` 到最终播放器画面的实际运行链路。

## 一、项目定位

当前项目是一个 Android TV 原型，目标是验证本地频道配置和直播播放链路，而不是完整产品。

当前主流程非常简单：

1. 从 `assets/qtv.json` 读取频道数据
2. 在首页左侧渲染频道列表
3. 用户通过 D-pad 选择频道
4. 右侧播放器区域切换到对应流地址
5. Media3 ExoPlayer 开始播放

## 二、目录概览

```text
QTV/
├─ app/
│  ├─ src/main/
│  │  ├─ AndroidManifest.xml
│  │  ├─ assets/qtv.json
│  │  ├─ java/com/qtv/app/
│  │  │  ├─ MainActivity.kt
│  │  │  ├─ config/LocalQtvConfig.kt
│  │  │  ├─ player/QtvPlayerPane.kt
│  │  │  └─ ui/theme/
│  │  └─ res/
│  │     ├─ drawable/tv_banner.xml
│  │     ├─ values/
│  │     └─ xml/network_security_config.xml
│  └─ build.gradle.kts
├─ docs/
├─ gradle/libs.versions.toml
├─ settings.gradle.kts
└─ README.md
```

## 三、核心文件职责

### 1. `app/src/main/AndroidManifest.xml`

作用：

- 声明 `INTERNET` 权限
- 声明 TV 相关能力
- 配置 `LEANBACK_LAUNCHER`
- 允许当前原型使用 cleartext HTTP
- 绑定 `network_security_config.xml`

这个文件决定了应用是否真正按 Android TV 方式启动，以及 HTTP 源能不能进入播放链路。

### 2. `app/src/main/assets/qtv.json`

作用：

- 当前原型的频道配置源
- 保存频道、分类、流地址、优先级

当前默认频道仍来自这个本地文件，但已经支持用 external URL 覆盖它。

### 3. `app/src/main/java/com/qtv/app/config/LocalQtvConfig.kt`

作用：

- 保存 `QtvChannel` / `QtvSource` 数据模型
- 解析原始 JSON
- 取出每个频道的主播放源
- 按 `priority` 排序并选择优先级最高的源
- 映射成界面可直接消费的 `QtvChannel`

### 4. `app/src/main/java/com/qtv/app/config/QtvConfigRepository.kt`

作用：

- 抽象配置来源
- 当前支持 bundled default 和 external URL
- external URL 后续既可以是普通远程地址，也可以是 NAS 提供的 URL
- 在 external 配置失败时回落到 bundled default
- 向 UI 返回当前实际生效的配置来源和 warning

### 5. `app/src/main/java/com/qtv/app/MainActivity.kt`

作用：

- 应用入口
- 组合式 TV 首页
- 异步加载频道配置
- 左侧频道列表
- 右侧频道详情和播放器区域
- D-pad 焦点和已选频道状态管理

这部分现在承担了 UI 主流程，没有再拆 ViewModel 或更完整的状态层。

### 6. `app/src/main/java/com/qtv/app/player/QtvPlayerPane.kt`

作用：

- 创建并持有 `ExoPlayer`
- 接收选中的 `streamUrl`
- 构造 `MediaItem`
- 明确把流按 HLS 处理
- 展示播放状态和错误信息
- 在 Compose 中嵌入 `PlayerView`

这份代码是这次流媒体调通的关键位置。

### 7. `app/src/main/res/xml/network_security_config.xml`

作用：

- 配置 cleartext HTTP 的网络策略

当前配置非常宽松，使用了全局允许：

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

这适合原型验证，但不适合直接视为最终生产策略。

## 四、运行链路

### 1. 启动

应用从 `MainActivity` 启动，Compose 渲染 TV 首页。

### 2. 配置读取

`TvHomeScreen()` 调用 `loadBundledChannels(context)`，把 `qtv.json` 映射成 `List<QtvChannel>`。

### 3. 列表渲染

左侧使用 `LazyColumn` 渲染频道列表，每一项都可获得焦点并触发选择。

### 4. 频道切换

当 `selectedIndex` 变化时，右侧取出新的 `selectedChannel`，其 `streamUrl` 传给 `QtvPlayerPane()`。

### 5. 播放器加载

`QtvPlayerPane()` 在 `LaunchedEffect(streamUrl)` 中：

1. 重置状态
2. 构造新的 `MediaItem`
3. 显式设置 HLS MIME type
4. `prepare()`
5. `play()`

### 6. 状态反馈

播放器监听 `Player.Listener`，把缓冲、播放中、结束、错误等状态写回 UI。

## 五、当前结构的优点和边界

### 优点

- 文件少，链路短，便于验证
- 问题定位集中，流媒体问题主要聚焦在播放器和网络配置
- 不依赖后端，方便快速做源验证

### 边界

- 业务状态没有分层
- 没有 ViewModel
- 没有仓储层或远程数据层
- 没有多源自动 failover
- 没有播放历史、收藏、EPG、搜索
- 没有生产级别网络安全收敛

## 六、建议的后续演进方向

当原型进入下一阶段，可以按下面方向拆分：

1. `config` 从本地 `assets` 扩展到 bundled default + external URL 切换
2. `player` 增加重试、超时、错误分级、源切换
3. `ui` 引入更清晰的状态管理层
4. 增加日志与诊断能力，减少纯靠肉眼判断播放是否成功
