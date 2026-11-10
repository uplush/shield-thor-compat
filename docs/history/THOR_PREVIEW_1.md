# Shield → Thor compatibility research

**索尔（AYN Thor）源码预览版 · v0.1.0-thor-preview.1**

面向神盾 Android 版《超级马力欧银河》的独立兼容研究。这里发布可独立整理的适配组件、
Mesa 修改补丁、测试与技术说明，**不是游戏开源版，也不含游戏安装包**。
项目与 Nintendo、NVIDIA、AYN 无隶属或认可关系。

> **下载前请看：本版是开发者源码预览，不是可直接安装的 APK。**
> 下面的命令可以编译兼容组件、验证并应用 Mesa 补丁；仅克隆本仓库还不能重建完整游戏移植版。
> 原游戏调用点接入、部分着色器规则、私有接口适配与端到端打包流程尚未整理进本次公开版本。
> 使用这些模块不等于已获得游戏文件的再分发授权。

## 本版内容

| 目录 | 内容 |
|---|---|
| `src/java/` | 过场视频桥接、有限追帧、后台 Surface 保留、资源准备/校验 |
| `src/native/video_upload.c` | Android 视频 RGBA → 应用内私有 Mesa 纹理的 JNI 上传 |
| `patches/mesa-thor.patch` | 相对固定 Mesa fork 的完整 13 文件差异，包括共享 bindless、legacy vertex、barrier 生命周期修复 |
| `tools/` | 独立组件编译、主机测试、带哈希检查的补丁应用与回滚 |
| `tests/` | 合成资源测试、Android API 替身、使用真实 Mesa 类型/API 的原生生命周期测试 |
| `docs/` | 架构、构建/接入说明、脱敏实测摘要和发布说明 |
| `provenance/`、`LICENSES/` | 源码来源、上游补丁原始署名与许可 |

**仓库不包含**：游戏资源、APK/DEX/原厂私有库、反编译游戏类、提取的游戏着色器、
签名材料、设备标识、存档或设备原始日志。Java 的原有包名仅为本地接入 ABI 保持兼容，
这些新增类不是 NVIDIA 官方开源代码。

## 已验证与未验证

本次源码来自在索尔上通过测试的本地实验版本：

- 两次独立进程启动后，开场库巴过场均自然播放完毕：5368 / 5360 帧。
- 两轮对应音轨欠载均为 0；用户确认画面、声音连续同步，返回游戏后移动/跳跃正常。
- 这些是**本地集成 APK 的历史实测**，不是仅凭本仓库源码构建出的游戏的测试结果。
- 主机测试和独立组件编译结果见 [VERIFICATION.txt](https://github.com/uplush/shield-thor-compat/blob/v0.1.0-thor-preview.1/VERIFICATION.txt)。
- 未验证全流程通关、所有过场、长期稳定性；没有保证恒定 60 FPS。
- **奥丁 3 尚未实机验证**，本版不宣称支持所有 Android 掌机。

详见 [测试记录](https://github.com/uplush/shield-thor-compat/blob/v0.1.0-thor-preview.1/docs/TESTING.md) 和 [发布说明](https://github.com/uplush/shield-thor-compat/blob/v0.1.0-thor-preview.1/docs/RELEASE_NOTES.md)。

## 快速开始

主机测试需要 Python 3.10+ 和 JDK 11+（本机验证使用 Python 3.14 / Android Studio JBR）：

```sh
python tools/run_tests.py --java-home /path/to/jdk
```

编译 Java / DEX 与 ARM64 视频 JNI，**不构建或安装游戏 APK**：

```sh
python tools/build_components.py \
  --sdk /path/to/android-sdk \
  --ndk /path/to/android-ndk-r28c \
  --java-home /path/to/jdk
```

使用 SDK Platform 35、Build Tools 35.0.0、NDK 28.2.13676358。
产物在 `.build/components/`：`port-helpers.jar`、`dex/classes.dex`、`libsmg_video.so`。
首次预览只对 Windows 构建实际执行了验证；其他主机的路径支持仍待测试。

Mesa 基线**不是任意上游 23.0.4**，而是 Swung0x48/mesa 的固定提交
`9ea0decb3176d34e578c3c603fd9c8e29b580f71`。准备好该源码后：

```sh
python tools/mesa_patch.py verify --source /path/to/mesa-base --state baseline
python tools/mesa_patch.py apply --source /path/to/mesa-base --output /path/to/mesa-thor
# 回滚到另一份新目录，保留 mesa-thor 与最初源码：
sh ROLLBACK.sh --source /path/to/mesa-thor --output /path/to/mesa-restored
```

输出目录必须尚不存在且与输入互不嵌套。源码复制会排除 `.git`、`.gitlab-ci` 和 Python 缓存；
补丁涉及文件统一为 LF 换行，按清单验证 SHA-256。完整依赖与接入契约见 [BUILD.md](https://github.com/uplush/shield-thor-compat/blob/v0.1.0-thor-preview.1/docs/BUILD.md)。

## 许可与贡献

本项目原创部分使用 [MIT](https://github.com/uplush/shield-thor-compat/blob/v0.1.0-thor-preview.1/LICENSE)；Mesa/Khronos 等第三方部分遵循各自原始许可。
见 [第三方说明](https://github.com/uplush/shield-thor-compat/blob/v0.1.0-thor-preview.1/THIRD_PARTY_NOTICES.md)。请保留来源署名；不要把整棵 Mesa 都重新标为本项目原创。

欢迎提交可复现的兼容问题和小型补丁。问题报告建议包括：设备型号、Android 版本、
测试版本、复现步骤、是否影响音视频/后台恢复；日志请先移除账号、设备标识和本地路径。
请勿在 issue / PR / release 中上传游戏文件、存档或签名材料。

---

**English summary:** Source-only preview of independent compatibility components and a pinned
Mesa patch set for AYN Thor. No game files, APK, private vendor binaries, extracted shaders or
complete game rebuild pipeline are distributed. Two historical local integrated-game cutscene
runs passed; this is not a full-game stability certification. Odin 3 remains untested.
