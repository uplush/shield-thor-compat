# 构建与接入

奥丁 3 新增组件请使用 `--profile odin3`，详细调用顺序见 [ODIN3.md](ODIN3.md)。
以下六类组件与 Mesa 配置说明沿用索尔基线；奥丁配置新增两个类、替换一个同名类，合计八类。
每次编译使用新的/空的 `--out` 目录，避免旧产物混入。

## 独立组件（本次执行了编译验证）

- Python 3.10+；JDK 11+（实际测试使用 Android Studio JBR）。
- Android SDK Platform 35、Build Tools 35.0.0。
- Android NDK 28.2.13676358 / r28c，ARM64 目标 API 28。
- Git（Mesa 补丁工具使用 `git apply`）。

在仓库根目录运行 README 中的 `run_tests.py` 和 `build_components.py`。
支持 `JAVA_HOME`、`ANDROID_HOME` / `ANDROID_SDK_ROOT`、`ANDROID_NDK_HOME`；
显式路径参数优先。脚本不下载依赖、不连接设备、不安装/卸载应用。
JNI 库使用 16 KiB 最大页对齐参数；这并不是整套游戏的 16 KiB 页兼容性认证。

产物与集成含义：

- `port-helpers.jar` / `dex/classes.dex`：六个新增 Java 类及其内部类，没有宿主 Activity/Manifest。
- `libsmg_video.so`：需要应用内可加载的 `libEGL_mesa.so` 与正确的当前上下文。
- 这些文件不是独立应用，不能作为 APK 安装。

## Mesa 基线与补丁

固定基线：

- 仓库：<https://gitlab.freedesktop.org/Swung0x48/mesa>
- 提交：`9ea0decb3176d34e578c3c603fd9c8e29b580f71`
- [源码归档](https://gitlab.freedesktop.org/Swung0x48/mesa/-/archive/9ea0decb3176d34e578c3c603fd9c8e29b580f71/mesa-9ea0decb3176d34e578c3c603fd9c8e29b580f71.tar.gz)
- 研究时归档 SHA-256：`9a73f58fa70ebdabf9504eb51ccb7baca4d01cee3ece5f5dd2e681287691b2ea`

归档重新生成时压缩文件哈希可能改变，补丁工具仍会按 `patches/manifest.json` 检查相关源码文件。
哈希不符时应核对版本，不要删掉校验硬套补丁。

```sh
python tools/mesa_patch.py apply --source /src/mesa-base --output /src/mesa-thor
python tools/mesa_patch.py verify --source /src/mesa-thor --state modified
```

当前补丁包含全部 13 个有源码语义差异的路径，不包含 Python 缓存。
`provenance/upstream_*.patch` 仅用于保留参考来源和署名，**不要在合并补丁上再次应用**。

### Mesa 编译配置参考（不是自动化干净环境重建承诺）

原研究使用 Meson/Ninja、NDK r28c、静态 libdrm 2.4.114、Mako、packaging、Flex/Bison。
目标为 `aarch64-linux-android28`；Windows 的构建工具与 Android 交叉编译工具必须分开。
交叉文件需要配置正确的编译器、`pkg-config` 以及目标 libdrm 路径。

```sh
meson setup build-mesa mesa-thor --cross-file android-aarch64.ini \
  -Dplatforms=android -Dplatform-sdk-version=30 -Dandroid-stub=true \
  -Dxlib-lease=disabled -Degl=enabled -Dgbm=disabled -Dglx=disabled \
  -Dllvm=disabled -Dopengl=true -Dvulkan-drivers= -Dgallium-drivers=zink \
  -Dzstd=disabled -Dglvnd=false -Degl-lib-suffix=_mesa \
  -Dbuildtype=release -Dstrip=false -Dvalgrind=disabled \
  -Dlibunwind=disabled -Dbuild-tests=false
ninja -C build-mesa src/gallium/targets/dri/libgallium_dri.so
```

这些是本地驱动构建的已记录选项，不是现成的跨平台 SDK。
本次发布验证了补丁正向/反向应用、独立 Java/JNI 编译与原生测试探针编译；
没有重新从零配置 Mesa/Turnip 的所有依赖，更没有从该公开仓库重建完整游戏。

## 真实 Mesa 生命周期探针

在已配置且构建了 `src/util/libmesa_util.a` 的 Android Mesa 构建目录上：

```sh
python tools/build_lifetime_probe.py --mesa-build /src/build-mesa --out /tmp/lifetime_probe
```

该程序使用真实 Mesa set/refcount API 和合成资源，不含游戏代码。
运行需要与编译目标匹配的 Android ARM64 环境；脚本只编译，不自动部署。
可分别以 `baseline` 和 `modified` 参数运行。它使用计数但不释放内存的析构替身，
从逻辑上检测所有者释放后队列是否仍持有引用，不会故意读取已释放内存。

## 回滚

```sh
sh ROLLBACK.sh --source /src/mesa-thor --output /src/mesa-restored
# Windows 或没有 sh 的环境使用等价命令：
python tools/mesa_patch.py rollback --source /src/mesa-thor --output /src/mesa-restored
```

回滚仅生成新的源码副本；它不更改已安装游戏，不动存档，也不撤回 GitHub 发布。
输出目录必须不存在，以免覆盖已有工作。
