# FT8CN 优先级系统与稳定性升级计划

## 目标
实现 JTDX 风格的通联优先级颜色提醒，并修复数据库重构导致的启动闪退。

## 已实施的更改

### 核心逻辑
#### [Ft8Message.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/Ft8Message.java)
- 定义 `Priority` 级别：`NEW_DXCC`, `NEW_BAND`, `NEW_PREFIX`, `NEW_CALLSIGN`, `RARE_DX`。

#### [GeneralVariables.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/GeneralVariables.java)
- 增加线程安全的会话统计容器：`sessionDxccCount`, `sessionPrefixCount`, `workedBandsByDxcc` 等。

#### [CallsignDatabase.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/callsign/CallsignDatabase.java)
- 实现优先级计算算法，结合经纬度计算距离。

### 数据库修复
#### [DatabaseOpr.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/database/DatabaseOpr.java)
- 修复 `SQLiteException: no such column: callsign`。
- 修改查询指向 `QslCallsigns` 表。

### UI 呈现
#### [CallingListAdapter.java](file:///C:/Users/cassi/Documents/Project/Github/FT8CN/ft8cn/app/src/main/java/com/bg7yoz/ft8cn/ui/CallingListAdapter.java)
- 实现 `applyPriorityColors` 方法，根据优先级覆盖默认行背景。
- 自动适配文字反差色。

---
## 验证计划
### 自动化验证
- 运行 `gradle :app:assembleDebug` 确保编译通过。
### 手动验证
- 启动应用，检查 Logcat 是否仍有数据库错误。
- 观察解码列表，验证不同颜色的色块是否正确显示。
- 检查“呼叫”列表是否包含高优先级但未发 CQ 的电台。
