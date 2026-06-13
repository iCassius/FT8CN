# 已知问题

本文件记录了在当前高危稳定性修复中被有意延后的 lint 问题。

## AndroidManifest 文件导入 intent-filter 警告

- **警告类型**：`AppLinkUrlError`
- **位置**：`ft8cn/app/src/main/AndroidManifest.xml`
- **当前状态**：已知问题，本轮未作修复。
- **影响范围**：仅影响静态代码分析（lint），该 `VIEW` 类型的 intent-filter 用于处理本地文件导入的 `file:` 与 `content:` 协议 URI。这不是网页版的 App Link 流程。运行期实际影响预计极低，但在消除该警告或调整结构前需仔细确认 Manifest 配置。
- **修复难度**：低到中。最安全的修复方式是拆分或使用注解标记该本地文件导入的 intent-filter，而不改变其原有的 `.txt` 与 `.adi` 文件导入逻辑。

## 缺失的翻译

- **警告类型**：`MissingTranslation`
- **涉及字符串**：`sync_time`, `syncing`, `qsl_success`, `transmitting_msg`
- **缺失的语言区域**：`el` (希腊语), `ja` (日语), `es` (西班牙语)
- **当前状态**：已知问题，本轮未作修复。
- **影响范围**：希腊语、日语和西班牙语的用户可能会看到这些字符串对应的英文回退文本。不会导致运行期闪退。
- **修复难度**：低。直接在受影响的语言资源文件中补全翻译，或者若有意回退为英文，则将这些字符串标记为 `translatable="false"`。
