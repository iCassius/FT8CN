# 项目已完成改动与版本发布历史

本文件合并了项目所有已完成的改动记录、电台（如 YAESU FT-710）兼容性修复总结、最终代码差异分析以及历史版本发布日志。

---

## 第一部分：版本发布历史与变更日志

### v0.93.005 - P0-E 发布链与可回退测试包（准备中）

- **统一版本身份**：以 `ft8cn/gradle.properties` 为唯一来源，使用 `0.93.005` / `versionCode 93005` / `v0.93.005`；保留并禁止覆盖既有 `v0.93.004`。
- **正式签名 fail-fast**：正式构建缺少任一正式签名配置或 keystore 文件时直接失败，禁止回退到 debug 证书。
- **TEST/BETA 产物**：本机和普通 CI 生成 debug 签名、`com.bg7yoz.ft8cn.beta` 包名的独立 APK，文件名包含版本、beta 类型和短 commit，可与正式版共存。
- **发布门禁**：普通 CI/tag workflow 统一使用 JDK 17；Release notes 使用实际存在的 `doc/RELEASES.md`；加入版本、敏感信息、APK 签名和产物路径检查。
- **回退边界**：同包较低 `versionCode` 需要卸载，或在授权 adb 测试设备使用 `adb -d install -r -d`；BETA 通过独立包名共存，不冒充正式版。

本项只修改构建、workflow、发布文档和校验脚本，不修改运行时功能代码；正式证书指纹和真机/HIL 仍需在发布环境单独确认。

### v0.93.004 - Cloudlog/Wavelog 扩展与工作流优化
本版本打包了 FT8CN 最新的运行期健壮性改进和日志同步优化。
- **新增 Wavelog 日志同步支持**：支持与 Wavelog 平台无缝同步，并与现有的 Cloudlog 上传逻辑合并，支持自动匹配 Endpoint。
- **优化日志连接测试**：更新了 Cloudlog/Wavelog 测试流程，无需写入虚拟 QSO 即可验证 API 连接是否正常。
- **广播接收器注册安全强化**：针对 Android 13+ 的动态注册广播接收器要求进行适配，防止因缺少 Exported 标志而闪退。
- **修复 onBackPressed 警告**：优化了退出流程，在静默 lint 误报的同时保留了现有的完全退出逻辑。
- **构建工作流优化**：将 GitHub Actions 自动打包工作流从构建 debug APK 修改为编译并发布 release APK，并自动绑定 to Release 标签中。

### v0.93.002 / v0.93.003 - 核心解码卡顿、忙等发热与闪退稳定性修复
本版本是面向社区测试的稳定性修复版本，主要解决网络模式解码卡死、高发热以及全新安装时的闪退问题。
- **修复网络模式发射后解码卡住**：
  - 针对 WiFi 音频断流导致解码时隙错位的问题，在 `HamRecorder` 引入“新周期注册时强制结算未满窗口（补零）”策略。
  - 修复 `CopyOnWriteArrayList` 遍历中由于在回调中自删导致跳过下一个监听器的 Bug，避免每周期丢失约 160ms 音频。
- **消除发射忙等与烧 CPU 问题**：
  - 移除了 ICOM 和协谷 UDP 协议以及 `FT8TransmitSignal` 状态机中的忙等自旋，改用绝对时间表配合 10ms/20ms 的 sleep 节拍，大幅减少发热和因降频导致的解码性能下降。
  - 清除了发包线程结束时残留的 `interrupt` 标志，防止线程复用出现异常。
- **修复 DXCC 归属地与分区显示失效**：
  - 恢复呼号查询的最长前缀匹配查询（原版语义），修复此前将 SQL 改为精确匹配导致无法查到中国省级细分等前缀呼号的问题。
  - 补全了 CTY.DAT 经度西经为正的转换逻辑，并修复了 `CountDbOpr` 中的空指针风险。
- **解决瀑布图 bitmap 回收闪退**：
  - 采用互斥锁对 UI 线程的 `recycle` 和音频线程的 `draw` 绘图过程进行同步，并改为先创建新位图再回收旧位图的逻辑，解决“recycled bitmap”崩溃。
- **支持测试版与正式版并存（测试包）**：
  - debug 构建配置独立包名 `com.bg7yoz.ft8cn.beta`，桌面名称为 "FT8CN测试版"，追加 `-beta` 版本后缀。
  - 修复了 FileProvider authority 写死冲突的问题，支持两包完全独立安装和使用。
- **修复两个全新安装必崩的闪退 bug**：
  - **录音前台服务闪退**：当用户未授予录音权限启动 App 时，提前检查 `RECORD_AUDIO` 权限，未授权时不调用前台服务，且对前台服务启动方法添加 `try-catch` 防御。
  - **蓝牙/SCO 广播接收器闪退**：当用户未授予蓝牙运行时权限（`BLUETOOTH_CONNECT`）时，蓝牙耳机连接/断开或状态改变会触发崩溃。通过在 `onReceive` 处增加 `try-catch` 和 `getDeviceNameSafely` 来解决。
- **引入本地崩溃日志捕获**：
  - 新增 `FT8CNApplication`，将未捕获异常的完整堆栈、机型、版本等信息写入本地 `Android/data/com.bg7yoz.ft8cn/files/crash/`（限制最多 10 份，保护隐私不自动上传）。
- **自动化测试落地**：
  - 新增 `CallsignDatabaseTest` instrumented 自动化测试，覆盖呼号前缀最长匹配、西经非负等核心规则，防止以后再次出现 DXCC 逻辑回归。

### v0.93.001 - Android 14 兼容性与性能现代化升级 (Draft)
本版本是一次重大的维护性更新，重点关注系统兼容性、功耗降低、生命周期收敛以及电台兼容性，不改变原有 FT8 的核心使用流程。
- **升级 Android 14 兼容性**：更新了 Android Gradle Plugin，显式开启 `BuildConfig` 生成支持，并优化了 JVM 编译配置。
- **引入后台任务管理器**：建立全局 `AppExecutors` 集中管理后台多线程，防止线程无节制增长。
- **完善 ViewModel 生命周期清理**：重构 `MainViewModel` 的退出清理逻辑，确保在 ViewModel 被销毁（App退出或配置变更）时，后台定时器、音频录制、FT8 监听和内置 HTTP 服务能被完全释放和停用。
- **优化应用退出流程**：移除了暴力的 `System.exit(0)`，改用更加标准和优雅的 Android 应用退出通路。
- **降低功耗与发热**：
  - 用计划的 FT8 周期时隙定时器取代了高频的轮询计时，降低了挂机时的 CPU 占用和电池消耗。
  - 重构了瀑布图渲染机制，使用双缓冲并将最高重绘帧率限制在 5-10 FPS，避免无意义的高频刷新造成手机发热。
  - 音频缓冲区复用，避免高频创建 float buffer 带来的垃圾回收（GC）压力。
- **废弃 AsyncTask 并优化数据库**：将原有的呼号数据库和日志读写从已被 Android 废弃的 `AsyncTask` 转移到 `AppExecutors.diskIO()`，防止内存泄漏和操作卡顿。
- **改进数据结构与文本格式化**：
  - 在热点通路使用 `SparseIntArray` 替代传统的 Java Map 数据结构，减少自动装箱（Auto-boxing）开销。
  - 修复了多语言 `strings.xml` 中多处 XML 字符串占位符缺失位置参数（例如 `%d` 改为 `%1$d`）的警告，防止国际化环境下崩溃。
- **增强用户反馈与电台支持**：
  - 增加了连接状态、发射触发和 QSO 成功的气泡/Toast 提示。
  - 增加并恢复了对 YAESU FT-710 和 FTX-1 电台的 CAT 连接控制支持。

---

## 第二部分：YAESU FT-710 电台兼容性修复与差异分析

### 1. FT-710 机型支持改动汇总
- **机型识别入口**：
  - `InstructionSet.java` 中增加 `InstructionSet.YAESU_FT710 = 23`。
  - `rigaddress.txt` 资源文件中增加 `YAESU FT-710,00,38400,23` 条目，独立于 FTDX10 显示在机型列表中。
- **复用 DX10 CAT 指令集**：
  - 在 `MainViewModel` 中，当机型为 `YAESU_FT710` 时，复用 `YaesuDX10Rig` 实例，将具体的兼容性差异剥离出 CAT 指令层，下沉至 USB 串口共存层处理。
- **控制方式默认 CAT 化**：
  - 在 `ConfigFragment` 中，选择 FT-710 机型时，如果默认控制方式不是 `CAT`，则自动将其强制设为 `ControlMode.CAT` 并持久化，减少用户操作摩擦。

### 2. 核心串口只写修复（最关键修复项）
- **现象线索**：
  - FT8CN 连接 FT-710 复合 USB 设备后，Android 的 USB 音频会话会被破坏，导致系统自带播放器静音，且断开连接后需重启播放器才能恢复。
  - 调试日志显示发射时流模式下的 `AudioTrack.write(...)` 会返回 `0`，音频无法写入硬件。
- **根因研判**：
  - FT-710 的 USB 复合设备对 Android 系统的串口与音频并行占用极为敏感，FT8CN 原本继承自 DX10 的高频后台串口读取循环/轮询行为干扰了 USB 音频会话的稳定性。
- **修复方案 (`CableSerialPort.java`)**：
  - 新增 `shouldUseFt710WriteOnlyCatMode()` 判定。
  - 当组合为 **FT-710 + USB线 + CAT控制** 时，**不再启动后台串口读循环**（禁用 `usbIoManager.start()`），使 FT-710 的 USB CAT 变为**只写不读模式**。
  - 经实测，该修改彻底消除了外部音乐播放器静音的问题，并让 FT-710 的 `DATA-U` 模式能正常产生射频功率输出。

### 3. 外围稳定性改进
- **发射与录音切换保护 (`MicRecorder.java`)**：
  - 延迟创建 `AudioRecord` 并添加初始化失效时的重试与释放逻辑，防止发射后恢复录音时抛出 `AudioRecord startRecording called on an uninitialized AudioRecord`。
- **蓝牙状态广播收敛 (`BluetoothStateBroadcastReceive.java`)**：
  - 恢复 `shouldHandleBluetoothAudioRouting()` 校验，将蓝牙音频路由切换仅限制在当前处于 `BLUE_TOOTH` 连接模式下，避免在 USB 线模式下错误响应蓝牙状态。
- **CDC ACM 接口安全包装 (`CdcAcmSerialDriver.java`)**：
  - 封装 `claimInterfaceSafely(...)`，集中处理接口空指针并安全处理 force claim 逻辑。

---

## 第三部分：已证伪并回收的尝试 (FT-710 排障历史)

在解决 FT-710 发射无功率的过程中，以下尝试均已被证实对根因无效，并已从代码中清理回收：
1. **专属音频格式修改**：曾尝试强推 `48kHz / stereo / stream` 等不同采样率/声道格式以及音频静音前/后填充（Pre-roll/Post-roll），但对 `0` 功率没有本质改善。
2. **EX菜单命令参数猜测**：曾尝试通过 CAT 发送未经证实的 EX 菜单命令（如 `EX0104141;`）强改电台设置，经反馈无效，已去除。
3. **强制模式重写**：曾强行重写电台发射前后的工作模式，后证实非必需，现已恢复为保留电台当前模式。
