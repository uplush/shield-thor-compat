# 架构与接入边界

原神盾 Android 版的主要执行逻辑和资源保留在用户本地；本仓库不包含它们。
本地实验的图形路径为：游戏 OpenGL 调用 → 应用内 Mesa/Zink → Vulkan → Turnip → Adreno。
不更换系统 GPU 驱动，不刷固件。

## 本次公开模块

### 视频：PortVideo + video_upload.c

`PortVideo` 自有的 Android EGL/GLES 上下文从解码器 SurfaceTexture/OES 取图，
应用 SurfaceTexture 变换矩阵，转换为 RGBA；JNI 在私有 Mesa 当前上下文中上传。
`video_upload.c` 保存并恢复纹理绑定、PBO 绑定和相关像素解包状态。
系统 GLES 纹理和私有 Mesa 纹理没有被假定为可直接共享。

接入方必须在解码开始前创建/绑定 SurfaceTexture，通知视频尺寸，
在具有正确私有 Mesa 当前上下文的渲染路径调用 `update`，并负责销毁顺序。
模块不会自行创建整套解码器或游戏线程。

### 时钟：PortMovie

保留 audio-only MediaSync；不把视频送入 MediaSync input surface。
过期帧每次调用最多跳过 3 个，总共最多取出 4 个输出，避免单次工作无限增长。
目标时钟结合音频时间戳与单调时钟；暂停、循环和关闭路径应调用 `reset`。
本模块不改 PCM 数据，不会单独保证每个设备上的声音质量。

### 后台：PortGameView / PortLifecycle

暂时离开窗口时保留 SurfaceTexture/Surface，返回时重新连接；最终退出才 `dispose`。
游戏正常的原生音频/输入/图形暂停流程仍需由宿主调用，不能用此模块替代。
`PortLifecycle` 仅输出事件诊断；这不是跨进程存档或 Android 杀进程后的状态恢复。

### 资源：BundleStore / PortBundle

只处理接入方自带清单中列出的资源；校验长度和 SHA-256 后原子提交。
支持取消、剩余空间检查、已完成文件复用与失败重试；启动门控确保资源完成后才启动原生部分。
仓库没有实际游戏资源清单；测试使用随机数据。

## Mesa 修复

合并补丁基于固定 fork，而不是最新 Mesa：

1. 共享上下文间的 bindless texture handle 所有权/驻留管理。
2. bindless 初始化与 pending descriptor 处理的适配。
3. `VK_EXT_legacy_vertex_attributes` 定义与按功能判断的能力声明。
4. `need_barriers` / `update_barriers` 的资源引用生命周期。

第 4 项给每个唯一的延迟同步队列条目持有一个 `pipe_resource` 引用；
重复插入不多持有，移除时释放，跨集合转移先取得目标引用再释放来源引用，析构时清空四个集合。
这不是跳过 GPU 同步或屏蔽非法指针。

本地捕获的真实崩溃位于 `zink_resource_image_barrier2`，涉及无效的资源对象指针。
队列裸指针的生命周期缺口已确认并修正；历史上具体哪一次释放造成该崩溃未被逐次追踪。
两轮修复后游戏实测通过仍不等于排除所有崩溃来源。

## 尚未形成公开端到端流程的部分

- 原游戏调用点注入、启动/音量键修改和私有平台接口接入。
- 原游戏动态着色器转换规则及其来源审查。
- 私有驱动加载路径、Turnip 构建/加载集成以及整套 APK 打包。

这些差距不会由“自备 APK”自动解决。首版的目标是公开可审阅、可单独编译/测试的组件和完整 Mesa 差异，
后续再逐项完善可复现接入工具，而不是提供一个看似完整但依赖隐藏文件的构建命令。
