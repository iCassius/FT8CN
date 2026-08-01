# HANDOFF 会话交接日志

> 新记录插入顶部，保留历史。每条记录格式：做了什么 / 关键决策 / 当前状态 / 下一步。

---

## 2026-08-01　P0-E 发布门禁回归修复

### 做了什么

- 修正 `check_release_contract.py --history` 对 `git cat-file --batch` 的消费：commit、tree、tag 和 blob 都先完整读取 body，再只扫描文本 blob，避免首个 commit 造成游标错位、历史敏感内容漏扫。
- 为上述路径增加 commit/tree/tag 位于敏感 blob 前的回归测试；`python scripts/test_release_gates.py` 和模块化 unittest 两种入口均已验证。
- 删除顶层 Gradle 中未被引用的历史绝对 keystore 路径，正式签名继续只接受环境变量或未跟踪的 `keystore.properties`。

### 当前状态

- `python scripts/check_release_contract.py`、`--history` 和 5 项发布门禁测试通过；已用本机 Android SDK 重新完成 `:app:packageTestApk`。
- 本修复不创建正式 keystore、tag 或 GitHub Release。集成后仍需更新该版本 Release Notes 以覆盖实际运行时修复，并在配置正式签名 Secrets 后执行正式 APK 门禁。

---

## 2026-07-30　P0-E 第二轮独立发布审查：签名迁移与正式发布阻断

### 做了什么

- 保留已通过的 TEST/BETA APK：`ft8cn/.artifacts/FT8CN-v0.93.005-beta-d8f8c5d.apk`，命名、JDK 17 和 beta 包名不变。
- 增加 `FT8CN_RELEASE_CERT_SHA256` 可信证书指纹门禁；Gradle 与 `verify_apk_signature.py` 都要求正式 APK 精确匹配，错误指纹必须失败。
- 明确已公布 `v0.93.004` APK 使用 Android Debug 证书；不假设旧私钥可取回。正式发布默认要求用户批准一次性签名迁移、提供新长期 keystore 和证书指纹，否则 workflow fail-fast。
- 泛化远端同名 tag/既有 GitHub Release 不可覆盖检查；增加版本专用 `doc/release-notes/v0.93.005.md`，不再把完整 `doc/RELEASES.md` 直接作为 body。
- CI keystore 改写到固定 `${RUNNER_TEMP}/ft8cn-release-signing`，workflow 使用 `if: always()` 清理；Git 忽略规则递归覆盖 JKS、keystore、P12/PFX 和 APK。
- 敏感扫描覆盖跟踪文件、staged diff 和可选历史 blob 扫描，覆盖 PEM/JKS/PKCS12/Base64/credential 模式并跳过自身规则文本和 placeholder。

### 当前阻断与用户决策

- 当前只允许生成/分发 TEST/BETA，不创建正式 Release 或 tag。
- 若要发布 `v0.93.005`，用户必须明确接受一次性签名迁移的升级/卸载边界，并提供新的长期 keystore、`FT8CN_RELEASE_CERT_SHA256` 和 `FT8CN_FORMAL_RELEASE_APPROVED`；不能假装新证书可以覆盖 v0.93.004。
- 如果旧私钥不可恢复，发布前必须指导用户备份 ADIF/QSO 和可导出的配置；项目没有自动迁移应用私有数据的实现，卸载重装可能清除旧包数据。
- 仓库管理员还需设置 GitHub tag protection/ruleset，禁止 tag deletion、update/force-push，并限制 Release 权限。

### 下一步

1. 只运行 beta 构建、beta 正反证书测试和发布合同扫描；正式 release build 在无 keystore/批准时保持预期失败。
2. 只有用户完成上述决策后，才在配置 secrets 的环境运行正式构建和证书指纹比对。
3. 本分支不创建、不推送 tag，不 push。

## 2026-07-30　P0-E 发布、CI、签名、版本与可回退测试 APK

### 做了什么

- 基于干净的 `origin/release@d8f8c5d` 审计现有 Gradle、AGP 9.2.1、Gradle 9.4.1、workflow、tag 和签名配置。
- 将 `ft8cn/gradle.properties` 设为版本唯一来源：`0.93.005` / `93005`，对应未来 tag `v0.93.005`；不创建或推送 tag，不覆盖已有 `v0.93.004`。
- 普通 CI 和 tag workflow 统一切换 JDK 17；正式 Release 使用实际存在的 `doc/RELEASES.md`，同名 Release 或资产不覆盖。
- 增加正式签名 fail-fast、`verifyReleaseSigning`、版本/敏感信息校验脚本，以及 `:app:packageTestApk`；TEST/BETA 输出到被 Git 忽略的 `.artifacts/FT8CN-v0.93.005-beta-<短commit>.apk`。
- 发布文档补充正式 Secrets 变量名、证书检查、敏感信息检查、Android 同包 downgrade 和 BETA 共存边界。

### 关键决策

- `versionName` 采用仓库要求的三位构建号 `0.93.005`，`versionCode=93005`；`v0.93.004` 视为不可变历史发布。
- 普通 CI 不构建正式版，因为它不应持有正式签名 Secrets；它构建 debug 签名的 BETA 包。正式版只由 tag workflow 在 Secrets 和 keystore 均完整时构建。
- BETA 保持 `.beta` 包名、`-beta` 版本后缀和测试标签，不把 debug APK 重命名成 release。

### 当前状态

- 已完成构建配置、workflow、校验脚本和文档修改，未生成或提交 APK、keystore、tag，也未 push。
- 正式证书值和指纹不在本机 worktree；正式签名构建必须在配置 Secrets/keystore 的环境验证。
- `doc/PROJECT_OVERVIEW.md`、`doc/ROADMAP_TODO.md` 在本 worktree 的 `origin/release@d8f8c5d` 尚不存在；本轮依据现有 `PROJECT_RULES.md`、`doc/HANDOFF.md`、`doc/RELEASES.md`、`doc/RELEASE_SIGNING.md` 和实际 Gradle/workflow 审计，不伪造缺失文档内容。

### 下一步

1. 在有正式 Secrets 的 CI 或维护者本机运行 `:app:verifyReleaseSigning`、正式 APK 构建和证书指纹人工比对。
2. 由发布负责人在确认 `v0.93.005` 尚未存在后创建并推送 tag；本分支不代执行该动作。
3. 用模拟器或明确授权的测试设备分别安装 BETA/正式 APK；同包回退按 `RELEASE_SIGNING.md` 的卸载或 `adb -d install -r -d` 边界执行。

## 2026-06-12　测试版打包（.beta 并存安装）+ 两个全新安装闪退修复

### 1. 本次会话做了什么

用户要一个"不和正式版冲突"的测试包。实现 debug 构建独立包名 `com.bg7yoz.ft8cn.beta`、桌面名"FT8CN测试版"、版本号带 `-beta` 后缀，可与正式版并存安装、数据互不影响。模拟器实测两包并存安装/运行正常，instrumented 测试 5/5 通过。

**顺带发现并修复了两个全新安装必崩的 bug**（模拟器全新装 .beta 包时暴露，正式包此前已授权所以没暴露）：

1. **录音权限未授予时启动 microphone 前台服务闪退**：`MicRecorder.start()` 在用户还没点"允许录音"时就 `startForegroundService`，Android 14 直接 SecurityException；系统重启服务又在后台触发第二次崩溃。修复：start() 先查 RECORD_AUDIO 权限，未授权直接返回（MainActivity 授权回调里本来就会重启录音）；`AudioForegroundService.startForeground` 加 try/catch 兜底。
2. **BLUETOOTH_CONNECT 未授权时收到蓝牙广播闪退**：`BluetoothStateBroadcastReceive.onReceive` 里 `getProfileConnectionState`/`device.getName()` 在 Android 12+ 需要 BLUETOOTH_CONNECT 运行时权限，未授权时任何蓝牙/SCO 广播都抛 SecurityException。修复：try/catch + getDeviceNameSafely。
   - **⚠️ 这可能就是 1-2 小时闪退的真凶之一**：如果用户手机没授"附近的设备"权限，挂机中蓝牙耳机连接/断开、SCO 状态变化都会让 app 闪退。让用户检查正式版的权限页确认。

### 2. 关键决策与原因

- 测试包做法：debug buildType 加 `applicationIdSuffix '.beta'` + `versionNameSuffix '-beta'` + manifestPlaceholder 改 label。**FileProvider authority 必须跟 applicationId 走**（manifest 改 `${applicationId}.fileprovider`，`ShareLogs.java` 改 `BuildConfig.APPLICATION_ID + ".fileprovider"`），否则两包 authority 冲突第二个装不上。
- release 构建完全不受影响（包名、名称、authority 都不变）。
- 全代码 grep 过，写死包名的就 manifest + ShareLogs 两处（GeneralVariables 里一处是注释）。

### 3. 当前状态

- 测试包：`ft8cn/app/build/outputs/apk/debug/app-debug.apk`（0.93.002-beta），模拟器验证通过待交付用户。
- 修复已提交到 `fix/decode-stall-dxcc-stability` 分支。
- 16KB 对齐警告在 Android 17 模拟器上会弹一次提示框（可勾选不再显示），不影响功能。

### 4. 下一步建议

1. 用户真机装测试版：验证 WiFi 网络模式解码、DXCC 显示、2 小时挂机。
2. **让用户检查正式版"附近的设备/蓝牙"权限是否授予**——未授予+用蓝牙耳机的话，1-2 小时闪退基本可以解释。
3. 闪退复现后取 `Android/data/com.bg7yoz.ft8cn.beta/files/crash/` 日志。

---

## 2026-06-12　模拟器验证：DXCC 前缀匹配测试落地、崩溃捕获实测通过

### 1. 本次会话做了什么

用户暂无真机可测，改用本机安卓模拟器（AVD `Pixel_10`，Android 17 / API 37，16KB 页系统镜像）验证上一会话的修复：

1. **App 冒烟测试通过**：debug APK 安装启动正常、UI 渲染正常、无 Java 异常、崩溃后能正常重启。
2. **DXCC 修复验证通过（落地了第一批自动化测试）**：新建 instrumented 测试 `CallsignDatabaseTest`，5 个用例全部通过——前缀匹配命中完整呼号（BG7YOZ→China/BY）、最长前缀优先（BV9PAB→Pratas Island 而非 Taiwan）、`=` 精确条目命中、查不到返回 null、数据层经度不取反（西经为正约定）。
   - 文件：`ft8cn/app/src/androidTest/java/com/bg7yoz/ft8cn/callsign/CallsignDatabaseTest.java`（新建）、`ft8cn/app/build.gradle`（加 testInstrumentationRunner 和 androidx.test 依赖）
3. **崩溃捕获实测通过**：用 `adb shell am crash` 强制触发未捕获异常，`files/crash/` 正确生成带版本/机型/线程/完整堆栈的日志文件。

### 2. 关键决策与原因

- **呼号库是内存数据库**（`MainActivity` 里 `CallsignDatabase.getInstance(..., null, 1)`，databaseName=null）：磁盘上没有 callsign.db，每次启动从 assets/cty.dat 重新导入。所以验证只能走 instrumented 测试，不能直接查库文件。
- **测试等待导入完成的方法**：`InitDatabase` 跑在 `AppExecutors.diskIO()`（单线程池）上，测试里向同一池提交空任务并 `get()` 即可确保导入完成，不用 sleep 轮询。
- **这份 cty.dat 是中国省级细分版**：China 主条目只含部分前缀，BG7 等在省级条目里（DXCC 仍为 BY），写断言时只能断言 `CountryNameEn.contains("China")`。
- 坑：`gradlew :app:connectedDebugAndroidTest` 首次跑出现 "failed to attach" 偶发失败，重跑即通过；`adb shell am instrument -w` 可作为备用跑法。多设备时 adb 必须 `-s emulator-5554`（**用户明确要求：不许用连接的实体手机测试，只用模拟器**）。

### 3. 当前状态

- 模拟器能验证的都已验证：DXCC 查询逻辑✅、崩溃捕获✅、启动冒烟✅。
- **模拟器验证不了的**（仍需用户真机+电台）：解码卡住修复（需 WiFi 网络模式真实音频流）、长时间挂机发热、完整 QSO 流程。
- 新发现：**libft8cn.so 不是 16KB 页对齐**，Android 15+ 的 16KB 设备上系统弹兼容模式警告（能跑，但有性能损失且未来可能不兼容）。需要用 NDK r27+ 重新编译 .so（仓库里只有预编译库，可能要找原始 cpp 源码）。
- 1-2 小时闪退仍未定论（等真机崩溃日志）。

### 4. 下一步建议

1. 真机验证仍是发版前提（WiFi 网络模式 QSO、2 小时挂机）——只能由用户本人操作。
2. 处理 libft8cn.so 的 16KB 对齐问题（中期任务，影响新机型）。
3. 后续数据层/协议层修复都照此模式补 instrumented/JVM 测试。
4. 验证通过后合入 `release`，升 0.93.003 打 tag。

---

## 2026-06-11　解码卡住与 DXCC 失效修复、崩溃捕获、项目准则建立

### 1. 本次会话做了什么

**修复的问题：**

1. **网络模式发射后解码卡住**（用户报告：回复一次后解码卡住，停止发射收听约两轮恢复）
   - 根因：`HamRecorder` 的录音窗口纯靠样本数凑满（15s×12kHz=180000 采样），发射期间/WiFi 丢包造成的音频断流会让窗口拖到下一时隙才凑满，缓冲内容跨时隙拼接但仍按原 UTC 解码 → 时间错位解不出任何信号；未完成窗口还会积压（每个 720KB）。
   - 修复：新解码周期注册时强制结算上一个未凑满的一次性窗口（缺口补零）；监听器列表改 `CopyOnWriteArrayList` 并修复"回调中自删导致跳过下一个监听器、每周期丢约 160ms 音频"的遍历 bug。
   - 文件：`ft8cn/app/src/main/java/com/bg7yoz/ft8cn/wave/HamRecorder.java`
2. **发射忙等烧 CPU**（加重发热降频，间接恶化上一条）
   - `IcomAudioUdp` 发射 13 秒全程一个核满载自旋；`XieGuAudioUdp` 的发包线程是**常驻**线程、全程自旋。两处都改为绝对时间表 + sleep 的 20ms 节拍；移除发射线程结束时的 `interrupt()` 残留（线程池线程会复用）。
   - 文件：`icom/IcomAudioUdp.java`、`icom/XieGuAudioUdp.java`、`ft8transmit/FT8TransmitSignal.java`（等待轮询 1ms→10ms）
3. **DXCC/归属地/分区显示全部失效**
   - 根因：某次"全面架构优化"提交把呼号查询 SQL 从前缀匹配改成精确匹配，而 callsigns 表存的是 CTY.DAT 前缀，几乎所有呼号都查不到。
   - 修复：恢复前缀匹配（取最长命中）；恢复原版语义：DXCC/ITU/CQ 标记表示"**新**分区"（`!getDxccByPrefix(...)`）、哈希呼号 `<>` 剥离、`isChina` 选择中英文国名、CTY.DAT 经度取反、跳过自由文本和 CQ 的 to 查询；查不到返回 null（恢复原版约定）并给 `CountDbOpr` 调用点补了判空；表结构类型修正（DXCC TEXT，CQ/ITU INTEGER）。保留了本分支新增的 JTDX 优先级高亮功能。
   - 文件：`callsign/CallsignDatabase.java`、`count/CountDbOpr.java`
4. **瀑布图 bitmap 回收竞争**（1-2 小时闪退的候选原因之一）
   - `onSizeChanged`（UI 线程）recycle 位图时，`setWaveData`（后台音频线程，每 160ms）可能正在使用 → "recycled bitmap" RuntimeException 闪退。两者改为共用一把锁，先建新图再回收旧图。
   - 文件：`ui/WaterfallView.java`
5. **新增全局崩溃捕获**
   - 新建 `FT8CNApplication`，崩溃堆栈+机型/版本信息写入 `Android/data/com.bg7yoz.ft8cn/files/crash/`（最多 10 份，本地保存不上传），并在 Manifest 注册。
   - 文件：`FT8CNApplication.java`（新建）、`AndroidManifest.xml`

**其他产出：** `PROJECT_RULES.md`（项目准则，含定位/范围控制/高风险区域/必验清单/发版规范）、本文件。

### 2. 关键决策与原因

- **录音窗口对齐方案**选了"新周期注册时强制结算旧窗口（补零）"而不是"按墙钟时间戳重排样本"：前者改动小、行为可预期（解码数据永远对齐时隙，断流退化为部分数据解码），后者要重写整个数据通路。
- **DXCC 修复保留 fork 的优先级高亮功能**，只恢复被破坏的原版语义，避免把这个 fork 的特性一起回滚。
- **崩溃捕获不做 UI 导出**（小步快跑）：先落地捕获本身，导出按钮下个迭代再说。
- **踩过的雷**（已写入 PROJECT_RULES.md 高风险区域表）：callsigns 表存的是前缀不是完整呼号；CTY.DAT 经度西经为正；XieGu 发包线程是常驻的；线程池线程不要留 interrupt 标志。

### 3. 当前状态

- 以上改动均已提交到分支 `fix/decode-stall-dxcc-stability`（基于 release），`compileDebugJavaWithJavac` 编译通过。
- **全部未经真机验证**：解码卡住的修复需要 WiFi 网络模式 + 实际 QSO 验证；DXCC 显示需真机查看列表标记。
- 1-2 小时闪退**未定论**：瀑布图竞争是候选之一已修；忙等发热问题已修可能间接缓解；但需要崩溃日志确认（崩溃捕获已就位）。
- 构建环境备注：本机 gradle 需要 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`，`ft8cn/local.properties` 已指向 `~/Library/Android/sdk`（该文件不入库）。

### 4. 下一步建议

1. 真机实测本分支：WiFi 网络模式完整 QSO、DXCC 标记显示、挂机 2 小时以上。
2. 复现闪退后从 `files/crash/` 取崩溃日志，确定闪退根因（若是 native 崩溃，crash 目录不会有文件，需要 adb logcat/tombstone，注意区分）。
3. 解码卡住问题如仍出现，抓 `FT8SignalListener`/`GetVoiceData` 日志看"录音窗口强制结算"是否频繁出现及缺口大小（缺口大说明音频流断流严重，方向就对了）。
4. 按 PROJECT_RULES.md 第五条，给 `CallsignDatabase.getCallsignInfo` 补第一个单元测试（前缀匹配规则），防止再次回归。
5. 验证通过后合入 `release`，版本号升至 0.93.003 并打 tag 发版。
6. 潜在风险点：ICOM 协议的 RX 音频丢包不会重传（`IcomUdpBase` 的 rxSeqBuffer 被注释），WiFi 环境差时仍可能影响解码，可考虑恢复重传请求或加丢包统计。
