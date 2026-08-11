# FT8CN
由 BG7YOZ 开发，N0BOY 托管

在 Android 上原生运行 FT8

## 近期重大更新与项目状态

当前分支包含多次重要的维护与稳定性更新（正式历史版本至 `0.93.004`，当前发布准备版本为 `0.93.005`），重点提升了 Android 14 兼容性、系统资源占用和高负载挂机的稳定性。

### 目前项目进度与状态

- 🟢 **核心架构升级 (v0.93.001)**：已完成 Android 14 兼容性适配，引入 `AppExecutors` 统一管理后台线程，完成了对全局生命周期的收敛清理，优化了瀑布图渲染（双缓冲与重绘限帧）和音频缓冲复用，大幅降低了运行功耗与发热。
- 🟢 **稳定性深度修复 (v0.93.002 - v0.93.003)**：
  - **核心解码/忙等修复**：彻底解决了网络模式下发射后解码卡死（引入时隙超时强制结算补零）、UDP 发包忙等自旋烧 CPU 以及瀑布图 bitmap 回收竞争闪退问题。
  - **权限与闪退防护**：修复了在全新安装或未授予录音、蓝牙运行时权限（`BLUETOOTH_CONNECT`）时，启动录音前台服务和收到蓝牙/SCO广播导致 App 闪退的严重缺陷。
  - **DXCC 恢复与测试落地**：恢复了被误改的 DXCC 呼号前缀最长匹配算法，保证归属地显示正确；并首次落地的 Instrumented 自动化测试，确保数据库底层逻辑的稳定性。
  - **测试版共存 (.beta)**：支持构建带有独立的包名 `com.bg7yoz.ft8cn.beta` 和名称 “FT8CN测试版” 的测试包，可与正式版无缝共存，方便挂机测试。
- 🟢 **云同步扩展与构建发布 (v0.93.004)**：新增了 **Wavelog 日志同步** 支持，合并了 Cloudlog/Wavelog 的日志上传逻辑并支持连接免 Dummy QSO 验证；优化了 GitHub Actions 工作流，直接构建并发布 Release 版 APK。
- 🟡 **v0.93.005 / 16KB native 集成候选**：开发 SHA `9fbda6f` 已接入重建 native 源码、严格 oracle 和四 ABI/16KB artifact gate；独立测试报告 16KB native 自动化 GO、P0=0，API 37/16KB AVD 启动成功，connected 63 pass/1 intentional skip。4KB AVD、真机/HIL、完整 QSO、长时挂机、功耗和温升仍待验证。
- 🟡 **下一 beta**：远端 beta.1 至 beta.7 已占用，当前只准备不可变候选 `v0.93.005-beta.8`（`93008`）；实际 beta-only 签名必须由 GitHub Actions 完成，正式 Release 仍因缺受保护 formal 签名材料而阻断。

详细的维护文档、发布历史和技术分析请查阅：
- 📂 [维护文档目录索引 (doc/README.md)](doc/README.md)
- 📄 [项目已完成改动与变更历史 (doc/RELEASES.md)](doc/RELEASES.md)
- 📄 [性能与内存优化指南 (doc/OPTIMIZATION_GUIDE.md)](doc/OPTIMIZATION_GUIDE.md)

版本号采用 `主版本.次版本.构建号` 格式，从 `0.93.001` 开始。同一基础版本的后续发布，每次只将三位构建号加 1，例如 `0.93.002`、`0.93.003`，以此类推。

## 发布操作规范

普通分支推送只同步代码，不会自动创建 GitHub Release，也不会上传 APK：

```powershell
git push origin release
```

正式发布版本时，需要在推送代码后创建并推送版本 tag。tag 名称必须和应用版本号一致，并以 `v` 开头：

```powershell
git tag -a v0.93.005 -m "FT8CN v0.93.005"
git push origin v0.93.005
```

推送 `v*.*.*` 格式的正式 tag 后会进入 GitHub Actions formal 发布门禁；在用户确认签名迁移、提供长期 keystore 和可信证书前，formal workflow 会阻断，不创建 Release。beta tag 使用独立 beta-only workflow；`v0.93.005-beta.8` 目前只准备 notes 和契约，未创建 tag/Release，必须先通过 CI 实际签名和所有 native 门禁。

请前往 [Releases](https://github.com/iCassius/FT8CN/releases) 下载最新 APK 文件。

```
免责声明：
   FT8CN旨在研究的目的，学习如何对FT8信号进行解码、发射等操作，不对使用者操作本APP所产生的后果负责。
   在中华人民共和国境内，使用FT8CN请遵守《中华人民共和国无线电管理条例》等相关规定。
   考虑到手机的性能和续航的限制，对信号的处理采用轻量化的运算，未做深度解码等处理。
   如有好的建议或问题可以提交到到”有问题要吐槽“。

BG7YOZ
2022-07-01

致敬：
   Steve Franke(K9AN)、Bill Somerville(G4WJS)、Joe Taylor(K1JT)，提出FT8和FT4协议（FT是Franke和Taylor的首字母），并在论文《The FT4 and FT8 Communication Protocols》详细介绍了FT4和FT8的设计初衷和在WSJT-X中的具体实现细节，成为完成本APP的根本指南。
   Karlis Goba(YL3JG)在代码的具体实现上提供了参考。
鸣谢：
   BG7YOY，在FT8CN开发阶段为我在无线电基本理论上作出指导，并为FT8CN设计了图标
   BG4IGX，在我刚刚入门业余无线电时为我在具体实践上作出指导。抖音上您可以搜到很多他的教学视频
   BD7MXN，帮助我对部分电台的连接控制做了一些测试，并提出改进建议
   BH2RSJ，帮助我建立了一个FT8CN测试群，为测试和后续改进提出了很多宝贵意见
   BH7ACO，帮助解决了某电台的驱动和相关的配置参数
   BG7IKK，帮助解决了只支持通过RTS控制PTT发射的电台的测试
   BI1NIZ，帮助注册账号，用于收集问题反馈和FAQ的功能
   BD3OOX以及石家庄业余无线电俱乐部，FT8CN的呼号地区归属数据提取至JTDX石家庄版，使呼号定位可以精确到中国的省级
   VR2UPU(BD7MJO)，在FT8的开发和使用经验上提供指导，并在多语言方面给予帮助
   BA2BI，在业余无线电的基础知识和通联的日志处理方面上给予帮助和指导
   BI3QXJ，在对某品牌系列电台的指令集上给予专业性的指导
   BG6TQD，在对某型号电台的指令集测试上给予帮助
   BG5CSS，提供某型号电台用于测试
   BG7YXN，提供某型号电台用于测试
   BG7YRB，对呼号规则运算提供帮助
   BG8KAH，提供设备用于测试
   BA7LVG、JE6WUD，完成日文的翻译校对工作
   BG6RI，帮助解决日志的信号报告问题
   SV1EEX，完成希腊文、西班牙文UI的翻译工作
   VR2VRC，帮助修正历史呼号读取规则
   BA7NQ，提供设备用于测试
   BD7MYM，对某型号的电台测试给予指导
   NØBOY，帮助提供Github源，以及翻译工作
   BG5JNT，帮助修正非标准呼号的识别问题
   BH3NEK，协助对某型号电台进行测试
   BG2ALB，协助对某型号电台进行测试
   BG6DRU，协助对某型号电台进行测试
   BG7NQF，提供某型号电台的隐藏指令，对一些设备做兼容性测试
   BH2VSQ，协助对某型号电台进行测试
   BG7YBW，协助对部分功能进行测试
   BH1RNN，协助对部分功能进行测试
   BG7BSM，协助对一些BUG进行调试
   BH4FTI，发现并协助对一些BUG进行调试
   BG8BXM（M哥），为FT8CN的使用做推广，抖音和B站上有很多他的教学视频
   BA7MFQ，为FT8CN的使用做推广，帮助测试
   BG2EFX，提供大数据量的日志用于测试
   DS1UFX，贡献(tr)uSDX audio over CAT代码
   BG8HT，提供某型号电台进行测试
   UB6LUM，帮助解决某型号电台的操作模式设置
   BG5VLI，贡献向Cloudlog和QRZ自动上传日志的代码
```
