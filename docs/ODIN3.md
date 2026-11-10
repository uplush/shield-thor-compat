# 奥丁 3 源码预览：配置、接入与回滚

## 适配配置

`profiles/odin3.json` 是人工接入配置记录，不是自动启动器或自行写入环境的脚本。
本地实测设备是 AYN Odin 3 / Android 15 / Adreno 830。
索尔版是共同组件的起点，不代表奥丁仍使用索尔的专属 GPU 驱动。

- 使用 Stable Turnip **sync** V2；链接、库 SHA-256 见配置文件。
  该库不随本仓库分发。上游源码分支 `turnip/26.2` 仅提供追溯线索，未固定到可重复构建的提交。
- `TU_DEBUG=sysmem`；`GALLIUM_THREAD` 和 `ZINK_DEBUG` 保持未设置。
  此前关闭线程队列的直接提交、`ZINK_DEBUG=rp` 实验引起闪烁，均已排除。
- `frame_rate_hint.h` 仅对成功创建的私有 EGL 窗口请求 60 Hz、无缝切换。
  它不是帧率保证，不设置全局显示模式，也不调整分辨率、swap interval 或 present mode。
- `thread_timing.h` 仅在私有 EGL 的 `eglMakeCurrent` 成功后调用
  `port_thread_timing_bound(context != EGL_NO_CONTEXT)`。
  当前线程原定时器合并容差高于 10000 ns 时才调小；成功解绑时恢复。
  外部已修改的值不覆盖，恢复失败保留状态供再次尝试，调用前 `errno` 保留。
- 两个头文件由宿主提供 `LOG`；窗口提示还需要 Android native window、pthread、dlfcn 和 errno 头文件。
  独立编译探针在 `tests/odin3/header_probe.c`，不是完整 EGL wrapper。

本版不切换系统性能档位、不绑定 CPU、不降低画质。
用户已接受当前游戏帧率；复杂场景恒定 60 FPS 未获验证。

## 字幕偏晚：修复的两层原因

1. 解码器/分离器进度不是屏幕上正在显示的视频时间；丢帧、排队与 SurfaceTexture 合并帧会拉开差距。
   新时间轴用实际成功上传的视频帧 PTS。提交 token 与 SurfaceTexture timestamp 精确匹配，
   未匹配时不借用最近解码时间来猜测。
2. 原字幕触发要求恰好等于目标帧。跳过那一帧就可能错过触发。
   配套逻辑改为 `previous < cueStart <= presented`，不修改台词或逐句时间表。
   正常丢帧下跨过的字幕仍能及时触发。

`SubtitleTimeline` 在丢弃视频帧之前观察首个解码 PTS，以此为原点；
`frame = 1 + round((pts - origin) * fps / 1e6)`。
待提交队列上限为 64，处理重复、合并、溢出与 flush；flush 清理待提交项而保留原点。
首次出现电影重置上一字幕帧；时钟倒退有保护。
这不是任意远距离前跳的过期字幕处理系统；大跨度 seek 需额外定义策略。

### 宿主调用顺序（需要接入，不由组件编译器自动完成）

1. 初始化 codec、SurfaceTexture、原生 player id 和真实帧率后调用
   `PortSubtitle.bind(codec, surface, id, fps)`。
   `libsmg.so` 必须已经载入；原生版式/指令检查不通过就终止该接入。
2. 用奥丁版 `PortMovie.dequeue` 保留音频追帧处理，并在丢弃之前观察 PTS。
3. 保存该输出 buffer 的 `presentationTimeUs`。在
   `releaseOutputBuffer(index, tokenNs)` 之前调用
   `PortSubtitle.queue(codec, tokenNs, presentationTimeUs)`；使用同一个唯一 token。
4. `PortVideo.update(surface)` 成功上传纹理后才调用 `PortSubtitle.presented(surface)`。
   失败时不推进字幕时钟；移除旧的 extractor sample time / delivery-count 时钟更新。
5. codec flush 后调用 `PortSubtitle.flush(codec)`；每段新片重新 bind。
   最终销毁时先 `PortSubtitle.release(surface)` 再释放视频资源；保留现有音频和生命周期清理顺序。
6. 同时应用下面的 ALF/native 两部分补丁；只加 Java helper 或只改一个二进制不会构成完整修复。

JNI 通过已载入库查询 player，发布帧到 `player+0x18`、秒数到 `player+0x1c`。
它检查若干已知指令/导出地址，但不是任意版本的内存布局探测器。
只适用于补丁清单标识的整文件版本，不能按相同文件名推断兼容。

### 精确输入上的副本补丁

`patches/odin3-subtitle.json` 记录整文件 SHA-256、长度、偏移和极小的修改区域。
`alf` 输入是解封装后的指定 `product.alf`；`native` 输入是本地适配链中的指定
`libsmg.so`，不是随便一个原版 APK 中的同名库。
本预览不提供输入文件、解封装密钥、资源重新封装或完整调用点自动改写器。

```sh
python tools/subtitle_patch.py verify --kind alf --source /local/product.alf --state baseline
python tools/subtitle_patch.py apply --kind alf --source /local/product.alf --output /local/product-subtitle.alf
python tools/subtitle_patch.py apply --kind native --source /local/libsmg.so --output /local/libsmg-subtitle.so
# 在另一新文件回滚，保留原始输入与修复副本：
sh ROLLBACK.sh subtitle --kind alf --source /local/product-subtitle.alf --output /local/product-restored.alf
sh ROLLBACK.sh subtitle --kind native --source /local/libsmg-subtitle.so --output /local/libsmg-restored.so
```

Windows 无 sh 时使用 `python tools/subtitle_patch.py rollback`，参数相同。
`ROLLBACK.sh` 不带 `subtitle` 时仍执行原 Mesa 源码回滚。
工具拒绝同路径/已有输出、错误整文件哈希或补丁字节；正反向都核对结果哈希。
回滚只生成副本，不修改已安装应用或 GitHub 历史。

## 构建与测试的边界

`--profile odin3` 使用 `src/odin3/java` 覆盖同名帮助类，其余沿用 `src/java`。
输出八个顶级 Java 帮助类、DEX、`libsmg_video.so`、`libsmg_subtitle.so`、
Android 原生字段写入测试可执行文件和头文件编译探针。
DEX 内没有原游戏宿主类；这是组件编译成功，不是游戏 APK 构建成功。

主机测试中的 codec/Android 类型是替身，原生字段测试只在合成 player 内存上运行。
测试本身不会启动游戏，也不证明 SurfaceTexture 或 GPU 在真实设备上的完整行为。
最近一轮设备结果及本次源码测试命令分别见 `docs/TESTING.md` 与根目录 `VERIFICATION.txt`。
