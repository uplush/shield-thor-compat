# Shield → Thor / Odin 3 compatibility research

**奥丁 3（AYN Odin 3）源码预览版 · v0.2.0-odin3-preview.1**

面向神盾 Android 版《超级马力欧银河》的独立兼容研究。
本版在索尔组件基础上，整理奥丁 3 配置、局部性能适配及最新过场字幕同步修复。
项目与 Nintendo、NVIDIA、AYN 无隶属或认可关系。

> **这是开发者源码预览，不是可直接安装的游戏 APK。**
> 仓库提供独立兼容组件、补丁、测试和接入说明，不包含游戏本体或资源。
> 克隆仓库可以构建帮助类和 JNI 库，但还不能一键重建完整移植版。
> 原游戏调用点、部分私有接口与着色器规则、资源重新封装及完整 APK 打包流程仍不在该预览中。

## 版本与验证状态

| 版本 | 状态 |
|---|---|
| [奥丁 3 v0.2.0](https://github.com/uplush/shield-thor-compat/releases/tag/v0.2.0-odin3-preview.1) | 本地集成版实机测试通过；用户确认字幕出现时机正常 |
| [索尔 v0.1.0](https://github.com/uplush/shield-thor-compat/releases/tag/v0.1.0-thor-preview.1) | 保留原发布；两次独立开场过场回归通过 |

奥丁 3 最近一轮开场过场自然播完，6738 次提交成功关联、未匹配 0，
对应音轨欠载 0，返回游戏后进程仍在；用户确认字幕时机正常。
**这些是本地集成游戏的历史实测，不是本次公开源码单独构建完整游戏的结果。**
保持原画质；当前帧率已获用户接受，但不宣称所有场景恒定 60 FPS。
其他过场、全流程通关和长期稳定性尚待验证。

## 本版新增

- `profiles/odin3.json`：奥丁 3 驱动/提交配置，保留正常线程队列与原渲染质量。
- `src/odin3/java/`：字幕时钟按实际上传的视频 PTS 前进，避免解码进度/提交计数与显示画面漂移。
- `src/odin3/native/`：字幕 JNI、60 Hz 窗口提示、GL 线程定时器合并容差恢复逻辑。
- `patches/odin3-subtitle.json`：严格匹配指定原文件哈希的小范围补丁；字幕触发改为跨帧区间检测。
- 新增时间轴、奥丁视频追帧及补丁工具回归；索尔原有测试继续保留。

原有 `src/java/`、`src/native/` 和 Mesa 13 文件补丁仍保留。
索尔历史说明见 [原发布](https://github.com/uplush/shield-thor-compat/tree/v0.1.0-thor-preview.1)。
仓库名称保持不变，避免破坏既有链接。

## 快速开始

依赖：Python 3.10+、JDK 11+、Android SDK Platform 35 / Build Tools 35.0.0、NDK r28c。

```sh
python tools/run_tests.py --java-home /path/to/jdk
python tools/build_components.py --profile odin3 \
  --sdk /path/to/android-sdk --ndk /path/to/android-ndk-r28c \
  --java-home /path/to/jdk
```

默认 `--profile thor` 继续编译原六个帮助类；奥丁配置编译八个帮助类及两个 JNI 库。
每次构建请使用新的/空的输出目录（`--out`），防止两种配置残留类混合。
编译不会安装应用、连接设备或改动存档；本轮实际验证的构建主机为 Windows。

- [奥丁 3 接入与字幕原理](docs/ODIN3.md)
- [通用构建与 Mesa 接入](docs/BUILD.md)
- [测试范围](docs/TESTING.md)、[确切发布验证记录](VERIFICATION.txt)
- [发布说明](docs/RELEASE_NOTES.md)、[来源与第三方许可](THIRD_PARTY_NOTICES.md)

## 发布内容与许可

仅发布审阅的文本源码、少量补丁数据、文档、测试和许可。
不包含 APK/DEX、游戏资源、原厂私有库、反编译游戏类、提取的游戏着色器、
存档、原始设备日志、设备标识或签名材料。
原创组件使用 [MIT](LICENSE)，上游部分保留原许可；游戏文件的权利不随本项目许可转移。
新增 Java 类保留原包名用于接口接入，不代表 NVIDIA 官方代码。

欢迎提交脱敏的设备型号、Android 版本、测试版本、复现步骤与小型补丁。

**English:** Source-only compatibility preview for AYN Odin 3 and Thor. The Odin profile adds
presented-video subtitle timing, exact-preimage patch tooling and application-local timing hints.
Historical integrated-game tests passed; this repository is not a complete game rebuild pipeline.
No game binaries/assets, signing material or private device data are distributed.
