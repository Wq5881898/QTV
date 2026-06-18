# 已实现功能说明

本文档记录当前 QTV 原型已经落地并验证通过的功能，不讨论未来规划，只描述“现在已经能做什么”。

## 一、TV 基线

已实现：

- 应用可作为 Android TV 应用启动
- Manifest 已声明 `LEANBACK_LAUNCHER`
- 应用支持横屏展示
- 已接入沉浸式全屏，隐藏系统栏
- 已设置 TV banner 资源
- 非触屏设备可正常运行

相关文件：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/drawable/tv_banner.xml`

## 二、首页 TV 交互

已实现：

- 默认大视频区域
- 按 `Center / OK` 弹出左侧频道列表覆盖层
- 按 `Back` 关闭频道列表覆盖层
- D-pad 焦点移动
- OK 选择频道
- 已选中频道高亮
- 焦点态与选中态分离
- 触屏点击视频区域可打开频道列表
- 触屏点击频道项可切台
- 触屏点击右侧空白可关闭频道列表
- 系统返回键优先关闭频道列表，不直接退出应用

补充说明：

- 当前首页交互是 “TV 遥控器优先 + 触屏兼容补全”
- TV 模式和触屏模式共用同一套 `showChannelList` 状态控制

相关文件：

- `app/src/main/java/com/qtv/app/MainActivity.kt`

## 三、本地频道配置加载

已实现：

- 配置源已抽象成 repository + source provider 结构
- 默认从 `app/src/main/assets/qtv.json` 读取频道配置
- 已接入统一的 external URL 配置源
- 解析 `channels.items`
- 支持频道 `sources`
- 按 `priority` 选择主源
- 自动跳过没有可用 URL 的配置项

当前规则：

- 每个频道只使用当前优先级最高的一个源
- `status` 当前固定显示为 `Configured`
- `type` 当前用于推断播放器 MIME type
- `label` 当前保留在数据层，供后续诊断或扩展使用
- 如果配置了 external URL，应用会优先尝试 external 配置
- external URL 后续既可以是普通远程地址，也可以是 NAS 暴露出来的 URL
- 如果 external 配置失败，会自动回落到 bundled default，并保留 warning

相关文件：

- `app/src/main/assets/qtv.json`
- `app/src/main/java/com/qtv/app/config/LocalQtvConfig.kt`
- `app/src/main/java/com/qtv/app/config/QtvConfigRepository.kt`

## 四、流媒体播放

已实现：

- 使用 Media3 ExoPlayer 播放直播流
- 在 Compose 中嵌入原生 `PlayerView`
- 切换频道时自动切换播放源
- 显示基础播放状态
- 显示播放器报错信息
- 播放失败后自动重试当前主源
- 首次失败立即重试
- 后续失败按 10 秒间隔继续重试
- 同一频道最多重试 3 次
- 切台后自动清空上一频道的错误与重试状态

当前状态文案包括：

- `Loading stream...`
- `Buffering...`
- `Playing`
- `Playback ended`
- `Playback error: ...`
- `Retrying stream...`

相关文件：

- `app/src/main/java/com/qtv/app/player/QtvPlayerPane.kt`

## 五、HTTP 源支持

已实现：

- 应用允许 cleartext HTTP 流量
- Manifest 已开启 `usesCleartextTraffic`
- 已挂载 `network_security_config.xml`
- 当前网络策略允许所有 cleartext HTTP

这一步是当前原型成功播放测试源的必要条件之一。

相关文件：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/network_security_config.xml`

## 六、HLS 流识别修正

已实现：

- 播放器不再只依赖 URL 后缀猜测格式
- 创建 `MediaItem` 时显式设置 HLS MIME type

这一步解决了 URL 虽然最终返回 HLS，但入口路径是 `live.php?...`，ExoPlayer 默认无法稳定推断格式的问题。

相关文件：

- `app/src/main/java/com/qtv/app/player/QtvPlayerPane.kt`

## 七、已完成验证

已经完成的实际验证包括：

- Android TV 模拟器中应用可启动
- 本地 `qtv.json` 中的频道可显示
- D-pad 交互正常
- 触屏点击交互已接入并可用于手机端测试
- 至少多个测试流已成功进入 `Playing` 状态
- Windows 侧使用 PotPlayer 对源做过对照验证

## 八、当前未实现

当前还没有实现的内容包括：

- 远程配置拉取
- NAS 目录接入
- 用户自定义导入 `qtv.json`
- 多源自动切换
- 频道分组页
- 搜索
- 收藏
- EPG
- 真机兼容性矩阵验证

## 九、近期 Bug 修复

### 1. 频道列表覆盖层打开后，底部提示文案残留

问题：

- 默认播放器模式下会显示 `Press OK to open channels`
- 打开频道列表覆盖层后，这条提示不应该继续可见

修复：

- 频道列表覆盖层只在 `showChannelList == true` 时渲染
- 默认底部提示已直接移除，不再长期叠在视频上
- 覆盖层显式使用更高的绘制层级，避免状态切换时出现视觉残留

规避原则：

- TV 页面里所有模式提示文案都必须绑定到明确状态
- 覆盖层与底层提示不能只靠“视觉遮住”，必须从组合逻辑上互斥
- 新增播放态、列表态、错误态提示时，要先检查是否和已有提示发生重叠
