# FT-710 支持问题跟踪

## 背景

- 目标机型：Yaesu FT-710
- 连接方式：Android 手机 Type-C OTG -> USB 转接 -> FT-710 USB
- 目标工作模式：`DATA-U`
- CAT 波特率：`38400`
- 电台侧关键菜单：`Data Source=USB`

## 当前结论

- `PTT` 控制链路已经基本可用，应用能够把电台拉入发射。
- 蓝牙路由抖动、录音恢复失败、发射后解码失效等外围问题已有针对性修正。
- 目前核心未解问题仍然是：
  - 在 `DATA-U` 下多数情况下 `ALC=0`、`PO=0`
  - 电台有时会出现短脉冲或噪声脉冲，但没有持续功率输出

## 关键观察

### 1. 最重要线索

- 曾经出现过一次“程序错误切到 `RTTY-U`”的情况。
- 用户手动把模式从 `RTTY-U` 改回 `DATA-U` 后，电台出现过可听输出。
- 这说明问题很可能不只是 Android 音频格式，而与 **FT-710 的模式切换状态** 或 **`DATA-U` 进入方式** 强相关。

### 2. 现象变化

- 早期现象：起发瞬间出现一次类似噪声的脉冲，然后完全无功率。
- 中间改动后：变成两个脉冲，但仍然没有持续功率。
- 这类变化说明 Android 侧播放链路并非完全不工作，但并没有解决根因。

### 3. 当前判断

- `DATA-U` 下无持续功率，更像是：
  - 模式切换命令与 FT-710 的实际状态不完全匹配
  - 电台进入 `DATA-U` 后，数据音频入口状态没有被正确初始化
  - 或需要特定的过渡切换流程，不能只直接发送 `MD0C`

## 已落地改动

### FT-710 独立分支化

- 新增独立指令集：`InstructionSet.YAESU_FT710 = 23`
- 新增独立机型类：`YaesuFT710Rig`
- 在 `rigaddress.txt` 中加入 `YAESU FT-710`
- 配置页中将 FT-710 排在 FTDX10 前面
- 选择 FT-710 时默认控制方式切为 `CAT`

### 发射前后稳定性

- 发射期间暂停录音，发射结束后延时恢复
- 避免发射后 `AudioRecord startRecording called on an uninitialized AudioRecord`
- 限制蓝牙状态广播只在蓝牙连接模式下干预音频路由
- 降低蓝牙连接/断开反复弹消息的问题

### 调试能力

- 在配置页新增 `Debug` 开关
- 调试信息持久化到配置项 `debugMode`
- FT-710 相关关键日志接入应用内 Debug 面板：
  - 模式切换
  - PTT 开关
  - 录音暂停/恢复
  - 音频路由
  - 发射音频生成与播放完成

### 当前仍在保留验证的逻辑

- `YaesuFT710Rig.setUsbModeToRig()` 目前采用：
  - 先切 `RTTY-U`
  - 短延时后再切回 `DATA-U`
- 这是依据“手动从 `RTTY-U` 切回 `DATA-U` 后曾出现输出”这条关键线索保留的待验证逻辑。

## 已验证无效的尝试

以下尝试已经验证“不解决 `DATA-U` 下 0 功率”问题，现已标记为无效；其中纯音频兼容路径已从代码中回收。

### [无效] 猜测型 FT-710 菜单 EX 命令

- 曾尝试加入类似 `EX0104141;` 的 FT-710 菜单设置命令。
- 用户验证无效，且命令本身缺乏可靠手册依据。
- 结论：不保留。

### [无效] FT-710 专属音频格式强制兼容

- 曾尝试过：
  - `48kHz / stereo / stream`
  - `44.1kHz / mono / 16bit / stream`
  - `44.1kHz / mono / 16bit / static preload`
  - 满音量输出
- 用户验证后结论：`0` 功率问题没有本质变化。
- 结论：这部分不能作为主方向，已从 `FT8TransmitSignal` 的 FT-710 专属分支中回收。

### [无效] 仅直接 CAT 切到 `DATA-U`

- 仅使用 `MD0C` 直接切 `DATA-U`，长期表现为：
  - PTT 能拉起
  - 但 `ALC=0`、`PO=0`
- 结论：直接切 `DATA-U` 不足以解决问题。

## 当前保留的工作假设

### 假设 A：模式切换序列问题

- FT-710 对 `DATA-U` 的进入流程可能有额外状态要求。
- “`RTTY-U -> DATA-U` 后手动恢复有输出”的线索，优先支持这个方向。

### 假设 B：电台 `DATA-U` 音频入口状态问题

- 即使前面板显示为 `DATA-U`，也可能还有内部数据调制入口没有被 CAT 正确带起。
- 需要结合更多调试信息继续确认。

## 后续建议

### 短期

- 使用最新带 Debug 开关的 APK 继续复现。
- 抓取应用内 Debug 面板内容，重点看：
  - `mode switch request / settled`
  - `PTT ON / PTT OFF`
  - `playFT8Signal`
  - `generated samples`
  - `route Before TX`
  - `finishPlaybackOnce`

### 中期

- 若 `RTTY-U -> DATA-U` 仍不能恢复功率，需要继续核对：
  - FT-710 CAT 手册中模式查询/返回值
  - `DATA-U` 相关菜单项是否存在可读写 CAT 命令
  - 电台端 `DATA MOD SELECT` / `USB AUDIO` / `DATA PTT SELECT` 是否有额外状态

## 当前状态

- 状态：`进行中`
- 优先级：`高`
- 根问题：`FT-710 在 DATA-U 下仍无持续发射功率`

## 最新发现 2026-04-15

- 禁用 FT-710 的自动模式切换：无改善。
- 在发射（TX）前释放 `AudioRecord`：无改善。
- 采用 `48kHz / 立体声 / 16位 / MODE_STREAM` 测试 FT-710 发射链路：仍然没有射频（RF）输出。

### 确凿证据

- 调试日志显示：
  - `partial write expected=1213456, actual=0, trackMode=1`
- 这意味着当前直接的失败点是：
  - 流模式下的 `AudioTrack.write(...)` 返回 `0`
  - 发射音频未能真正进入播放管道

### 播放器对比

- 用户验证：
  - 在 FT8CN 连接前，`DATA-U 模式 + 手动 PTT + 音乐播放器` 可以使 FT-710 输出声音。
  - 在 FT8CN 连接电台后，音乐播放器的输出消失。
  - 仅杀死 FT8CN 不足以立即恢复播放。
  - 重启音乐播放器 App 可恢复声音。
- 这强烈表明 FT8CN 在连接到 FT-710 复合 USB 设备后，干扰了 Android USB 音频播放会话。

### 当前优先级

- 调查为什么 `AudioTrack.write(...)` 在 FT-710 的 stream 路径中返回 `0`。
- 调查连接到 FT-710 复合 USB 设备是否会扰乱现有的 Android USB 音频会话。
- 将上述内容作为下一次调试的首要工作假设。

## 串口链路假设更新 2026-04-15

- 现场表现出的“FT8CN 连接 CAT 后，外部音乐播放器立即失去声音”的现象，已被视作关键线索，而非常规副作用。
- 这更强烈地指向 FT8CN 干扰了 FT-710 复合 USB 设备会话，而不仅是生成了错误的 FT8 音频。
- 本轮的新增代码修改：
  - `CableSerialPort` 现倾向于精确的 `deviceId/productId` 匹配，而非仅匹配 `vendorId`。
  - `CableSerialPort` 现记录连接时选定的 USB 设备和所有接口类。
  - `CableSerialPort` 现记录串口开启前后的音频路由快照。
  - `CdcAcmSerialDriver` 现尝试首先使用 `claimInterface(..., false)`，仅在需要时回退到强制 claim。
- 本轮目的：
  - 减少 FT8CN 在打开 CAT 时强制干扰 FT-710 USB 音频的可能性。
  - 收集关于串口开启本身是否会改变 Android USB 音频路由的证据。

## 额外日志线索 2026-04-16

- 在之前捕获的调试日志中，至少有一次发射尝试是正常启动，然后在大约 1 秒后中断。
- 截图中的具体时间：
  - `15:46:15` 发射开始 / `playFT8Signal`
  - `15:46:16` `finishPlaybackOnce` / `afterPlayAudio release track` / `PTT OFF`
- 这远短于标准的 15 秒 FT8发射窗口，应作为一个关键线索。
- 解释：
  - 至少有一种失败模式是“发射播放链路提前结束”，而不只是“音频一直在播放但射频一直为 0”。
  - 这强烈支持去调查播放提前完成、提前 `PTT OFF`，或提前释放 USB 音频会话的原因。

## USB 共存突破 2026-04-16

- 用户在一个测试版本中进行了验证，该版本中 FT-710 CAT 串口仍然打开，但未启动串口后台读循环。
- 在该版本中：
  - FT8CN 连接后，Android 音乐播放器不再被破坏。
  - 能够同时听到 FT8 发射音频和音乐播放器的音频。
  - 这是首个明显改善 USB 音频共存状态的修改。
- 解释：
  - 主要干扰源极可能是 FT-710 CAT 串口的后台读取循环。
  - 仅用 `AudioTrack` 无法完全解释播放中断的原因。
  - 仅用 `AudioRecord` 也无法完全解释播放中断的原因。
- 下一步正式版本的方向：
  - FT-710 应当使用专用的 CAT 分支。
  - 针对 FT-710 禁用 USB CAT 的串口后台读循环。
  - 针对 FT-710 禁用继承自 DX10 的后台 CAT 轮询。

## 当前思路快照 2026-04-16

- USB 共存问题与 `0` 射频输出问题目前应分为两个层次对待：
  - 第一层：FT8CN 连接 CAT 后不能主动破坏 Android 音乐播放器或 USB 音频会话。
  - 第二层：在第一层稳定后，调查为何 FT-710 在 `DATA-U` 模式下仍然没有射频输出。
- 当前证据显示，在禁用 FT-710 CAT 读取循环后，第一层已经有了实质性的改善。
- 如果新版本在外部音频播放保持健康的同时仍然表现为 `0` 射频，下一个怀疑点将不再是 Android 播放被毁，而是电台端的 `DATA-U` 发射条件。

## 产品边界说明 2026-04-16

- 打开 FT8CN 不应该主动暂停或破坏已有的外部音乐播放器。
- 连接 FT8CN 到 FT-710 CAT 也不应该使播放器处于需要重启 App 才能恢复的损坏状态。
- 未来采用以下策略是可接受的：
  - 仅在实际发射期间请求音频焦点。
  - 仅在实际发射期间主动暂停或降低外部媒体的音量。
- 未来的媒体焦点策略是一个产品设计选择，并非当前 FT-710 故障的根本原因。

## 下一步工作项

- 针对以下内容增加更清晰的 FT-710 发射追踪：
  - `PTT ON/OFF`
  - 发射播放的起止时间
  - 发射前后的路由快照
  - FT-710 CAT 只写模式状态
- 创建极简的 FT-710 发射路径：
  - 无模式切换
  - 无 CAT 读取
  - 无仪表盘轮询
  - 仅执行 `PTT ON -> 播放音频 -> PTT OFF`
- 一旦极简发射路径验证稳定，继续调查 FT-710 `DATA-U` 的附带条件：
  - `DATA MOD SELECT`
  - `USB AUDIO`
  - `DATA PTT SELECT`
  - 与 `RTTY-U` 不同的任何电台端前提条件

## 落地进展更新 2026-04-16（当天）

- 在实际发射路径中增加了更清晰的 FT-710 发射生命周期追踪：
  - 发射生命周期开始
  - 播放开始
  - 播放结束
  - 清理完成
- 在以下节点增加了 FT-710 的边界日志：
  - `before-transmit-entry`（发射前入口）
  - `After PTT ON`（PTT 开启后）
  - `after-transmit-entry`（发射后入口）
  - `After PTT OFF`（PTT 关闭后）
- 在发射播放前后增加了额外的 FT-710 路由快照：
  - `FT710 TX stream started`（FT710 发射流已启动）
  - `FT710 before PTT tail hold`（FT710 在 PTT 尾部保持前）
  - `FT710 after TX cleanup`（FT710 在发射清理后）
- 在音频播放结束后、`PTT OFF` 之前，增加了一个微小的 FT-710 专属 PTT 尾部保持时间：
  - 当前设定值：`250ms`
  - 目的：降低 FT8 信号末尾被过早切掉的概率。

## 下一次现场测试的关注点

- 连接 CAT 后，FT-710 是否仍能保持外部播放器处于健康状态。
- 当 FT8 播放明显开始时，`DATA-U` 是否仍然为 `0` 射频输出。
- 新日志是否显示：
  - 发射播放时长正常
  - `After PTT ON` 路由仍然在 USB 头戴式耳机上
  - `After PTT OFF` 路由保持稳定
  - 导致 `DATA-U` 拒绝调制的任何明确过渡节点。

## 源码清理说明

- 发现部分源文件包含由于早期提交引入的乱码中文注释。
- 已知实例：
  - `ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java`
  - 出现可见的乱码文本，如 `?????????`。
- 这不是当前 FT-710 故障的根源，但后续应从 git 历史记录中修复，以准确还原原始的中文注释。

## 日志解读 2026-04-16（最新测试）

- 当前截图显示 Android 端的发射路径健康得多：
  - 在串口打开前、打开后以及发射开始时，USB 路由均维持在 `USB_HEADSET`。
  - 音频轨道绑定汇报为 `bind=true`。
  - FT8 音频流的写入几乎完全结束：
    - `writtenFrames=606728`
    - `actualDurationMs=12640`
    - `elapsedMs=12002`
    - `remainingMs=638`
- 解释：
  - Android 音频路由不再是首要故障点。
  - FT8CN 现在能够成功将完整的发射音频流推向 USB 音频路径。
  - 剩下的 `0` 射频问题越来越有可能是 FT-710 端的 `DATA-U` 接收问题。

## 最新测试后的针对性修改

- 在真实的 FT8 信号音前后，增加了 FT-710 专属的 USB 音频静音填充：
  - 前置静音填充：`250ms`
  - 后置静音填充：`250ms`
- 目的：
  - 让 FT-710 在 FT8 音频开始前看到一个稳定的 USB 音频流。
  - 降低由于音频流开始过于突兀而导致 `DATA-U` 忽略调制的概率。

## 确认解决 2026-04-16

- 用户确认 `DATA-U` 现已能产生射频功率输出。
- 重要细微差别：
  - 之前的版本在 `DATA-U` 中就已经产生了射频输出。
  - 新加入 USB 音频静音填充的版本同样能产生射频输出。
- Therefore, 目前证实的有效基线更有可能是：
  - FT-710 采用 CAT 只写模式。
  - 禁用串口后台读循环。
  - 禁用继承自 DX10 的后台 CAT 轮询。
  - 稳定本地 USB 音频播放路径。
- 新加入的 `250ms` 前置/后置静音填充本身尚未被证实是起到决定性作用的修复。

## 最新日志阅读（发射成功）

- 成功的日志依然显示：
  - `bind=true`
  - USB 路由保持在 `USB_HEADSET`。
  - 音频流写入几乎完全完成。
  - 由于包含了静音填充，整个发射持续时间略微延长。
- 这支持了以下结论：Android 端的发射链条目前已足够健康，能够使 FT-710 的 `DATA-U` 接收调制。

## 下一步推荐工作

- 将当前的 FT-710 工作分支冻结为新的已知良好基线。
- 稍后进行一次细致的 A/B 清理对比：
  - 保留 FT-710 极简 USB 发射架构。
  - 评估是否真的需要 USB 填充。
  - 仅移除确认没有必要的修改。
- 之后，将重点从“能否发射”转移到：
  - 连续发射/接收循环的稳定性。
  - 发射后的接收恢复情况。
  - 是否应当减少或将仅调试日志设为可选。

## 最新检查点 2026-04-17

### 安全提交基线

- 创建了安全检查点提交：
  - `73ae96f Stabilize FT-710 USB TX path and debug cleanup`
- 分支：
  - `Feature/FT710Support`
- 目的：
  - 在修改 CQ 状态机行为前，保留当前可正常工作的 FT-710 USB 发射路径。

### 更新后的当前结论

- 认为在 USB/CAT/音频边界层面上，FT-710 `DATA-U` 的发射问题已基本修复。
- 最可信的有效修复链条为：
  - FT-710 专属电台分支。
  - FT-710 CAT 只写模式。
  - 禁用 USB 串口后台读循环。
  - 禁用继承自 DX10 的轮询行为。
  - 稳定本地 USB 音频播放路径。
  - 发射前后暂停/恢复录音。
- 剩余的担忧已从“无射频输出”转移到“QSO 完成后或 CQ 激活行为可能不符合预期”。

### 重要的时间概念区分

- FT-710 的发射尾部保持（tail-hold）与用户可配的 `PTT 延迟`（PTT delay）并不等同。
- 当前的时间概念：
  - `transmitDelay`（发射延迟）：周期调度的偏移量。
  - `pttDelay`（PTT 延迟）：开启 PTT 后到音频播放前的等待时间。
  - FT-710 发射尾部保持：音频播放结束后到关闭 PTT 前的等待时间。
- 因此：
  - 调整设置页面的 `PTT 延迟` 不会改变 FT-710 发射尾部保持的行为。

### 需要评估的新行为风险

- 需要将一个新观察与非 FT-710 行为进行仔细对比：
  - 在发射路径稳定后，应用可能会保持在已激活的 `CQ` 模式并持续发射 `CQ`。
  - 用户截图特征：
    - 目标呼号为 `CQ`
    - 功能序列为 `6`
    - 发射内容为 `CQ <呼号> <网格>`
- 当前解读：
  - 这尚未被证实是 FT-710 专属的 Bug。
  - 这可能是从通用发射状态机中继承下来的共享行为。
  - 核心逻辑位于 `FT8TransmitSignal.parseMessageToFunction(...)` 和 `resetToCQ()`。

### 下一阶段的决策

- 不要盲目修改 FT-710 分支中的 CQ 自动重置逻辑。
- 首先将当前的 CQ/状态机行为与非 FT-710 电台进行对比。
- 仅在对比后，决定是：
  - 保留当前行为。
  - 当状态机自动回到 `CQ` 时清除激活状态。
  - 将手动的 `resetToCQ()` 行为与状态机自动的 `resetToCQ()` 行为区分开。

### 紧接工作项

- 将 `73ae96f` 视作新的回滚安全基线。
- 在对通用发射逻辑做任何功能性改动前，先审视共享的 CQ 行为。
- 在评估 CQ/状态机行为的同时，保持 FT-710 USB 发射路径冻结。

## 当前修复思路 2026-04-17

### 对现有修改重新归类

- 在对比工作分支与 `main` 分支后，FT-710 的改动现应划分为三个部分：
  - 极有可能是真正的修复。
  - 易用性/稳定性改进。
  - 不确定或可选的修改。
- 这一点很重要，因为该分支已经包含了许多尝试性改动，后续的清理应首先保护好真正的修复路径。

### 极有可能是真正的修复

- 增加专用的 FT-710 电台分支：
  - `InstructionSet.YAESU_FT710`
  - `YaesuFT710Rig`
  - `rigaddress.txt` 中的 FT-710 条目
- 阻止 FT-710 继承 DX10 的后台 CAT 轮询行为。
- 停止 FT-710 USB CAT 后台读循环，让 CAT 保持在只写路径上。
- 停止在 FT-710 USB 发射准备期间强行重写电台模式。
- 这些修改最契合观察到的转折点：
  - 连接 CAT 后，FT8CN 不再破坏外部音乐播放器。
  - `DATA-U` 发射能够产生射频功率。

### 易用性/稳定性改进

- 发射前后暂停和恢复录音。
- `MicRecorder` 重新初始化健壮性提升。
- 更丰富的路由/发射生命周期调试日志。
- 调试模式开关。
- 配置中 FT-710 默认选择 `CAT`。
- 发射/接收按钮状态刷新工作。
- 这些都是有用的改动，应当予以保留，但它们目前看起来不像起决定性作用的根本修复。

### 不确定/可选的修改

- FT-710 USB 前置/后置静音填充。
- FT-710 尾部保持时间。
- `AudioRouteHelper.bindTrackToPreferredOutput(...)`。
- 精确的 USB `deviceId/productId` 匹配。
- `CdcAcmSerialDriver` 中更安全的 `claimInterface` 回退机制。
- `FT8TransmitSignal` 中针对 FT-710 本地播放参数的微调。
- 这些改动应当被视作 A/B 候选方案，而非假定的必然需求。

### 下一步工作原则

- 冻结当前已验证正常的 FT-710 极简路径。
- 宁做减法，不做加法。
- 每次仅改动一个不确定的变量。
- 保持对 CQ 行为的独立评审，除非证实其与 FT-710 有直接耦合。

## 减法验证清单 2026-04-17

### 验证快照 2026-04-19

- 回滚后已验证正常的项目：
  - `TX_AUDIO_FOCUS_SETTLE_MS = 0`
  - `FT710_USB_AUDIO_PREROLL_MS = 0`
  - `FT710_USB_AUDIO_POSTROLL_MS = 0`
  - `FT710_TX_TAIL_HOLD_MS = 0`
  - 无 `bindTrackToPreferredOutput(...)` 的 FT-710 发射路径。
  - 回滚 `CableSerialPort` 中精确的 `deviceId/productId` 匹配。
  - 回滚 `CdcAcmSerialDriver` 中非强制优先的 `claimInterfaceSafely(...)` 策略。
  - 回滚 FT-710 本地播放参数至接近 `main` 的行为：
    - 采样率遵循 `GeneralVariables.audioSampleRate`。
    - 输出位深遵循 `GeneralVariables.audioOutput32Bit`。
    - `trackMode = MODE_STATIC`。
    - 单声道 `float2Short(...)` 路径。
- 当前结果的含义：
  - FT-710 的修复目前看来不依赖新增的延迟补偿。
  - FT-710 的修复目前看来也不依赖显式的首选输出绑定。
  - FT-710 的修复目前看来也不依赖精确的 `deviceId/productId` 匹配。
  - FT-710 的修复目前看来也不依赖 CDC ACM 接口的非强制优先 claim 策略。
  - FT-710 的修复目前看来也不依赖之前新增的针对 FT-710 的本地播放参数组合。
- 减法后剩下的高置信度核心路径：
  - 专用的 FT-710 电台分支。
  - 不继承 DX10 的后台轮询。
  - FT-710 CAT 只写 / 禁用串口读循环。
  - 保留电台当前模式，不强制重写。
- 若继续做减法，下一步候选风险极高：
  - 因为只剩下了高置信度的核心路径。

### 现场交接快照

- 最后一个确认正常的测试结果：
  - 回滚 FT-710 本地播放参数至接近 `main` 分支的行为。
- 已经在现场测试中验证过的当前 APK：
  - `app-debug.apk`
  - 构建时间戳：`2026-04-19 22:19:46`
- 处于验证中的当前代码状态：
  - `FT710_TX_TAIL_HOLD_MS = 0`
  - `TX_AUDIO_FOCUS_SETTLE_MS = 0`
  - `FT710_USB_AUDIO_PREROLL_MS = 0`
  - `FT710_USB_AUDIO_POSTROLL_MS = 0`
  - 采样率遵循 `GeneralVariables.audioSampleRate`。
  - 输出位深遵循 `GeneralVariables.audioOutput32Bit`。
  - `trackMode = MODE_STATIC`。
  - FT-710 本地 PCM 路径由立体声复制改回单声道 `float2Short(...)`。
  - FT-710 路径依然跳过 `bindTrackToPreferredOutput(...)`。
  - `CableSerialPort` 目前使用宽松的 vendor 匹配。
  - `CdcAcmSerialDriver` 目前处于强制 `claimInterface(..., true)`。
- 下一步预期：
  - 决定是否停止做减法，并开始进行收尾与整合。

### 优先级最高的 A/B 回滚项

- `TX_AUDIO_FOCUS_SETTLE_MS`
  - 代码位置：
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L47)
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L605)
  - 理由：与 `main` 相比，这是新引入的延迟，应当在其他 FT-710 特有延迟补偿之前完成验证。

- FT-710 USB 前置静音填充
  - 代码位置：
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L48)
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L471)
  - 理由：在 FT-710 特有的时间改动中，这一项最有可能将 FT8 的实际起发时刻推迟到 15 秒时隙内较晚的位置。

- FT-710 USB 后置静音填充
  - 代码位置：
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L49)
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L472)
  - 理由：这延长了发射时间占用的时段，缩减了切回接收的余量，但对起发时刻的影响风险小于前置静音。

- FT-710 发射尾部保持
  - 代码位置：
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L46)
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ft8transmit/FT8TransmitSignal.java#L133)
  - 理由：仍值得做 A/B 回滚，但其发生在音频播放结束之后，对时隙规范的干扰概率小于前置静音填充。

- `AudioRouteHelper.bindTrackToPreferredOutput(...)`
  - 代码位置：
    - [AudioRouteHelper.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/audio/AudioRouteHelper.java#L41)
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/ft8transmit/FT8TransmitSignal.java#L769)
  - 理由：是有用的路由加固手段，但核心的修复转折点仍然是在串口侧。

### 第二梯队 A/B 回滚项

- 精确的 `deviceId/productId` 匹配
  - 代码位置：
    - [CableSerialPort.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/connector/CableSerialPort.java#L56)
    - [CableSerialPort.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/connector/CableSerialPort.java#L107)
  - 理由：高健壮性逻辑，但目前尚未证明是起到决定性作用的修复。

- 更安全的 `claimInterface` 回退
  - 代码位置：
    - [CdcAcmSerialDriver.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/serialport/CdcAcmSerialDriver.java#L108)
  - 理由：可能对复合 USB 共存有影响，但应当放在风险较低的项之后进行验证。

### 较后的候选

- FT-710 本地播放参数微调
  - 代码位置：
    - [FT8TransmitSignal.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/ft8transmit/FT8TransmitSignal.java)
  - 理由：历史证据表明纯音频格式层面的修改大部分是无效的。

### 绝不要首先回滚的项

- 专用的 FT-710 电台分支
  - [YaesuFT710Rig.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/rigs/YaesuFT710Rig.java)
- 阻止继承 DX10 的后台轮询
  - [YaesuDX10Rig.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/rigs/YaesuDX10Rig.java#L29)
  - [YaesuFT710Rig.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/rigs/YaesuFT710Rig.java#L14)
- FT-710 CAT 只写 / 禁用串口读循环
  - [CableSerialPort.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/connector/CableSerialPort.java#L161)
  - [CableSerialPort.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/connector/CableSerialPort.java#L286)
- 保留电台当前模式，不强制重写
  - [YaesuFT710Rig.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/rigs/YaesuFT710Rig.java#L19)
- 这些目前是 FT-710 修复链路中置信度最高的核心改动候选。

### 在当前 A/B 工作结束时延后的事项

- 重新审视用户关于最近的版本在时序上仍然“感觉有点不对劲”的观察。
- 稍后评审的范围：
  - 将主观的发射/接收时序体感与 `main` 分支进行对比。
  - 独立验证对时/时间差（UTC 偏移量）行为是否确实与 `main` 分支存在差异。
- 目前的临时研判：
  - `UtcTimer.syncTime(...)` 相比 `main` 并没有发生变化。
  - 发射执行路径与 `main` 并非完全相同。
- 优先级：
  - 放在当前待办事项的末尾。
  - 不要为此中断当前正处于收敛中的 A/B 回滚。

## 极简核心修复 A/B 计划 2026-04-19

### 目标

- 经过前期的减法验证，大部分针对 FT-710 时序/本地播放的实验性改动已被证实可以移除。
- 下一步是对剩下可能的核心修复逐一验证，而不是加入更多的新逻辑。

### 剩下的核心候选

- 专用的 `YaesuFT710Rig`。
- 阻止继承 DX10 的后台轮询。
- FT-710 CAT 只写 / 禁用串口读循环。
- 保留电台当前模式，不强制重写。

### 统一的现场验证清单

- CAT 连接不得破坏外部音乐播放器。
- FT8 发射时 `DATA-U` 模式必须能正常产生射频功率输出。
- 不得出现异常切换至 `RTTY-U` 的现象。
- 头戴式耳机图标不得丢失 / USB 音频不得崩溃。
- 发射后必须能正常返回接收状态。

### 计划中的顺序

- Round A：
  - 仅恢复 `CableSerialPort` 中 FT-710 的串口读循环。
- Round B：
  - 如有必要，恢复 FT-710 的后台轮询。
- Round C：
  - 如有必要，恢复 FT-710 的 USB 模式重写行为。
- Round D：
  - 仅作为最后手段，与类似 DX10 的电台路径进行对比。

### Round A 状态

- 已完成。
- 单变量控制：
  - 仅限 `CableSerialPort`。
- 具体回滚：
  - 让 `usbIoManager.start()` 对 FT-710 重新生效。
- Round A 期间冻结的项目：
  - 专用的 FT-710 电台分支。
  - 阻止继承 DX10 轮询。
  - 保留当前模式行为。

### Round A 结果

- 用户验证：
  - 问题重新复现。
  - 发射开始后，依旧无声音 / 无射频功率输出。
- 当前解读：
  - 重新启用 FT-710 串口读循环已经被证实是高置信度的回归诱因。
  - 这强烈支持将“FT-710 CAT 只写 / 禁用串口读循环”视作核心修复，而非可选的权宜之计。
- 紧接行动：
  - 在继续进行下一项核心路径对比前，恢复禁用读循环的基线配置。

### Round B 状态

- 已完成。
- 单变量控制：
  - 仅限 `YaesuFT710Rig`。
- 具体回滚：
  - 恢复对 FT-710 类似 DX10 的后台 CAT 轮询。
- Round B 期间冻结的项目：
  - FT-710 串口读循环保持禁用。
  - 专用的 FT-710 电台分支保持。
  - 保留当前模式行为保持。

### Round B 结果

- 用户验证：
  - 射频功率输出正常。
  - 外部音乐播放器保持健康。
  - 头戴式耳机图标 / USB 音频状态维持稳定。
  - 未观察到串口断连 / 发射卡死 / 模式异常。
- 当前解读：
  - 仅重新启用对 FT-710 的后台轮询并不会立刻引发 `0` 功率症状。
  - 这使得“禁用后台轮询”看起来不像是单一的决定性修复。
- 阶段性结论：
  - “FT-710 CAT 只写 / 禁用串口读循环”依然是经过证实的最强核心修复候选。
  - “阻止继承 DX10 的后台轮询”目前看来更像是次要的保护性措施或可选偏好，而非硬性最低要求。

### Round C 状态

- 已完成。
- 单变量控制：
  - 仅限 `YaesuFT710Rig`。
- 具体回滚：
  - 恢复 FT-710 对类似 DX10 的 USB 模式重写行为。
- Round C 期间冻结的项目：
  - FT-710 串口读循环保持禁用.
  - 专用的 FT-710 电台分支保持。
  - 后台轮询像 Round B 那样保持启用。

### Round C 结果

- 用户验证：
  - 恢复模式重写行为后，操作维持正常。
  - 射频功率输出维持正常。
  - 未汇报额外的播放器 / USB 音频 / 串口稳定性问题。
  - 本轮中未汇报模式异常。
- 当前解读：
  - 仅重新启用 FT-710 USB 模式重写并不会引回问题。
  - 这使得“保留当前模式，不强制重写”看起来不像是硬性的最低要求。
- 更新后的结论：
  - 置信度最高的核心候选依然是“FT-710 CAT 只写 / 禁用串口读循环”。
  - 禁用后台轮询和保留当前模式目前看来均属于可选的保护机制，并非最低限度修复所必需。

### Round D 状态

- 已完成。
- 单变量控制：
  - 仅限 `MainViewModel`。
- 具体回滚：
  - FT-710 的机型选项依旧存在，但将底层的电台实例由 `YaesuFT710Rig` 切换为 `YaesuDX10Rig`。
- Round D 期间冻结的项目：
  - FT-710 串口读循环保持禁用。
  - 后台轮询保持启用。
  - 模式重写保持启用。

### Round D 结果

- 用户验证：
  - 所有检查项正常。
  - 射频功率输出维持正常。
  - 播放器 / USB 音频 / 串口稳定性正常。
  - 未观察到模式异常。
- 当前解读：
  - 移除专用的 `YaesuFT710Rig` 实例并没有引回问题。
  - 这强烈降低了“专用的 FT-710 电台分支”是最低限度修复必需项的置信度。
- 最终收敛：
  - 经过证实的最简核心修复方案仅为“FT-710 CAT 只写 / 禁用串口读循环”。
  - 电台类的独立拆分、禁用后台轮询以及保留当前模式等改动，现在看来均更符合“可选的隔离保护或安全偏好”，而非强制必需的修复手段。
- 最终整理方向：
  - 保留 FT-710 机型选项与独立指令集。
  - 但允许其电台控制行为复用 `YaesuDX10Rig`。
  - 将决定性的 FT-710 修复完全收紧于串口层（只写不读）。
