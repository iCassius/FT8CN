# FT8CN 维护文档目录索引

本目录收录了 FT8CN 社区维护版项目的所有排障、发布历史及维护规则文档。方便后续读取、交接并继续开发。

## 📂 文档结构与说明

### 建议先读

*   📘 [项目说明与架构总览 (PROJECT_OVERVIEW.md)](PROJECT_OVERVIEW.md)
    *   **状态**：🟢 当前事实基线
    *   **作用**：说明产品定位、用户价值、功能范围、架构、数据流、线程模型、近期变化、成熟度和验证边界。
*   🗺️ [Roadmap 与可派发 TODO (ROADMAP_TODO.md)](ROADMAP_TODO.md)
    *   **状态**：🔄 执行中
    *   **作用**：按 P0/P1/P2 记录为什么改、如何改、用户价值、涉及范围、自动化与真机验收、依赖、风险和回滚。

### 1. 📄 [项目历史改动与版本记录 (RELEASES.md)](RELEASES.md)
*   **状态**：📚 历史记录
*   **作用**：合并了项目所有已完成的改动记录。主要包括：
    *   **第一部分**：`v0.93.001` 到 `v0.93.004` 的历史发布记录，以及 `v0.93.005` 发布准备门禁。
    *   **第二部分**：YAESU FT-710 电台兼容性修复的深度总结、核心串口只写修复方案（解决复合 USB 音频会话冲突与 0 功率发射）和外围稳定性（MicRecorder、蓝牙广播、CDC ACM 接口）的差异分析。
    *   **第三部分**：已证伪并回收的尝试记录（音频格式、EX命令、模式强制改写等）。
*   **注意**：此文档记录历史实现与当时结论，不代表所有内容都已在当前基线稳定验证。自动化、模拟器、真机和 HIL 证据只对记录中的版本、设备与场景有效。

### 2. 📄 [会话交接日志 (HANDOFF.md)](HANDOFF.md)
*   **状态**：🔄 动态更新（收工时更新）
*   **作用**：记录历次开发会话所做的工作、关键决策、当前状态以及下一步行动。
*   **读取建议**：每次开工前应首先阅读此文件头部，了解上一次会话的具体进展。

### 3. 📄 [电台支持问题跟踪日志 (FT710Issue.md)](FT710Issue.md)
*   **状态**：🔍 问题定位中 / 验证中
*   **作用**：详细记录针对 YAESU FT-710 的发射功率、模式切换和 USB 音频共存问题的排查全过程，包含每次测试的日志解读与详细的 A-B 回滚验证计划。

### 4. 📄 [已知问题列表 (KNOWN_ISSUES.md)](KNOWN_ISSUES.md)
*   **状态**：⚠️ 待处理
*   **作用**：收录并跟踪在当前高危稳定性修复中被有意延后的 lint 问题，如 AndroidManifest 的导入 intent-filter 警告，以及非主要语言区域（希腊语、日语、西班牙语）的缺失翻译。

### 5. 📄 用户与使用手册 (由原根目录 PDF 转换而来)
*   📄 [FT8CN 快速手册 0.88版 (FT8CN快速手册0.88版.md)](FT8CN快速手册0.88版.md)
*   📄 [FT8CN 英文快速手册 0.89版 (FT8CN_Quick_Guide_v0.89.md)](FT8CN_Quick_Guide_v0.89.md)
*   📄 [FT8CN 设计初衷与使用说明 0.88版 (FT8CN软件设计初衷及使用说明0.88版.md)](FT8CN软件设计初衷及使用说明0.88版.md)
*   **状态**：🟢 已完成转换
*   **作用**：项目自带的用户操作指南和详细的软件设计理念文档。

### 6. 📄 编译构建与性能优化指南 (由原 ft8cn 目录移入)
*   📄 [性能与内存优化指南 (OPTIMIZATION_GUIDE.md)](OPTIMIZATION_GUIDE.md)
*   📄 [Android Gradle 插件升级修复说明 (AGP_UPGRADE_FIX.md)](AGP_UPGRADE_FIX.md)
*   📄 [Release 编译签名说明 (RELEASE_SIGNING.md)](RELEASE_SIGNING.md)
*   **状态**：🟢 已完成
*   **作用**：面向开发者的编译、打包签名及应用性能和内存优化说明。

---

## 🛠️ 当前审计状态与下一步

当前本地集成分支为 `codex/v0.93.005-16kb-integration`，来源为远端 `codex/v0.93.005-integration@786ceed4`，开发最终 SHA 为 `9fbda6f`。远端 beta.1 至 beta.7 已占用，下一可用 notes 为 `v0.93.005-beta.8`，尚未创建 tag/Release：

1. 16KB native 自动化 `GO`、P0 `0`、strict oracle 通过；四 ABI `PT_LOAD=0x4000`，JNI contract 为 31 required + 2 optional export。
2. API 37 / `PAGE_SIZE=16384` 的 16KB AVD 启动成功，fatal compatibility mode 已关闭；connected 测试 63 pass、1 intentional skip，AVD 已关闭。
3. 4KB AVD、真实 Android 设备、电台 CAT/PTT/TX、完整 QSO、长时间挂机、功耗和温升仍未完成；自动化/AVD 不等于真机/HIL。
4. beta.8 必须由 GitHub Actions 使用 beta-only 受保护签名材料实际构建并通过 workflow；formal Release 仍因本地缺少受保护正式签名材料而 `NO-GO`。
5. 后续任务与验收顺序以 [ROADMAP_TODO.md](ROADMAP_TODO.md) 为准；构建、自动化、16KB/4KB AVD、真机和 HIL 必须分开报告。
