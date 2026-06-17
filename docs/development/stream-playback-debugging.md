# 流媒体源调通与排查记录

本文档专门记录这次把 QTV 原型中的直播流真正调通时，遇到的问题、判断过程和最终解法。

这是当前最重要的一份开发文档，因为原型成功的核心不在 UI，而在“Android TV 里能不能把源播起来”。

## 一、目标

目标不是证明某个播放器控件能显示，而是证明下面这条链路在 Android TV 原型中成立：

1. `qtv.json` 提供频道与 URL
2. 用户选择频道
3. App 把 URL 交给 Media3 ExoPlayer
4. ExoPlayer 正确识别流格式
5. 播放器在 Android TV 模拟器内进入 `Playing`

## 二、已知输入条件

这次验证使用的是 HTTP 地址，入口形式类似：

```text
http://.../play/live.php?mac=...&stream=...&extension=m3u8
```

有两个关键特征：

1. 是 `http`，不是 `https`
2. URL 路径本身是 `live.php`，不是直接以 `.m3u8` 结尾

这两个特征分别触发了两类问题：

- Android 网络安全策略问题
- ExoPlayer 流格式识别问题

## 三、第一类问题：HTTP 被 Android 默认拦截

### 现象

播放器报错，核心含义是：

`Cleartext HTTP traffic not permitted`

这说明源本身不一定有问题，而是 Android 应用默认不允许明文 HTTP。

### 初步判断

同一个地址在 Windows 端 PotPlayer 能直接播放，这说明：

- 源并不一定坏
- 更大概率是 Android 客户端策略问题

也就是说，不能因为播放器失败就立刻认定源失效，要先把“应用侧网络限制”和“源本身不可播”拆开判断。

### 解决方式

在 Manifest 中打开 cleartext 支持：

```xml
<application
    android:networkSecurityConfig="@xml/network_security_config"
    android:usesCleartextTraffic="true" />
```

并在 `app/src/main/res/xml/network_security_config.xml` 中允许 cleartext：

```xml
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

### 为什么最终用了全局允许

中间曾经尝试过更严格的做法，只对白名单域名放开 cleartext。

但这类直播入口有一个实际问题：入口域名和最终请求落点未必一致。服务端可能会：

- 302 跳转
- 回落到其他域名
- 直接落到 IP 地址

一旦真实请求链路落到白名单之外，Android 仍然会拦截，表现上就像“明明已经配了域名允许，为什么还是不能播”。

对当前原型阶段来说，最务实的做法是先全局放开 cleartext，优先验证播放链路。等后面明确真实流量拓扑，再收紧策略。

## 四、第二类问题：ExoPlayer 无法稳定识别流格式

### 现象

在 cleartext 限制解决后，播放器仍然可能失败。典型现象是：

- 没有进入正常播放
- 报输入格式无法识别
- 不能自动判断该流按哪种容器或协议处理

本质原因是这个入口 URL 长这样：

```text
/play/live.php?...&extension=m3u8
```

对人来说，很容易知道这是 HLS；但对播放器来说，路径不是 `.m3u8`，自动推断就不可靠。

### 根因

ExoPlayer 的格式判断通常依赖以下信息之一：

- URL 后缀
- 响应头
- 内容探测

而这类 PHP 中转地址在入口阶段并不天然提供足够稳定的显式格式信号，所以可能出现：

- 桌面播放器能播
- Android ExoPlayer 却不能直接识别

### 最终解法

在创建 `MediaItem` 时显式指定 HLS MIME type：

```kotlin
MediaItem.Builder()
    .setUri(streamUrl)
    .setMimeType(MimeTypes.APPLICATION_M3U8)
    .build()
```

这一步非常关键。它不是“优化”，而是当前这类源能稳定工作的重要前提。

## 五、播放器代码里真正关键的点

当前在 `QtvPlayerPane.kt` 中，播放链路的关键点有三个：

### 1. 频道切换时重建媒体项

通过 `LaunchedEffect(streamUrl)` 监听 URL 变化，确保每次换台都会重新装载源。

### 2. 显式声明 HLS

使用：

```kotlin
MimeTypes.APPLICATION_M3U8
```

避免依赖 URL 推断。

### 3. 把错误回显到界面

播放器监听 `onPlayerError()`，把错误文本回写到 UI。

这一步的价值很大，因为如果没有错误回显，排查时只能看到“黑屏”或“一直转圈”，定位效率会非常低。

## 六、这次排查的实际判断路径

本次问题不是一次性定位出来的，而是按下面顺序逐步收敛：

1. 先确认项目本身已经是 TV 基线，不是启动或焦点问题
2. 加入本地 `qtv.json`，确认频道配置能进 UI
3. 接入 Media3，先把播放器画面和状态跑起来
4. 播放失败后，先看错误信息，定位到 cleartext HTTP 被拦截
5. 放开 cleartext 后，再继续看下一层错误
6. 发现 URL 为 `live.php?...extension=m3u8`，怀疑是格式识别不稳定
7. 显式指定 HLS MIME type
8. 在模拟器内重新验证多个测试流，确认进入 `Playing`

这个顺序很重要，因为它说明这不是单点问题，而是两个问题叠加：

- 网络策略先拦截
- 流格式识别随后失败

只修一个，最后仍然播不起来。

## 七、为什么 PotPlayer 能播，但 Android 一开始不能播

这是本次最容易误判的点。

Windows 端 PotPlayer 能播，只能证明：

- 地址大概率有效
- 服务端确实能返回可播放内容

但它不能证明 Android App 也会直接成功，因为两边的约束完全不同：

- Android 有 cleartext 安全策略
- ExoPlayer 的格式识别逻辑和桌面播放器不同
- 桌面播放器往往对“非标准入口 URL”容忍度更高

所以 PotPlayer 的价值是“排除源完全失效”，不是“证明 App 端实现没问题”。

## 八、当前方案的结论

要让当前这类源在 QTV 原型里播起来，至少要满足下面两件事同时成立：

1. Android 允许 HTTP cleartext
2. 播放器显式按 HLS 处理该 URL

缺一不可。

## 九、当前方案的代价

当前为了快速验证，做了两个偏原型化的取舍：

### 1. cleartext 全局放开

优点：

- 快速
- 真实流跳转链路不容易再被策略拦住

代价：

- 安全边界过宽
- 不适合作为最终生产策略

### 2. 直接把源按 HLS 处理

优点：

- 对当前测试源有效
- 避免入口 URL 不标准导致误判

代价：

- 默认假设当前频道就是 HLS
- 如果以后引入其他协议，播放器层要增加类型识别或配置字段

## 十、后续建议

如果项目从原型进入正式开发，建议沿着下面几条继续推进：

1. 在 `qtv.json` 里明确增加流类型字段，例如 `type: "hls"`
2. 播放器不要硬编码所有源都按 HLS，可根据配置切换
3. 增加更细的错误日志，包括响应头、跳转、最终 URL、ExoPlayer error code
4. 真实环境里重新评估 cleartext 策略，尽量缩小允许范围
5. 对多源频道增加自动 failover，避免单源波动直接黑屏

## 十一、一句话结论

这次把流媒体调通，真正的关键不是“播放器控件放上去了”，而是同时解决了：

- Android 对 HTTP 的安全限制
- ExoPlayer 对非标准 HLS 入口地址的格式识别限制

## 十二、后续 UI 状态回归记录

### 覆盖层打开后，底部默认提示不应残留

后续在把首页改成“默认大视频，按 Center 弹出频道列表”之后，出现过一个 UI 回归：

- 默认态底部提示为 `Press OK to open channels`
- 切到频道列表覆盖层后，这条提示在视觉上不应该继续出现

这类问题虽然不是流媒体核心链路错误，但会影响 TV 端交互判断，也会干扰调试。

最终修正原则：

1. 默认提示与覆盖层必须由同一个状态变量互斥控制
2. 覆盖层应显式提升绘制层级，避免底层提示残影或过渡期观感错误
3. 每次切换页面模式后，都应检查“上一状态的提示文案是否还可见”

## 十三、当前原型播放策略

为了让当前原型的行为更可控、也更便于定位问题，播放器策略已经进一步收敛为：

1. 每个频道只播放当前 `priority` 最高的一个主源
2. 不做自动切换到下一个备源
3. 报错后先立即重试一次同一主源
4. 如果再次失败，再按 10 秒间隔继续重试
5. 同一频道最多重试 3 次
6. 超过 3 次后停止重试，保留最后一次错误在屏幕上
7. 用户一旦切台，上一频道的错误与重试状态立即清空

这样做的原因不是最终产品策略，而是为了把“源本身有问题”与“播放器链路有问题”分得更清楚，减少自动 failover 带来的干扰。
