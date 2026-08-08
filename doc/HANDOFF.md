# HANDOFF 会话交接日志

> 新记录插入顶部，保留历史。每条记录格式：做了什么 / 关键决策 / 当前状态 / 下一步。

---

## 2026-08-08　v0.93.005-beta.7 发布文档最终收口

### 范围、基线与当前状态

- 从精确、clean 基线 `f26abf843c37602e5cebfe724d10ef5cc2eb5d8a` 创建分支 `codex/v0.93.005-beta7-doc-final`。本次只修改正式 notes、beta.6 notes、beta.7 notes 和本文件；不修改 production/build/workflow/scripts/tests，不 push、tag、创建 Release 或设置 Secrets。
- live 的 `v0.93.005-beta.6` tag/Release 与 asset 已存在并保持不可变；beta.6 notes 已改为可直接供最终 publication 会话补丁 live Release body 的来源。beta.5→beta.6 因签名身份不同不能覆盖升级，beta.6→beta.7 也必须卸载一次；beta.7 以后沿用持久 beta-only certificate 才能覆盖升级。
- beta.7 当前仍未执行 tag/Release；beta-only certificate SHA-256 为 `F12E9AD8D1C2B6EAB407FDA8F8877A33097CF789A6AD9073FF8CE6CB828C5971`，包名/版本/versionCode 为 `com.bg7yoz.ft8cn.beta` / `0.93.005-beta.7` / `93007`。formal gate、formal Secrets、formal tag/Release 与真实手机/电台 HIL 独立。
- 本条以上为当前状态；下方更早记录中的旧 `10/10` 与“beta.6 tag/Release 未创建”均是各自会话的 HISTORICAL/SUPERSEDED 证据，不得覆盖本条和当前 release notes 的状态。beta.8 维护项：届时单独更新当前只接受 beta.7 的 hardcoded workflow 门禁，本次不扩大 diff。

### 文档验证与下一步

- `python -m unittest scripts.test_release_gates -v`：14/14；`check_release_contract.py` 默认与 `--history`：通过；`git diff --check`：通过。未重新宣称代码/build/AVD/HIL 结果，本次只完成文档纠正。
- 下一 publication 会话的精确基线是本分支本次文档 commit 的完整 SHA；须先将该 commit 集成到 `codex/v0.93.005-integration`，再按 beta.7 workflow 核验 tag、package/version/code、完整证书指纹、资产和 Release body。beta.6 不得重打 tag、覆盖 asset 或改写历史 tag 内容。

---

## 2026-08-08　v0.93.005-beta.7 持久 beta 签名链修复

### 范围与签名材料

- 基线：`b092598a286a0a51bc59dfc05b18dfb72b892523`；分支：`codex/v0.93.005-beta7-signing`。只在 beta 范围内工作，不 push、不 tag、不创建 beta.7 Release，不改 `release`、formal tag 或 formal Secrets，不删除/移动/覆盖 beta.6。
- 已检查仓库与既有签名位置，没有可恢复的持久 beta key；新 beta-only keystore 与独立 backup 位于仓库外 `C:\Users\cassi\Documents\FT8CN-signing\beta\20260808-204005`。恢复信息为当前 Windows 用户 DPAPI 加密，边界与用户 ACL 记录在该目录说明中。
- 新 beta certificate SHA-256 已写入仓库外受 ACL 保护的指纹说明并配置到 beta secret；私钥、密码和 secret 值未进入仓库或输出；formal 签名身份保持独立。
- 已通过本机 Git Credential Manager 认证的 GitHub API 配置 6 个 `FT8CN_BETA_*` Actions Secrets；仅核对名称和更新时间，未回显值。`gh` CLI 在本机不可用，未用浏览器/manual upload。

### 代码与发布语义

- 普通 `debug` 继续使用 Android Debug certificate；`packageTestApk`/tag prerelease 使用专用 `beta` build type，只接受 beta-only material，缺失、错证书或非 beta.7 版本 fail-fast。
- beta.7 固定为 `versionName=0.93.005-beta.7`、`versionCode=93007`、包名 `com.bg7yoz.ft8cn.beta`；formal canonical 仍为 `0.93.005`/`93005`。
- beta workflow 在 `RUNNER_TEMP` materialize keystore，先用 keytool 核证书、构建后用 apksigner 核精确 SHA-256，并 always 清理；metadata/zipalign 与 contract 也必须通过。beta.6 文档只记录已知签名身份问题，beta.6 tag/Release 保持不可变。

### 当前边界

- beta.5/beta.6 用户迁移 beta.7 前需卸载一次；beta.7 后由同一 beta-only certificate 形成覆盖升级链。自动化/AVD 证据不等于真实手机/HIL；本会话不执行真实电台、CAT/PTT/TX。
- 分层实现提交：`c3d5a47`（beta build/signature/metadata/test）、`18e569e`（workflow/contract）；本条文档随后的 docs commit 只包含说明更新。最终 AUTO_VERIFIED：JVM 21/21，失败 0、错误 0、跳过 0；`scripts.test_release_gates` 14/14；contract 默认与 `--history`、`git diff --check`、`compileDebugAndroidTestJavaWithJavac`、`assembleDebug`、`lintDebug`、两次 beta `packageTestApk` 均通过，lint 0 errors / 330 warnings。
- 独占 `Pixel_10_Pro_XL` AVD：beta.6 asset 可下载，`adb install -r` beta.7 因签名不一致按预期失败；卸载 beta.6 后 beta.7 clean install、Monkey/进程存活和 crash buffer 无该包记录通过；同一 beta-only key 重建的第二个 beta.7 APK `adb install -r` 覆盖升级通过，随后 Monkey/进程存活和 crash buffer 仍通过；AVD 已关闭。
- 当前交接结论：代码与证据已完成，等待主会话独立批准是否将该分支合入后再创建 beta.7 tag/Release；本会话没有执行 tag、Release、push、formal 修改或真实电台/CAT/PTT/TX。

---

## 2026-08-08　v0.93.005-beta.6 配置生命周期阻断修复

### 基线、分支与提交

- 精确基线：`codex/v0.93.005-beta6-ready@be4882a2c94527a038376ff44fc7edbaf8c49207`；fetch 后 HEAD 精确一致，工作树 clean；已阅读 `PROJECT_RULES.md`、本文件和 v0.93.005/beta.6 notes。
- 分支：`codex/v0.93.005-config-lifecycle-fix`；未 push、未合并 `release`、未创建 tag/Release/Secrets。
- 分层代码提交：`31e65b3`（仅测试修复：配置完成事件的显式等待与 observer 清理）；没有 production code/test switch 改动。

### 根因、确定性复现与修复

- 根因是测试时序：`MainActivity` 的配置读取在共享单线程 `DatabaseOpr.dbExecutor` 异步执行，`ActivityScenario.launch()` 只等待 Activity 生命周期，不承诺配置读取已经完成；第 75 行立即断言 `configUiCompletionActionCount == 1`，因此在 suite 负载下可在完成事件前观察到 0。
- 确定性复现：临时未提交 instrumentation 先用 latch 占住共享 `diskIO`，再启动 Activity；基线稳定得到 `expected:<1> but was:<0>`。复现文件随后移除，未进入提交。
- 产品生命周期未发现需要改动的竞态：配置完成仍由 shared `MainViewModel.configLoadComplete` sticky LiveData 发布，旧 Activity 的 `activityUiActive` 门控阻止其消费，重建 Activity 在 active 后消费同一完成状态。
- 修复只在 `MainActivityConfigLifecycleTest`：注册 `configLoadComplete` observer，使用 10 秒 bounded latch 等待真实完成事件，finally 在 UI 线程移除 observer；两个测试在初始 Activity/重建 Activity 断言前都经过该边界。无固定 sleep、skip、放宽断言或 production 外部可控 hook。

### 可复验验证与边界

- **AVD_VERIFIED**：确认无实体设备、无其他 emulator/qemu owner，独占 `Pixel_10_Pro_XL` / API 37。目标 class 连续 10 轮均 2/2；相关配置回归（`MainActivityConfigLifecycleTest`、`DatabaseConfigLoadTest`、`CallsignDatabaseTest`）12/12；全量 `connectedDebugAndroidTest` 连续 2 轮均 61/61，失败 0、错误 0、跳过 0；最终 XML 核对为 61/61/0/0/0。
- **AUTO_VERIFIED（HISTORICAL/SUPERSEDED，仅保留历史证据）**：JVM 全量 21/21；release contract 默认/`--history` 通过；`scripts.test_release_gates` 10/10；`git diff --check`、`assembleDebug`、`packageTestApk`、`lintDebug` 通过，lint 0 errors / 330 warnings。
- **AVD clean beta smoke**：debug beta 以 `pm uninstall --user 0` 后干净安装，Monkey 启动，包进程存活，crash buffer 和应用 crash 目录均无该包记录。
- **HIL**：无真实手机、无电台；未执行 CAT/PTT/TX、真实录音、完整 QSO、长时挂机、功耗/温升或用户 HIL。AUTO/AVD 不等同于 HIL，也不构成正式发布授权。

### 发布结论与下一步

- 本会话修复候选在自动/AVD层面可复验；`v0.93.005-beta.6` tag/Release 未创建，`release` 未合并/推送。
- `formal` 保持 `NO-GO`：缺长期 keystore、可信证书 SHA-256 和明确发布批准；未生成正式签名 APK。
- 最终文档 HEAD、提交、计数、APK metadata/SHA/Debug 证书和上述边界交还主会话；不 push、不 tag、不创建 Release、不执行真实手机/电台操作。

---

## 2026-08-08　v0.93.005-beta.6 发布阻断最终收口

### 基线、分支与分层提交

- 精确基线：`codex/v0.93.005-delivery-lease-fix@30cfb169e7ff2abf65bc79b91e19c072c1846741`；已核对 `PROJECT_RULES.md`、本文件；没有 `notes` 目录；工作树初始 clean。
- 分支：`codex/v0.93.005-beta6-ready`；未推送、未合并 `release`、未创建 tag/Release/Secrets；未修改或提交 `local.properties`、构建产物。
- 分层代码提交：`5e4505c`（P1-1 Ham/Mic stop→start ownership 与真实 MicRecorder control test）、`a820fa2`（P1-2 production foreground promotion boundary 与真实 Service integration test）、`f208096`（P1-2 debug-only failing Service registration）、`90455ce`（P1-3 beta.6 notes local gate/workflow enforcement）；本条、正式 notes 与 beta.6 notes 为最后独立文档提交。

### 根因与修复

- **P1-1 HamRecorder/MicRecorder**：HamRecorder stop 先通过 `MicRecorder.retireSession()` 在线性化 ownership 下撤销旧 session identity，再释放 HamRecorder lifecycle lock；AudioRecord、worker interrupt、前台服务停止和其他外部动作均在锁外完成。并发 start 不能取得旧 id，旧 cleanup 不能清理新 session。
- **P1-1 回归证据**：新增真实 `MicRecorder`（fake AudioRecord factory + controlled read）并发 stop/start test；latch 强制 stop 尚未完成 identity retirement 时发起 start，使用 Future 获取传播异常，断言 start 被锁住、取得不同 session id、新 session 真正进入 running 并交付新数据。
- **P1-2 前台服务**：删除 production Intent 测试 extra 和 synthetic post-startForeground throw；生产 Service 只通过 `protected promoteToForeground()` 调用真实 framework promotion。失败 ACK instrumentation 使用独立 debug-only `FailingAudioForegroundService` 组件，在 promotion boundary 抛出异常；release manifest 不包含该组件或 hook。真实 Service 路径仍覆盖成功 START、matching/stale/no-active STOP、startId/stopSelfResult 与失败 ACK。
- **P1-3 门禁**：beta workflow 使用非空 version-specific notes 检查；本地 `scripts.test_release_gates` 新增 `v0.93.005-beta.6` notes 必须存在、非空、含包名/Debug/HIL 边界的检查。

### 最终验证与边界

- **AUTO_VERIFIED**：最终文档 HEAD 重新执行 JVM `:app:testDebugUnitTest --rerun-tasks`、Java/androidTest Java 编译、`assembleDebug`、`packageTestApk`、`lintDebug`；release contract 默认/`--history`、release gates、`git diff --check` 均以最终输出为准。lint 保留既有 `0 errors / 330 warnings`。
- **AVD_VERIFIED（已被后续 live run supersede）**：本条原记录的 14/14 与 61/61 不能作为当前证据。独立 live rerun 在未修改的基线重现 `MainActivityConfigLifecycleTest.blockedLoadIsConsumedOnlyByRecreatedActivity` 第 75 行失败；详见本条以上的 2026-08-08 配置生命周期修复记录。
- **HIL**：无真实手机、无电台；未执行 CAT/PTT/TX、真实录音、完整 QSO、长时挂机、功耗/温升或用户 HIL。`AUTO_VERIFIED`/`AVD_VERIFIED` 不等同于 HIL，也不构成正式发布授权。

### 产物与发布结论

- 最终 beta APK 为 debug-signed `com.bg7yoz.ft8cn.beta`，版本 `0.93.005-beta` / `versionCode 93005`；最终路径、大小、SHA-256、Debug 证书指纹以最终 HEAD 的最终构建输出为准。
- beta.6 candidate：`GO`（仅限最终 AUTO/AVD/clean beta smoke 证据）；`v0.93.005-beta.6`：`NO-GO`，未创建 tag/Release；`release`：`NO-GO`，未合并/推送；`formal`：`NO-GO`，缺长期 keystore、可信证书 SHA-256 与明确批准，未生成正式签名 APK。

### 下一步

- 将最终 HEAD、分层提交、测试计数、APK/SHA/证书和上述 beta.6/integration/release/formal 结论交还主会话；不推送、不合并、不打 tag/Release，不执行真实手机/电台操作。

---

## 2026-08-08　v0.93.005 最后录音交付 lease 收口

### 基线、分支与分层提交

- 精确基线：`codex/v0.93.005-audio-session-fix@8cc69c5d8d179e1ca789b02ea646e20aae81fb5b`；已核对 `PROJECT_RULES.md`、本文件、无 `notes` 目录，工作树初始 clean。
- 分支：`codex/v0.93.005-delivery-lease-fix`；未推送、未合并 `release`、未创建 tag/Release/Secrets；未修改或提交 `local.properties`、构建产物。
- 分层提交：`9ee73b7`（P1 Mic delivery lease drain + stop→start barrier）、`4e268c6`（HamRecorder 真并发 admission test）、`b263c14`（真实 AudioForegroundService ACK/integration test）；本条为独立文档提交。

### 根因与修复

- **P1 MicRecorder**：停止前已由旧 session identity/lease 准入的 callback 不再被 `stopSession()` interrupt；stop 只在线性化撤销 session 后立即释放录音资源，并仅在没有 in-flight lease 时中止 setup/read worker。旧 callback 仍使用自己的 listener/buffer/session ID，自然返回后 lease 释放，不能进入新 session；新 session 不等待旧 callback，也不接收旧数据。
- **HamRecorder 测试真实性**：`getVoiceData()` 现在由测试线程实际进入，再用 latch 与 stop 争用；旧 monitor 的终结/清理、旧 session 数据不触发新 monitor callback，以及新 session 的独立数据路径均保留断言在线程边界之外传播。
- **AudioForegroundService integration**：新增真实 Android Service instrumentation，使用目标 app context 与 `ResultReceiver` ACK 覆盖真实 foreground start 成功、matching STOP、active new session 下 stale STOP、无 active STOP、命令 `startId/stopSelfResult`；注入失败路径先完成真实 foreground promotion 再抛出 synthetic notification-starter failure，避免 Android foreground watchdog 以 SIGKILL 中断测试，从而验证服务失败清理和 ACK。

### 验证与边界

- **AUTO_VERIFIED**：目标相关 instrumentation 连续 5 轮，每轮 13/13，失败 0、错误 0、跳过 0；全量 `connectedDebugAndroidTest` 连续 2 轮，每轮 60/60，失败 0、错误 0、跳过 0。两组均在独占 `Pixel_10_Pro_XL`（本机 `android-37.0` system image）上执行。
- 文档提交后的最终 HEAD 还必须重新执行 JVM、release contract 默认/`--history`、release gates、`assembleDebug`、`packageTestApk`、`lintDebug`；最终 beta APK 的路径、metadata、SHA-256 和证书指纹以该轮输出为准。
- **HIL**：无实体设备、无电台；未执行 CAT/PTT/TX、真实录音、完整 QSO、长时挂机、功耗/温升或用户 HIL。

### 发布结论

- beta candidate 只有在最终 HEAD 的上述 AUTO/AVD 门禁和 clean beta smoke 完成后才可判定为 `GO`；`v0.93.005-beta.6`、`release`、`formal` 在本会话均未创建或推进。
- `formal` 保持 `NO-GO`：缺长期 keystore、可信证书 SHA-256 与明确批准；不生成正式签名 APK。
- 不推送、不合并、不打 tag、不创建 Release，不执行真实手机/电台操作。

### 下一步

- 完成最终 HEAD 的自动门禁、clean beta install/Monkey/crash 检查；随后关闭独占 emulator，并将最终 HEAD、提交、测试计数、APK/SHA/证书与 beta.6/integration/release/formal GO/NO-GO 交还主会话。

---

## 2026-08-08　v0.93.005 音频会话最终并发收口

### 基线、分支与分层提交

- 精确基线：`codex/v0.93.005-final-risk-fix@9fcfeb81fd9c3620c7c620ff89926a4164d17419`；已核对 `PROJECT_RULES.md`、本文件、无 notes 目录、初始 clean tree。
- 分支：`codex/v0.93.005-audio-session-fix`；未推送、未合并 `release`、未创建 tag/Release/Secrets；未修改 `local.properties` 或提交构建产物。
- 分层提交：`8e85194`（Mic session delivery + stop→start 交错测试）、`6210548`（HamRecorder monitor generation/终态 + 并发测试）、`a1b0800`（前台服务 session/startId/ACK + 生命周期测试）、`b7aaa5b`（正常 current epoch duration publication）、`db01402`（MainViewModel/UtcTimer heartbeat epoch fence）、`c4ef5a5`（Decode executor terminal 幂等测试）。本条与正式 notes 为独立文档提交。

### 根因与设计

- **MicRecorder P1**：旧 worker 在 `isCurrent` 检查后仍可跨 stop→start 调用全局 listener，且共享 float buffer 可重叠。现在 Session 捕获自己的 listener/buffer，交付准入带 session ID 和 in-flight lease；HamRecorder 在交付侧再次验证 session/generation，因此旧数据最多完成旧 session 的已准入回调，不能被新 monitor 接收。
- **HamRecorder P1**：monitor 原来无 generation，stop 后可被新 session 音频或 `forceComplete` 触发。现在 monitor 绑定 recorder generation；get/stop/start 用 lifecycle lock 做准入与摘除，terminal close 不调用旧回调，forceComplete/receiveData 与 monitor terminal 锁串行，异常经 Future/调用方传播。
- **AudioForegroundService P1**：startService 返回不代表 startForeground 成功，stale STOP 还可能在无 active 时留下 started service。现在使用 session + Android startId 状态机；matching STOP 才清 active，stale STOP 不 self-stop 新 session，无 active STOP 用 `stopSelfResult`，startForeground 异常清理并通过 ResultReceiver ACK 返回失败。
- **P2**：duration 正常准入路径有正向回归；MainViewModel 与 UtcTimer 的 heartbeat 由 cleared/epoch gate 保护；owned decode/bounded executors 已检查终态 shutdown，shared AppExecutors 只由应用级 owner 管理，重复 terminal 调用保持幂等。

### 验证与边界

- `AUTO_VERIFIED`：JVM 全量 `:app:testDebugUnitTest --rerun-tasks` 为 `21/21`，失败 0、错误 0、跳过 0；Java 与 androidTest Java 编译通过；release contract 默认/`--history` 通过；release gates `9/9`；`git diff --check` 通过。
- `PAUSED_AVD`：只读检查发现 `emulator-5554` 已被 `Pixel_10_Pro_XL` / API 37 进程占用，无法证明属于本任务或已结束会话；未安装、未重启、未运行 connected tests/instrumentation 轮次/clean beta smoke，不把历史 AVD 结果计入本会话。
- `HIL`：未连接真实手机或电台，未执行 CAT/PTT/TX、完整 QSO、长时挂机、功耗/温升或用户 HIL。

### 产物与发布结论

- 最终 debug beta APK 将由文档提交后的最终 HEAD 重新执行 `assembleDebug/packageTestApk`；包名应为 `com.bg7yoz.ft8cn.beta`、版本 `0.93.005-beta`、`versionCode 93005`，APK metadata/SHA 以最终验证输出为准。
- beta 完整候选验收：`NO-GO`（AVD paused，仅 AUTO/JVM/静态门禁可 GO）；`v0.93.005-beta.6`：`NO-GO`，未创建 tag/Release；`release`：`NO-GO`，未合并/推送；`formal`：`NO-GO`，缺长期 keystore、可信证书指纹和明确批准，未生成正式签名 APK。

### 下一步

- 在确认 `Pixel_10_Pro_XL` 独占且无其他活动会话后，另行运行指定 instrumentation 5 轮、full connected 连续 2 轮和 clean beta smoke；随后再由主会话决定是否集成，任何 tag/Release、真实设备/HIL 或正式签名动作均需另行授权。

---

## 2026-08-08　v0.93.005 最终发布前 P1/P2 风险收口

### 基线与提交

- 精确基线：`codex/v0.93.005-decode-race-fix@7bea59057690146dbaada2352e81de1f8729083d`；工作树初始干净。
- 分支：`codex/v0.93.005-final-risk-fix`；未推送、未合并 `release`、未打 tag、未创建 Release/Secrets。
- 代码与测试按问题分层提交：`c5baf64a68dfcd10c133a9e61e06257a28a7b3f6`（P1-1）、`dc20db436fc8c8e8b692ef3a3151827614dc7d73`（P1-2/P2-1）、`c9a304ae9213b5ea85ff1063ac11eb67cf6e9005`（P2-2/P2-3）、`bfd3ea911164d4afb1d372b60a351c456fc2a98f`（lint 权限注解）；本条为独立文档提交。

### 根因与设计

- **P1-1**：解码耗时 UI 更新原先只在 `token.throwIfCancelled()` 后执行，停止或 `onCleared()` 可以插入检查与 `postValue()` 之间。现在耗时发布也通过 `MainViewModel` 的 `DecodeLifecycleGate`/epoch 准入；旧 epoch 被拒绝并转为取消，不再产生耗时副作用。准入锁只保护判定，不跨数据库、网络、电台或其他外部调用。
- **P1-2/P2-1**：录音前台服务、`AudioRecord` 初始化和启动原先位于调用者的同步路径，失败返回可能留下服务；现在每次开始都有独立 session/generation，阻塞设备初始化和读循环在 worker 执行。只有 `AudioRecord.startRecording()` 成功后才报告运行中；权限、构造、启动、异常和停止均进入单一终态清理，释放本 session 的资源并按 session token 停止服务，旧 session 不能清理新 session。
- **P2-2**：门控并发测试使用 `Future.get()` 传播 worker 异常并建立明确的完成边界，不再把工作线程断言或未确认的 `join()` 当成通过。
- **P2-3**：`DecodeCoordinatorTest` 使用可控双线程 executor 与 latch，强制旧任务的 terminal/finally 清理发生在新任务已接纳之后；`ActiveRun` 身份校验确认旧清理不能清除新任务的 active 状态。
- **P2-4**：保留 `RecorderLifecycleTest` 的拒绝权限 `ContextWrapper` 隔离；新增四个真实逻辑层 instrumentation 用例覆盖权限失败、初始化失败、启动失败和正常停止的服务/资源释放，不撤销系统权限，避免 SIGKILL。

### 验证与边界

- `AUTO_VERIFIED`：JVM 全量 `17/17`（失败 0、错误 0、跳过 0）；`scripts.test_release_gates` `9/9`；release contract 默认与 `--history` 均通过；`assembleDebug`、`packageTestApk`、`lintDebug` 成功，lint `0 errors/330 warnings`；`git diff --check` 通过。新增 MicRecorder instrumentation 为 `4/4`，独占 AVD 连续 `5` 轮；全量 connected 为 `51/51`，失败/错误/跳过均为 0，连续 `2` 轮。
- `AVD_VERIFIED`：独占 `Pixel_10_Pro_XL` / API 17；最终 beta 包 clean install 成功，Monkey 启动成功，包进程存活，crash buffer 中该包为 0，应用 crash 目录无文件。一次目标类名误指定的 instrumentation 命令得到 ClassNotFound，不计入测试证据；随后正确的 `wave.MicRecorderLifecycleTest` 五轮全部 `4/4` 通过。
- `HIL`：未连接真实手机或电台，未执行 CAT/PTT/TX、完整 QSO、长时挂机、功耗/温升或用户 HIL；这些边界仍未授权或未完成。

### 产物与发布结论

- beta APK：`ft8cn/.artifacts/FT8CN-v0.93.005-beta-bfd3ea9.apk`，`22,649,435` bytes，SHA-256 `EEB28BF9EA2FCF05B1A921EEF78A8750DF3C534E5F5756717C2EAFCEA744C0B0`；包名 `com.bg7yoz.ft8cn.beta`，版本 `0.93.005-beta`，`versionCode 93005`，min/target SDK `23/34`，Android Debug 证书 SHA-256 `5da76c45b0875913e0a08d7124f49a87bcf2283429c074c1ddc8eb495d3e8db3`。
- 本地 beta candidate：`GO`（仅限 AUTO/AVD 证据）；`v0.93.005-beta.6`：`NO-GO`，本会话未创建 tag 或 Release；`release`：`NO-GO`，未合并且未推送；`formal`：`NO-GO`，缺少长期 keystore、可信证书指纹和明确批准，且未生成正式签名 APK。

### 下一步

- 主会话复核上述四个代码提交和本条文档提交后再决定是否集成；任何 beta.6、release 或 formal 发布动作均需另行授权并重新满足签名、tag、Release、真实设备/HIL 门禁。

---

## 2026-08-08　v0.93.005 解码结果竞态修复

### 做了什么

- 从 `codex/v0.93.005-avd-stability@2a3d1a89501dca7e21a1e0149c784ec6ede1a78e` 创建 `codex/v0.93.005-decode-race-fix`。
- P1 提交 `5cae51a`：新增 `DecodeLifecycleGate`，把解码 epoch 与 `onCleared`/停止状态的副作用准入线性化；消息列表/UI、关注列表、自动回复解析、QTH 任务、SWL/消息/呼号网格数据库写入均在准入点受保护。门控锁只保护判定，不包住数据库、网络或电台外部调用；`stopListen()` 先关闭门控再取消工作线程。
- P2 提交 `d82663c`：`DecodeCoordinator` 使用 `ActiveRun` 身份和 `started` 状态；启动前取消会清理 `active/activeFuture`，运行中旧 Future 只能由自身终态清理，不能清掉新任务状态。

### 根因与回归证据

- P1 根因是 `MainViewModel.afterDecode()` 只有入口一次性 epoch 检查；检查通过后 `onCleared()` 仍可插入，旧结果随后执行持久化、UI/列表、QTH 和自动回复等副作用。
- `DecodeLifecycleGateTest.clearAfterEntryCheckRejectsTheStaleEffect` 用 latch 强制“入口检查后 → close → 副作用准入”，确认旧副作用为 0；相关新 JVM 测试（门控 2 + coordinator 6）连续 5 轮均通过。
- P2 回归覆盖排队 Future 启动前取消，以及运行中旧任务在 `finally` 前不得释放槽位；均无失败、错误或跳过。

### 当前状态与边界

- `AUTO_VERIFIED`：JVM 全量 `:app:testDebugUnitTest --rerun-tasks` 为 16/16；新增竞态测试 8/8 连续 5 轮；`assembleDebug`、`packageTestApk`、`lintDebug` 成功，lint 为 0 errors/330 warnings；`git diff --check`、release contract 默认与 `--history`、release gates 均通过（9/9）。
- `AVD_VERIFIED`：独占 `Pixel_10_Pro_XL` / API 17，`:app:connectedDebugAndroidTest --rerun-tasks` 连续 2 次均为 47/47，失败/错误/跳过均为 0；当前 beta `d82663c` 已卸载后干净安装，Monkey 启动成功，进程存活，crash buffer 与应用 crash 目录均为 0。
- 本会话未连接真实手机或电台，未执行 CAT/PTT/TX、完整 QSO、长时挂机、功耗/温升或用户 HIL；未推送、未合并 `release`、未打 tag、未创建 Release/Secrets。formal release 仍因长期签名材料、可信证书指纹和明确批准缺失而 `NO-GO`。

### 下一步

- 主会话可复核 `5cae51a` 与 `d82663c` 后决定是否集成；若进入正式发布，仍需按签名迁移门禁和真实设备/HIL 流程重新授权验收。

---

## 2026-08-08　v0.93.005 AVD instrumentation SIGKILL 专项

### 做了什么

- 从最终集成基线 `afe9d7abc8bdfb9377a95a2a815070068cb7b1cd` 创建 `codex/v0.93.005-avd-stability`，工作树初始干净；只修改了 `RecorderLifecycleTest`，提交 `9dc7be5b33deda6420d851f7a4c32879d5b02451`。
- 首轮完整 `:app:connectedDebugAndroidTest` 在独占 `Pixel_10_Pro_XL` / API 37 冷启动 AVD 上复现为 45/47；保存 Gradle XML/HTML、全量 logcat、crash buffer、`dumpsys meminfo`、`dumpsys activity exit-info`、ANR/tombstone 目录和 Dropbox 证据到 `ft8cn/.artifacts/avd-stability-20260808/`。
- 直接在已授予 `RECORD_AUDIO` 的条件下运行录音权限用例，`ApplicationExitInfo` 明确为 `reason=8 (PERMISSION CHANGE)`、`description=permissions revoked`；ActivityManager 记录 `permissions revoked` 后以 signal 9 杀掉 `com.bg7yoz.ft8cn.beta`。没有新的 ANR、tombstone、crash buffer、OOM 或 lmkd 杀进程证据。
- 全量清点确认 14 个 instrumented 测试类共 47 个 `@Test`；复核测试资源清理和生产生命周期（MainViewModel、FT8TransmitSignal、录音、Timer、线程池、网络 socket、Activity receiver）后，没有发现需要修改的产品生命周期缺陷。原测试用 `UiAutomation.revokeRuntimePermission` 直接改变被测包权限，触发 Android 的合法进程终止；改为不改变系统权限的拒绝权限 `ContextWrapper`，并在 finally 恢复全局 context。

### 关键决策与证据

- 这是测试注入缺陷，不是 AVD RAM/heap、instrumentation runner 或产品 Java/native crash；因此没有调整 AVD 资源、sharding、orchestrator，也没有扩大生产代码改动。
- 修复后 `RecorderLifecycleTest` 单独 5/5，历史失败点 `X6100CommandSubmissionTest` 单独 5/5；全量 47/47 连续 2 次，失败/错误/跳过均为 0，报告 XML 不再有 `system-err`。
- beta 先 force-stop 后卸载成功，再以 `FT8CN-v0.93.005-beta-9dc7be5.apk` clean install；Monkey 启动成功，包进程存活，crash buffer 为空，应用 crash 目录不存在（无崩溃文件）。

### 当前状态

- `AUTO_VERIFIED`：JVM 12/12；`assembleDebug`、`packageTestApk`、`lintDebug` 成功；lint 330 warnings / 0 errors；release contract 默认与 `--history` 通过；`python -m unittest scripts.test_release_gates -v` 为 9/9；`git diff --check` 通过。
- `AVD_VERIFIED`：独占 Pixel_10_Pro_XL、无实体设备，47/47 连续 2 次；未连接真实手机或电台，未执行 CAT/PTT/TX、完整 QSO、长时功耗/温升 HIL。
- 未推送、未合并 release、未打 tag、未创建 Release/Secrets；专项分支仍保持本地。

### 下一步

- 主会话可按 `9dc7be5` 复核并重新进入最终集成验收；本专项只证明 AVD instrumentation 与 beta smoke 边界，不替代真实设备/HIL 或正式签名发布授权。

---

## 2026-08-08　v0.93.005 最终集成与验收证据

### 做了什么

- 以干净基线 `codex/v0.93.005-integration@2906decc85ce7e4ac79d9f9443a23e0cc2bd49d9` 创建 `codex/v0.93.005-final-integration`；逐个检查了待集成提交的内容、父提交和来源工作树，未推送、未合并 `release`、未创建或推送 tag/Release，未创建 keystore 或设置 Secrets。
- 集成 P0-D `60901df028c2f8f49f0cf10963e3336202d27da0`、P1 `ed8a996`/`6d78cf6`/`ad86bb5`/`58fcdd6`，以及发布质量 `354bd18`/`532fe6c`/`e1b41b1`/`e533c2d`/`30f5de6`；对应实现已落在最终分支的独立 cherry-pick 提交中。
- 三个来源 HANDOFF 提交 `f1093cc`、`f8b180c`、`71faabb` 均基于同一旧文档头，未机械叠加冲突版本；本条整合记录保留了三者关于 P0-D、录音/FT-710/Wavelog/JTDX 和发布质量的全部事实。

### 关键决策与独立复核

- `MainViewModel` 的 P0-D epoch/终态门与 P1 JTDX priority 前置计算语义合并：priority 在 `findIncludedCallsigns` 前同步计算，异步位置查询使用无 priority 版本；旧 epoch 不进入消息、自动通联、PTT 或后续任务。P0-D 保持单飞、重叠跳过和取消中的 native 任务退出协调器后才允许下一任务进入；既有快速/深度解码、JNI、音频采集和自动通联规则未重写。
- 复核确认既有 P0-A/B/C/E 的 SubmissionResult、网络 EOF/断连、PTT/SCO 终态、配置生命周期和发布签名门禁路径未被这些变更移除；FT-710 USB CAT 只写核心未改，Yaesu Timer 仅在 rig 生命周期结束时停止；录音启动失败不再报告运行中，Wavelog station ID 使用精确 JSON token 匹配。
- formal workflow 继续要求 tag 与 `origin/release` 精确 SHA，增加 APK 非空检查，并用带 `--verify-tag` 的单步 `gh release create` 携带资产；不使用 `--clobber`，不再分离 `gh release upload`。release notes 保留正式签名、tag、Release、AVD 与 HIL 未完成边界。

### 当前状态

- `AUTO_VERIFIED`：`git diff --check` 通过；`check_release_contract.py` 默认与 `--history` 均通过；`python -m unittest scripts.test_release_gates -v` 为 9/9；最终集成代码的 `:app:testDebugUnitTest` 为 12/12（失败 0、错误 0、跳过 0）；`assembleDebug`、`packageTestApk`、`lintDebug` 均成功，lint 为 0 errors/330 warnings。
- beta APK 已构建并验证为包名 `com.bg7yoz.ft8cn.beta`、版本 `0.93.005-beta`、`versionCode` `93005`、Android Debug 签名；正式签名 APK 未生成。
- 已确认无实体设备、无其他 emulator/qemu 进程后独占启动 `Pixel_10_Pro_XL`（API 17），beta 包先卸载后干净安装成功。三次全量 `connectedDebugAndroidTest` 均因 instrumentation 进程被 SIGKILL 而失败：第一次完成 46/47、X6100 用例失败；第二次完成 45/47、录音权限用例失败；最终 HEAD beta APK 的第三次仍完成 45/47、录音权限用例失败。两个失败用例单独运行均通过，crash buffer 无应用崩溃；因此当前没有全量 AVD 通过证据。
- beta `Monkey` 启动 smoke 成功，`MainActivity` 进程存活且 crash buffer 为空；首次安装的录音权限提示仍在。没有连接真实手机、电台，没有 CAT/PTT/TX、完整 QSO、长时稳定性、功耗或温升/HIL 证据。

### 下一步

- 该分支可供后续专门会话继续定位全量 AVD instrumentation SIGKILL，但不能作为“AVD 全量通过”或正式发布授权。正式发布仍需长期 keystore、可信证书 SHA-256、明确批准和真实设备/HIL 门禁；不得覆盖既有 tag 或 Release。

---

## 2026-08-03　v0.93.005-beta.5 GitHub 预发布自动验收

### 做了什么

- 已成功发布不可变 tag `v0.93.005-beta.5`，其 peeled target 为 `3f6b0562806d016b0164fbd369234ea03797f4e0`。
- GitHub Actions run [`30760667432`](https://github.com/iCassius/FT8CN/actions/runs/30760667432) 已完成 beta 预发布；对应 [GitHub Pre-release](https://github.com/iCassius/FT8CN/releases/tag/v0.93.005-beta.5) 已生成。
- 远端资产为 `FT8CN-v0.93.005-beta-3f6b056.apk`，大小 `21,199,657` bytes，SHA-256 `7fa6b632…decae21`；包名 `com.bg7yoz.ft8cn.beta`，版本 `0.93.005-beta`，`versionCode` `93005`。

### 关键决策

- 此版本是 Android Debug 签名测试预发布；远端证书 SHA-256 记录为 `0ad16c4f…cc71f`。它不是正式签名版本，且 `AUTO_VERIFIED` 不等同于实机/HIL。
- `v0.93.005-beta.1` 至 `v0.93.005-beta.4` 是已失败且不可变的 tag，均未创建 Release；不得删除、移动或复用这些 tag。后续预发布必须使用新 tag 和同名独立 notes。

### 当前状态

- `AUTO_VERIFIED`：JVM 8/8；AVD `connectedDebugAndroidTest` 43/43，失败、错误、跳过均为 0；GitHub 预发布构建、签名验证、资产上传完成。
- 上述结果不含真实设备、电台、完整 QSO、长时稳定性、功耗或温升验证；P0-D 与 P0-F 仍未完成。

### 下一步

- 用户在真实设备上测试 beta.5；继续 P0-D、P0-F 与有线 legacy 连接契约。
- 处理 beta 签名连续性、构建可复现性和发布半完成恢复策略，再评估正式签名迁移。

---

## 2026-08-01　v0.93.005 P0 集成自动验收

### 做了什么

- 已在独立集成分支受控合入 P0-C 配置加载完成状态、P0-A 发射 PTT/SCO 生命周期、P0-B 网络/音频快照与 EOF 隔离、P0-E 版本和发布门禁。
- 根据独立审查补齐 Flex TCP EOF 到连接状态所有者的传播、X6100 早期 EOF 安全收尾、流初始化的会话取消/退避上限，以及断连重连时 ping 调度器重建。
- 交接日志以 `release` 文档为基线，不合并各子会话的过程日志；最终只保留这一条统一集成记录。

### 关键决策

- 自动化、AVD、安装启动 smoke 与真实设备/HIL 分开报告；本次结果只证明 `ed64570` 代码树的自动化边界。
- 测试产物必须由当前集成代码树重新打包，不使用以中间提交 `6ec16d8` 命名或构建的 APK 作为最终集成包。

### 当前状态

- `AUTO_VERIFIED`：JVM 8/8；AVD `connectedDebugAndroidTest` 43/43，失败 0、错误 0、跳过 0；clean、build、TEST/BETA package、干净安装和启动 smoke 均通过。
- 上述结果不是实机/HIL。P0-D 解码单飞/终态、有线 legacy 连接契约与 P0-F 真机/性能门禁仍未完成；未生成正式 Release、tag 或 GitHub Release。

### 下一步

- 继续 P0-D、有线 legacy 契约和 P0-F 真机/HIL；正式签名发布仍需提供长期 keystore、可信证书 SHA-256，并完成真实设备干净安装确认。

---

## 2026-07-30　完整产品与架构审计文档

### 做了什么

- 只修改文档，没有修改功能代码，也没有提交 Git。
- 新增 `doc/PROJECT_OVERVIEW.md`，记录产品定位、用户流程、功能、架构、数据流、线程模型、近期变化、成熟度和验证边界。
- 新增 `doc/ROADMAP_TODO.md`，把确认缺陷、较高概率风险、性能与省电工作按 P0/P1/P2 整理为可派发任务。
- 更新 `doc/README.md` 索引和当前审计状态。

### 关键决策

- 发版前先处理共享执行器/Observer、任务快照与 EOF、配置完成事件、解码单飞与终态、Release 签名/CI/版本以及真机门禁。
- 将 JTDX priority 首次自动候选遗漏、Wavelog station ID 包含式误匹配、FT-710 无响应轮询与 Timer 未取消列为确认缺陷；修复 FT-710 生命周期时保留已有现场 A/B 支持的 USB CAT write-only 核心。
- 不把构建成功、历史模拟器结果或代码正向变化写成 HIL。
- 性能任务先建立基线，再使用相对改善和不退化目标；`MainViewModel` 与 `GeneralVariables` 只允许小步拆分。

### 当前状态

- 审计基线：`release@d8f8c5d`；功能代码审计开始前工作树干净，当前仅本次文档有未提交变化。
- `assembleDebug` 成功；JVM 单测 `NO-SOURCE`；Instrumented 仅 1 个类 5 个用例，本轮未运行；`lintDebug` 为 5 errors / 343 warnings；`assembleRelease` 成功，但当前 APK 为 Android Debug 证书。
- 本轮无设备、模拟器、性能 profile 或 HIL。

### 下一步

按 `doc/ROADMAP_TODO.md` 从 P0-C、P0-A/P0-B、P0-D、P0-E 开始小步修复和补测试，完成 P0 后再进入真机稳定版门禁。

---

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
