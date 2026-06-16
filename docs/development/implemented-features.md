# 已实现功能说明

本文档记录当前 QTV 原型已经落地并验证通过的功能，不讨论未来规划，只描述“现在已经能做什么”。

## 一、TV 基线

已实现：

- 应用可作为 Android TV 应用启动
- Manifest 已声明 `LEANBACK_LAUNCHER`
- 应用支持横屏展示
- 已设置 TV banner 资源
- 非触屏设备可正常运行

相关文件：

- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/drawable/tv_banner.xml`

## 二、首页 TV 交互

已实现：

- 左侧频道列表
- 右侧详情与播放器区域
- D-pad 焦点移动
- OK 选择频道
- 已选中频道高亮
- 焦点态与选中态分离

相关文件：

- `app/src/main/java/com/qtv/app/MainActivity.kt`

## 三、本地频道配置加载

已实现：

- 从 `app/src/main/assets/qtv.json` 读取频道配置
- 解析 `channels.items`
- 支持频道 `sources`
- 按 `priority` 选择主源
- 自动跳过没有可用 URL 的配置项

当前规则：

- 每个频道只使用当前优先级最高的一个源
- `status` 当前固定显示为 `Configured`

相关文件：

- `app/src/main/assets/qtv.json`
- `app/src/main/java/com/qtv/app/config/LocalQtvConfig.kt`

## 四、流媒体播放

已实现：

- 使用 Media3 ExoPlayer 播放直播流
- 在 Compose 中嵌入原生 `PlayerView`
- 切换频道时自动切换播放源
- 显示基础播放状态
- 显示播放器报错信息

当前状态文案包括：

- `Loading stream...`
- `Buffering...`
- `Playing`
- `Playback ended`
- `Playback error: ...`

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
- 至少多个测试流已成功进入 `Playing` 状态
- Windows 侧使用 PotPlayer 对源做过对照验证

## 八、当前未实现

当前还没有实现的内容包括：

- 远程配置拉取
- NAS 目录接入
- 用户自定义导入 `qtv.json`
- 多源自动切换
- 播放失败自动重试
- 频道分组页
- 搜索
- 收藏
- EPG
- 真机兼容性矩阵验证
