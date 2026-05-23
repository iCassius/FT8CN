# FT8CN 性能优化与架构现代化指南

本项目在 Android 14 适配及进阶优化过程中，重点解决了内存泄漏、高功耗、陈旧 API 使用以及资源管理混乱的问题。

## 1. 内存管理与生命周期优化

### 1.1 MainViewModel 架构修正
- **问题**: `MainViewModel` 之前存在生命周期管理缺陷，导致 Activity 销毁后资源不释放。
- **优化**:
    - 实现了 `onCleared()`，确保 `utcTimer`, `hamRecorder`, `ft8SignalListener`, `httpServer` 在 ViewModel 销毁时停止。
    - 统一通过 `ViewModelProvider` 获取实例，确保数据同步。
    - 移除了强制进程退出的 `System.exit(0)`，改为 `finishAndRemoveTask()` 实现优雅关机。

### 1.2 Context 泄漏防护
- **问题**: 单例类持有静态 Context。
- **优化**:
    - `GeneralVariables`, `DatabaseOpr`, `CallsignDatabase`, `OperationBand` 全部强制使用 `getApplicationContext()`。

---

## 2. 功耗与发热优化 (CPU/Battery)

### 2.1 集中式线程池管理 (AppExecutors)
- **问题**: 之前项目中散布着大量的 `Executors.newCachedThreadPool()`，导致线程数量不可控。
- **优化**:
    - 引入 `AppExecutors` 全局管理类。
    - 磁盘 IO 使用单线程顺序执行，解码任务使用核心数受限的固定线程池，网络任务独立管理。

### 2.2 UtcTimer 计时器重构
- **问题**: 10ms 高频轮询极度消耗 CPU。
- **优化**:
    - 移除了 10ms 轮询逻辑。
    - 使用 `ScheduledExecutorService` 根据当前 UTC 时间计算下一次 FT8 周期的精确延时，实现准时触发。

### 2.3 录音与绘制节流 (Throttling)
- **问题**: 录音循环频繁分配内存，瀑布图绘制频率过高。
- **优化**:
    - `MicRecorder` 使用可重用 float 缓冲区，大幅减少 GC 压力。
    - `WaterfallView` 引入 100ms 绘制间隔限制（约 10 FPS），平衡视觉流畅度与功耗，避免在数据变化缓慢时重绘。

---

## 3. 数据结构与 API 现代化

### 3.1 替代 AsyncTask
- **问题**: `AsyncTask` 已弃用。
- **优化**:
    - 全部替换为 `AppExecutors.diskIO()` + `mainHandler.post()` 模式。

### 3.2 高效容器
- **问题**: `HashMap<Integer, ...>` 在 Android 上效率较低。
- **优化**:
    - 关键位置（如分区映射）改为使用 `SparseIntArray`，减少对象装箱开销。

### 3.3 交互增强
- **功能**: 增加了电台连接/断开、正在发射信号、通联成功（QSO Success）的弹出气泡提示，改善用户体验。

### 3.4 电台型号列表修复
- **功能**: 补全了遗漏的 `YAESU FT-710` 电台型号，并新增了 `YAESU FTX-1` 支持（采用与 FT-710 一致的指令集 6 和 38400 波特率）。

## 4. 优化清单 (Final Checklist)

- [x] `AppExecutors` 全局线程池集成。
- [x] `WaterfallView` 双缓冲机制与绘制节流（限制至 5-10 FPS）。
- [x] 移除 `System.exit(0)`，实现优雅退出。
- [x] `HashMap` 优化为 `SparseIntArray`。
- [x] 全局 `Handler` 规范化（显式指定 Looper）。
- [x] 权限请求迁移到 `ActivityResultLauncher`。
- [x] `DatabaseOpr` 与 `CallsignDatabase` 全面异步化。
- [x] 补全 `YAESU FT-710` 与 `FTX-1` 电台型号支持。
- [x] 修复 `strings.xml` 中的 XML 解析告警。
- [x] 优化 Gradle 构建性能（增加堆内存，切换至 ParallelGC）。
