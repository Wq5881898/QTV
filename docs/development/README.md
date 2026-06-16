# QTV 开发文档

这组文档面向后续开发和维护，重点记录当前原型的代码结构、功能边界，以及这次把流媒体源真正调通时的排查过程。

## 文档索引

- [project-structure.md](project-structure.md)
  项目目录、核心文件、运行链路
- [implemented-features.md](implemented-features.md)
  当前已经落地并验证成功的功能
- [stream-playback-debugging.md](stream-playback-debugging.md)
  流媒体源调通过程、关键问题、最终解法

## 当前版本定位

当前仓库处于 `v0.1.0-prototype` 阶段，目标不是产品完备，而是完成以下闭环验证：

1. Android TV 形态应用可正常启动
2. 本地 `qtv.json` 可被读取
3. 频道可通过 D-pad 选择
4. HLS 直播流可在 Android TV 模拟器内播放
5. HTTP 源在当前原型里可工作

## 阅读顺序建议

第一次接手项目，建议按下面顺序阅读：

1. [project-structure.md](project-structure.md)
2. [implemented-features.md](implemented-features.md)
3. [stream-playback-debugging.md](stream-playback-debugging.md)
