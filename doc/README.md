# FT8CN 维护文档目录索引

本目录收录了 FT8CN 社区维护版项目的所有排障、发布历史及维护规则文档。方便后续读取、交接并继续开发。

## 📂 文档结构与说明

### 1. 📄 [项目已完成改动与版本发布历史 (RELEASES.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/RELEASES.md)
*   **状态**：🟢 已完成
*   **作用**：合并了项目所有已完成的改动记录。主要包括：
    *   **第一部分**：`v0.93.001` 到 `v0.93.004` 的中文版本发布历史与变更日志。
    *   **第二部分**：YAESU FT-710 电台兼容性修复的深度总结、核心串口只写修复方案（解决复合 USB 音频会话冲突与 0 功率发射）和外围稳定性（MicRecorder、蓝牙广播、CDC ACM 接口）的差异分析。
    *   **第三部分**：已证伪并回收的尝试记录（音频格式、EX命令、模式强制改写等）。
*   **注意**：所有已完成并经过测试稳定的改动均在此文档中记录，不单独列出其他文件。

### 2. 📄 [会话交接日志 (HANDOFF.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/HANDOFF.md)
*   **状态**：🔄 动态更新（收工时更新）
*   **作用**：记录历次开发会话所做的工作、关键决策、当前状态以及下一步行动。
*   **读取建议**：每次开工前应首先阅读此文件头部，了解上一次会话的具体进展。

### 3. 📄 [电台支持问题跟踪日志 (FT710Issue.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/FT710Issue.md)
*   **状态**：🔍 问题定位中 / 验证中
*   **作用**：详细记录针对 YAESU FT-710 的发射功率、模式切换和 USB 音频共存问题的排查全过程，包含每次测试的日志解读与详细的 A-B 回滚验证计划。

### 4. 📄 [已知问题列表 (KNOWN_ISSUES.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/KNOWN_ISSUES.md)
*   **状态**：⚠️ 待处理
*   **作用**：收录并跟踪在当前高危稳定性修复中被有意延后的 lint 问题，如 AndroidManifest 的导入 intent-filter 警告，以及非主要语言区域（希腊语、日语、西班牙语）的缺失翻译。

### 5. 📄 用户与使用手册 (由原根目录 PDF 转换而来)
*   📄 [FT8CN 快速手册 0.88版 (FT8CN快速手册0.88版.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/FT8CN快速手册0.88版.md)
*   📄 [FT8CN 英文快速手册 0.89版 (FT8CN_Quick_Guide_v0.89.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/FT8CN_Quick_Guide_v0.89.md)
*   📄 [FT8CN 设计初衷与使用说明 0.88版 (FT8CN软件设计初衷及使用说明0.88版.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/FT8CN软件设计初衷及使用说明0.88版.md)
*   **状态**：🟢 已完成转换
*   **作用**：项目自带的用户操作指南和详细的软件设计理念文档。

### 6. 📄 编译构建与性能优化指南 (由原 ft8cn 目录移入)
*   📄 [性能与内存优化指南 (OPTIMIZATION_GUIDE.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/OPTIMIZATION_GUIDE.md)
*   📄 [Android Gradle 插件升级修复说明 (AGP_UPGRADE_FIX.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/AGP_UPGRADE_FIX.md)
*   📄 [Release 编译签名说明 (RELEASE_SIGNING.md)](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/doc/RELEASE_SIGNING.md)
*   **状态**：🟢 已完成
*   **作用**：面向开发者的编译、打包签名及应用性能和内存优化说明。

---

## 🛠️ 下一步开发与测试指引

根据最近几次的改动，目前的最新状态如下：
1.  **已完成验证**：
    *   核心串口只写机制和无读循环机制有效解决了 FT-710 的音频会话冲突。
    *   网络模式解码卡死、UDP 发包忙等、瀑布图回收崩溃、权限未授予前台服务/蓝牙接收器闪退均已修复，并在模拟器上通过了冒烟测试。
    *   新增了 Wavelog 日志云上传支持和 GitHub Actions 的 Release 编译构建流。
2.  **当前进度**：
    *   正在进行真机实际通联（网络模式/QSO）与 2 小时以上的长时间挂机内测。
    *   测试完成后，全部补丁将收敛合入 `release` 分支，正式发布 `v0.93.004` 稳定版。
