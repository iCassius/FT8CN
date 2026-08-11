# FT8CN 项目说明与架构总览

> 文档状态：当前产品与技术事实说明<br>
> 审计基线：`codex/v0.93.005-16kb-integration@9fbda6f`；本地文档收口分支从远端 `codex/v0.93.005-integration@786ceed4` fast-forward 创建<br>
> 审计日期：2026-08-11<br>
> 适用对象：用户、测试人员、维护者、产品负责人、技术负责人

## 1. 一句话说明

FT8CN 是一款在 Android 手机上原生运行的移动 FT8 通联软件。它把收音、解码、呼叫、电台控制、发射、日志和统计放在一个应用中，主要服务户外、便携台和不方便携带电脑的业余无线电用户。

本仓库是 BG7YOZ 原版 FT8CN 的社区维护版，已经独立维护。当前取舍原则是：

1. 稳定性；
2. 设备兼容性；
3. 性能与续航；
4. 新功能。

依据：`PROJECT_RULES.md` 第 6-17 行。

## 2. 用户价值

### 2.1 核心用户

- 希望用手机完成 FT8 通联的户外和便携台用户；
- 使用 USB、蓝牙、网络电台连接方式的 Android 用户；
- 希望在手机上查看呼号归属、通联历史、统计和地图的用户；
- 希望把日志同步到 Cloudlog、Wavelog 或 QRZ 的用户。

### 2.2 用户得到什么

- 不依赖电脑即可完成 FT8 收听和通联；
- 用同一个界面完成解码、选台、标准消息往返和日志保存；
- 支持声控、USB CAT、蓝牙和部分网络电台；
- 支持瀑布图、DXCC/CQ/ITU 标记、关注呼号和自动呼叫；
- 支持本地 QSO/SWL 记录、ADIF 导入导出和第三方日志同步；
- 可用测试版与正式版并存，降低社区测试对正式数据的影响。

### 2.3 产品边界

- FT8 解码依赖手机时间、音频质量、CPU 性能和预编译原生解码库；
- 不同电台的 USB 复合设备、CAT 指令和音频路由差异很大，不能用一台电台的成功代替全部设备验证；
- 模拟器构建、启动或数据库测试不等于真实音频解码、真实发射或完整 QSO；
- 本轮独立证据覆盖自动化与 16KB AVD：API 37 / `PAGE_SIZE=16384` 启动成功，connected 测试 63 pass、1 intentional skip；4KB AVD、真机、真实电台、长时挂机、功耗和温升仍未完成，不能把本轮结果写成 HIL。

## 3. 主要用户流程

### 3.1 首次使用

1. 安装并启动应用；
2. 授予录音权限，按连接方式选择性授予蓝牙、位置等权限；
3. 设置自己的呼号和梅登海德网格；
4. 选择电台型号、连接方式、波特率、控制方式和发射参数；
5. 检查频谱是否有输入，确认时间偏移可用；
6. 进入解码列表开始收听。

### 3.2 接收与解码

1. 麦克风、USB 音频或网络音频进入统一录音缓冲；
2. 应用按 UTC 15 秒时隙收集样本；
3. 原生解码器输出 FT8 消息；
4. 应用补充呼号、网格、DXCC、CQ、ITU 和历史通联信息；
5. 消息显示到解码列表、呼叫列表和瀑布图。

### 3.3 呼叫与自动通联

1. 用户长按或滑动消息选择目标，也可以发起 CQ；
2. 发射状态机生成标准 FT8 消息序列；
3. 到达目标时隙后控制 PTT；
4. 音频通过手机声卡、CAT 音频或网络 UDP 发送；
5. 收到对方回复后推进消息序号；
6. 完成后写入 QSO 日志，并按配置上传第三方服务。

### 3.4 日志与统计

1. 本地 SQLite 保存配置、QSO、SWL、呼号和网格数据；
2. 通联记录页面支持查看、筛选、导入和导出；
3. 内置局域网 HTTP 服务提供日志后台；
4. 统计和地图展示波段、DXCC、网格和历史记录；
5. Cloudlog、Wavelog、QRZ 可按配置同步。

## 4. 当前功能范围

### 4.1 已有功能

- FT8 快速解码和深度解码；
- 标准 FT8 消息、CQ、自动回复、自由文本；
- 麦克风、USB 音频、蓝牙 SCO、网络音频；
- VOX、CAT、RTS、DTR 等控制方式；
- ICOM、YAESU、KENWOOD、协谷、FlexRadio、(tr)uSDX 等多类电台适配；
- 解码列表、呼叫列表、频谱瀑布、日志、设置五个主入口；
- DXCC、CQ、ITU、网格、JTDX 优先级和历史通联标记；
- QSO、SWL、ADIF、局域网日志后台；
- Cloudlog、Wavelog、QRZ 同步；
- 本地崩溃日志；
- debug 测试包与正式包并存。

### 4.2 当前不应宣称已经完成的能力

- 所有连接方式均已长时间稳定；
- 所有支持电台均已真机验证；
- 网络模式发射后解码卡住已在真实 QSO 中彻底证实解决；
- 当前版本已经完成 2 小时挂机、温升和功耗验收；
- Wavelog 的所有 Endpoint 和 station ID 组合均已验证；
- 不应宣称所有 Android 15+ 设备、4KB 页设备和真实设备都已经完成 16KB 原生兼容验证；
- GitHub Release 产物已经使用固定正式证书签名；
- 当前自动化可以覆盖完整 FT8 主链。

## 5. 架构图

```text
用户
  │
  ▼
MainActivity + Navigation
  ├─ 解码列表 / 呼叫列表
  ├─ 频谱与瀑布图
  ├─ 通联记录
  ├─ 设置
  └─ 统计 / 地图 / 电台信息
  │
  ▼
MainViewModel（当前应用编排中心）
  ├─ GeneralVariables（全局配置、状态、缓存、LiveData）
  ├─ DatabaseOpr（配置、QSO、SWL、网格）
  ├─ CallsignDatabase（CTY.DAT 呼号前缀）
  ├─ LogHttpServer（局域网日志后台）
  │
  ├─ 接收链
  │    MicRecorder / 网络 Connector
  │      → HamRecorder
  │         ├→ SpectrumListener → JNI FFT → WaterfallView
  │         └→ 15 秒窗口 → FT8SignalListener
  │                         → libft8cn.so
  │                         → Ft8Message
  │
  └─ 发射链
       用户选择 / 自动状态机
         → FT8TransmitSignal
         → GenerateFT8
         ├→ AudioTrack
         ├→ CAT 音频
         └→ ICOM / 协谷 UDP
         → BaseRig / Connector → PTT / 频率
         → QSO 入库 → Cloudlog / Wavelog / QRZ
```

## 6. 模块职责

### 6.1 UI 与应用编排

- `MainActivity`：权限、导航、启动遮罩、USB 设备选择、蓝牙广播和退出流程；
- `MainViewModel`：拥有录音、解码、发射、电台、数据库、频谱和 HTTP 服务，是当前实际的应用编排器；
- `ui/`：解码、呼叫、频谱、日志、设置和电台信息页面；
- `grid_tracker/`、`count/`：地图和统计。

### 6.2 音频与解码

- `MicRecorder`：Android `AudioRecord` 采音；
- `HamRecorder`：把同一份音频分发给 15 秒解码窗口和 160ms 频谱窗口；
- `FT8SignalListener`：按 UTC 时隙启动解码，调用 JNI；
- `SpectrumListener`、`SpectrumView`、`WaterfallView`：频谱和瀑布图；
- `app/libs/*/libft8cn.so`：FT8 编解码和 FFT 的原生实现。

### 6.3 发射与自动 QSO

- `FT8TransmitSignal`：目标、时隙、消息序号、自动通联和发射生命周期；
- `GenerateFT8`：生成发送波形；
- `BaseRig`、`rigs/`：电台行为抽象和各型号 CAT 适配；
- `connector/`、`icom/`、`flex/`、`x6100/`：USB、蓝牙、TCP、UDP 和网络电台通道。

### 6.4 数据与同步

- `DatabaseOpr`：配置、QSO、SWL、呼号网格和查询；
- `CallsignDatabase`：导入 `cty.dat`，按最长前缀查询国家和分区；
- `LogHttpServer`：局域网日志浏览、导入和导出；
- `ThirdPartyService`：Cloudlog、Wavelog、QRZ 请求。

## 7. 关键数据流

### 7.1 接收数据流

```text
AudioRecord / 网络音频包
  → HamRecorder.doOnWaveDataReceived
  → 160ms 循环监听器 → SpectrumListener → FFT → 瀑布图
  → 15s 一次性监听器 → FT8SignalListener.decodeFt8
  → native decoder
  → Ft8Message 列表
  → 呼号与历史补充
  → LiveData
  → UI / 自动 QSO / SWL 入库
```

音频断流时，当前实现会在新时隙开始时把旧的一次性窗口补零并结算，防止旧窗口吞入下一时隙音频。该修改方向正确，但真实网络 QSO 尚待验证。

### 7.2 发射数据流

```text
用户选择目标 / 自动发现目标
  → FT8TransmitSignal 生成当前消息
  → UTC 时隙触发
  → PTT ON
  → 等待 pttDelay
  → AudioTrack / CAT / UDP 发送
  → PTT OFF
  → 状态机等待下一条回复
  → 完成后 QSO 入库和第三方同步
```

### 7.3 配置加载流

```text
MainActivity.InitData
  → DatabaseOpr.diskIO 查询 config
  → 写入 GeneralVariables
  → 主线程完成回调
  → 设置时隙参数 / 首次设置导航 / USB 恢复
```

当前确认问题是：数据层会逐行回调，界面层却把每次回调都当成“全部完成”，需要在发版前修正。

## 8. 线程模型与生命周期

### 8.1 已有统一执行器

`AppExecutors` 当前定义：

- 磁盘 IO：单线程；
- 网络 IO：3 线程；
- decoding：固定线程池；
- timerTrigger：单线程；
- scheduled：2 线程；
- mainThread：主线程 Handler。

### 8.2 尚未统一的线程

以下模块仍直接创建线程或私有 cached pool：

- FT8 解码；
- 麦克风采集；
- 部分日志上传和导入；
- ICOM/Flex UDP 与 TCP；
- 蓝牙串口；
- 多种 Rig 轮询 Timer。

因此“全局线程池已经完全收敛”不是当前事实。更准确的说法是：项目已经有统一执行器基础，但迁移尚未完成。

### 8.3 当前生命周期问题

- `FT8TransmitSignal.stop()` 会关闭共享 decoding executor；
- 音量 `observeForever` 没有在 stop 时移除；
- 多个 Rig Timer 在断开后没有统一取消；
- HTTP 日志服务随 ViewModel 常驻启动；
- `HamRecorder.isRunning` 不能反映底层 `AudioRecord` 的真实状态；
- 部分可变 Runnable 被重复提交，任务之间可能读到后来覆盖的数据；
- Radio TCP EOF 需要立即结束，不能形成忙循环。

## 9. 外部依赖与平台

### 9.1 平台

- Android `minSdk 23`；
- Android `targetSdk 34`、`compileSdk 34`；
- Java 8 源码兼容；
- Gradle 9.4.1；
- Android Gradle Plugin 9.2.1。

### 9.2 主要依赖

- AndroidX AppCompat、Lifecycle、Navigation；
- Material、ConstraintLayout；
- Google Maps；
- Guava；
- MPAndroidChart；
- Apache Commons Net；
- NanoHTTPD；
- osmdroid；
- CMake 从仓库内 C/C++ 源码构建 `libft8cn.so`；发布 artifact 仍须逐 ABI 验证四个 ABI 的 ELF 与 JNI 契约。

### 9.3 维护边界

- 当前集成候选已纳入重建的 C/C++ native 源码和 `externalNativeBuild`；旧 `app/libs` 预编译库不再是本候选的 native 来源；
- 16KB native 自动化门禁已通过：四 ABI 的 ELF `PT_LOAD` 对齐为 `0x4000`，JNI contract 为 31 个 required export + 2 个 optional export；
- 16KB AVD 启动与 connected 证据已通过，但 4KB AVD、真机/HIL、长时功耗和温升仍待验证；
- 部分第三方库以本地 jar/aar 方式维护，升级和依赖审计成本较高。

## 10. 近期变化

### 10.1 代码正向变化

- `0317463`：引入 `AppExecutors`、计划时隙调度、数据库异步化、音频缓冲复用和瀑布图调整；方向总体正确，但同时引入了配置完成事件、共享执行器所有权等回归；
- `b9b8937`：增加 JTDX 优先级显示和一批 UI/稳定性调整；优先级异步计算与自动候选之间仍有确认缺陷；
- `ca71060`：增加网络音频断流窗口强制结算、恢复呼号最长前缀查询、保护瀑布图位图，并把 ICOM/协谷 UDP 忙等改为 20ms 节拍 sleep；
- `e4d5e76`：合入 FT-710 支持分支；现场 A/B 支持的 USB CAT 只写核心应保留，但无响应轮询生命周期仍需修复；
- `8493b71`：新增 Wavelog 日志同步和 Endpoint 兼容路径；station ID 判断仍有确认缺陷；
- `c48b5be`：加强新版 Android 广播接收器注册和发布 workflow。
- `9fbda6f`：将重建 native 候选提升为当前生产构建来源，并接入严格 oracle、四 ABI/16KB native artifact gate；该提交不替代 4KB AVD、真机/HIL 或功耗验收。

以上是提交内容映射，不代表每项都已经在当前基线完成自动化、真机或 HIL 验证。

### 10.2 同期暴露的确认缺陷

- 配置异步化改变了完成回调语义；
- 共享 executor 的所有权错误；
- `observeForever` 生命周期未闭合；
- 空解码和异常解码不能保证状态复位；
- `afterDecode` 在呼号优先级异步完成前调用 `findIncludedCallsigns`，使 `NEW_DXCC`、`NEW_BAND`、`RARE_DX` 首次不能进入自动候选；证据：`MainViewModel.java:330-368,542-550`、`CallsignDatabase.java:99-122,149-200`；
- Wavelog station ID 使用字符串包含判断，目标 ID `1` 可被响应中的 `123` 假匹配；证据：`ThirdPartyService.java:256-268`；
- FT-710 禁用了 USB read loop，却继续复用 `YaesuDX10Rig` 每 2 秒发送 FA/RM 查询，且 Timer 没有 cancel；证据：`CableSerialPort.java:125-128,241-245`、`YaesuDX10Rig.java:27-46,175-190`；
- Release notes 路径、签名 fail-fast、CI JDK 和版本规则不一致；
- 当前 Release APK 实际使用 Android Debug 证书。

### 10.3 较高概率风险

- 解码任务重叠；
- 可变 Runnable 复用导致任务串扰；
- TCP EOF 忙循环；
- 录音状态失真和主线程 sleep；
- Rig Timer、Handler 和网络池在重连后残留；
- XieGu 空闲 50pps 和停止后不可重启；
- 网络请求无统一总超时和上传队列无界；
- 数据库 N+1 查询和索引不足；
- SWL 去重结构存在空实现；
- Fragment view binding 生命周期不完整。

## 11. 当前验证事实

### 11.1 当前集成与独立验证事实

| 项目 | 结果 | 能证明什么 | 不能证明什么 |
|---|---|---|---|
| Git 基线 | `codex/v0.93.005-16kb-integration@9fbda6f`，由远端 `786ceed4` fast-forward；文档提交后形成新的本地 HEAD | 集成来源、开发 SHA 和提交链明确 | 不代表已 push、tag 或 Release |
| native 自动化 | 16KB native `GO`，P0 `0`；strict oracle 通过；四 ABI `PT_LOAD=0x4000`；31 required + 2 optional JNI export | 当前 native 构建与 ABI/JNI/ELF 契约满足独立门禁 | 不证明真机、4KB AVD 或长时性能 |
| 16KB AVD | API 37 / `PAGE_SIZE=16384`，fatal compatibility mode 关闭后启动成功；connected 63 pass / 1 intentional skip；AVD 已关闭 | 16KB 模拟器启动和测试证据 | 不证明 4KB AVD、真实 Android 设备或真实电台 |
| 4KB AVD | 待验证 | 保留兼容性空缺为显式门禁 | 不能把 16KB 结果外推到 4KB |
| 真机/HIL/性能 | 未执行 CAT/PTT/TX、完整 QSO、长时挂机、功耗或温升 profile | 发布边界保持诚实 | 不证明真实使用稳定性、功耗或温升 |
| formal Release | `NO-GO`；本地缺受保护 formal keystore、可信证书和正式批准 | 正式发布仍由 workflow fail-fast 保护 | beta CI 签名成功也不等于 formal 授权 |

### 11.2 历史已有证据

- 呼号前缀 Instrumented 测试曾在模拟器执行 5/5；
- 崩溃日志曾用模拟器强制崩溃验证；
- debug 测试包和正式包曾验证可并存；
- FT-710 串口只写路径有现场 A/B 记录。

历史证据必须保留时间、版本和设备边界，不能自动继承为当前 `release@d8f8c5d` 的全产品证明。

### 11.3 发版前待验证

- WiFi 网络模式连接、接收、解码和完整 QSO；
- USB、蓝牙和代表性 CAT 电台完整流程；
- 连续 2 小时挂机；
- 解码任务始终不重叠；
- 连接切换和重连 100 次；
- 生命周期进入退出 100 次；
- TCP EOF 在 200ms 内结束且断开回调一次；
- 包任务 10 万次无串扰；
- 关键场景 30 分钟相对功耗和温升对比；
- Release 固定正式签名和覆盖升级。

## 12. 当前成熟度

### 功能完整度

高。接收、解码、呼叫、发射、日志、同步、统计和多电台连接均已有实现。

### 稳定性

中等。已有多轮修复，但主链仍存在确认缺陷，长时真机门禁尚未完成。

### 性能与省电

中等偏低。忙等和高频轮询已有改善，但缺少基线测量，且常亮屏、残留 Timer、线程池和整图复制仍有明显优化空间。

### 自动化

中等。当前 native oracle、JNI/ELF/16KB artifact gate 和独立 AVD 证据已建立，但 4KB AVD、真机/HIL 和完整 FT8 主链仍未闭环。

### 可维护性

中等偏低。模块目录清楚，但 `MainViewModel` 和 `GeneralVariables` 职责过大，线程所有权、全局状态和生命周期边界不清。

### 发布能力

formal 当前仍不合格：本地缺受保护 formal 签名材料和批准；beta.9 workflow 只准备 GitHub Actions 受保护签名路径，且必须先满足 canonical integration 与 tag peeled commit 的精确 HEAD 绑定，等 CI 实际签名成功后才能发布 beta 资产。beta.8 失败 tag 保持不可变。

## 13. 产品总监与技术总监总结

FT8CN 的产品价值已经成立：它不是单一工具，而是一套移动 FT8 工作站。用户真正关心的不是再增加一个设置项，而是长时间挂机不中断、空周期不假死、重连后不发热、日志不丢、升级包能正常覆盖安装。

近期优化方向大体正确，但“代码已经改了”和“产品已经验证稳定”之间仍有明显距离。下一阶段不应继续扩大功能面，应先完成以下闭环：

1. 修复 P0 生命周期、并发、配置加载、解码和发布问题；
2. 建立可重复的自动化、真机和性能基线；
3. 用 2 小时挂机、完整 QSO、连接重连和固定正式签名作为稳定版资格；
4. 在主链稳定后，再小步拆分 `MainViewModel` 和 `GeneralVariables`；
5. 所有对外说明明确区分：代码正向变化、自动验证、模拟器验证、真机验证和 HIL。

具体执行顺序和可派发任务见 [ROADMAP_TODO.md](ROADMAP_TODO.md)。
