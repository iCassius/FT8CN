# FT8CN
由 BG7YOZ 开发，N0BOY 托管

在 Android 上原生运行 FT8

## 近期重大更新

当前分支包含一次重要维护更新，重点提升 Android 14 兼容性、降低 CPU/电池消耗，并改善长时间运行稳定性。主要内容包括：使用 `AppExecutors` 集中管理后台任务，完善定时器、录音、网络服务等生命周期清理，降低瀑布图重绘压力，复用音频缓冲区，移除强制进程退出，改进数据库/呼号查询的异步处理，更新 Gradle 构建配置，并新增 YAESU FT-710 与 FTX-1 电台型号支持。

发布概要和技术说明请查看 [RELEASES.md](RELEASES.md) 与 [ft8cn/OPTIMIZATION_GUIDE.md](ft8cn/OPTIMIZATION_GUIDE.md)。

版本号采用 `主版本.次版本.构建号` 格式，从 `0.93.001` 开始。同一基础版本的后续发布，每次只将三位构建号加 1，例如 `0.93.002`、`0.93.003`，以此类推。

## 发布操作规范

普通分支推送只同步代码，不会自动创建 GitHub Release，也不会上传 APK：

```powershell
git push origin release
```

正式发布版本时，需要在推送代码后创建并推送版本 tag。tag 名称必须和应用版本号一致，并以 `v` 开头：

```powershell
git tag -a v0.93.001 -m "FT8CN v0.93.001"
git push origin v0.93.001
```

推送 `v*.*.*` 格式的 tag 后，GitHub Actions 会自动构建 APK，并上传到对应的 GitHub Release。后续版本按构建号递增，例如 `v0.93.002`、`v0.93.003`。

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
