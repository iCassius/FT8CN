# FT8CN UI 现代化升级需求与变更文档

## 1. 项目背景与目标
当前 FT8CN 的界面使用了较多的自定义样式（如渐变背景、自定义边框等），在现代化 Android 系统上略显陈旧，且兼容性与操作一致性有待提升。本次升级的目标是：
- **原生简洁风格**：全面转向 Material Design 3 (M3) 设计语言。
- **现代化控件替换**：使用 Material Components 替换目前的自定义弹出框、气泡提示等。
- **操作逻辑统一**：统一输入框、下拉菜单、单选框的操作逻辑，并提供快速切换功能。
- **增强易用性**：优化悬浮按钮（FAB）的 UI 和交互，使其更符合现代操作习惯。
- **兼容性保障**：确保在不同系统版本和设备上拥有稳定且一致的显示效果。

## 2. 核心变更内容 (TODO List)

### 2.1 基础样式与主题升级
- [ ] 引入 Material 3 依赖库（如果尚未引入）。
- [ ] 更新 `themes.xml`，定义 M3 配色方案（Primary, Secondary, Surface 等）。
- [ ] 逐步移除 `drawable/editor_style.xml` 等自定义渐变背景，改用 M3 标准容器样式。

### 2.2 弹出窗口与提示 (Dialogs & Tooltips)
- [ ] **对话框升级**：将所有的 `Dialog` 或自定义样式的弹窗替换为 `MaterialAlertDialogBuilder`。
    - 涉及：`FilterDialog`, `FreqDialog`, `HelpDialog`, `SetVolumeDialog`, `SelectBluetoothDialog` 等。
- [ ] **提示信息升级**：
    - 将部分自定义的 `ToastMessage` 或控制台风格的提示改为 `Snackbar`。
    - 引入 `Material Tooltip` 用于图标按钮的功能提示。

### 2.3 输入与选择控件 (Input & Selectors)
- [ ] **输入框**：将 `EditText` 替换为 `TextInputLayout` + `TextInputEditText`，支持错误提示和辅助文本。
- [ ] **下拉菜单**：将 `Spinner` 替换为 `Exposed Dropdown Menu` (TextInputLayout + AutoCompleteTextView)，提供更好的视觉反馈。
- [ ] **单选/多选**：统一使用 `MaterialRadioButton` 和 `MaterialCheckBox`。
- [ ] **快速切换功能**：在常用设置（如频段、功率等）的输入/选择控件旁增加“快速切换”小部件或预设值气泡。

### 2.4 悬浮按钮 (FAB)
- [ ] **UI 现代化**：使用 `ExtendedFloatingActionButton` 或 M3 风格的 FAB。
- [ ] **交互优化**：支持长按菜单或点击展开更多快捷操作，优化在小屏幕上的避让逻辑。

### 2.5 布局优化
- [ ] 优化 `ConfigFragment` 的长列表展示，使用 `MaterialCardView` 进行分组。
- [ ] 统一各页面的内边距（Padding）和间距（Margin），符合 M3 的 8dp 网格规范。

## 3. 修改边界与功能保护说明
为了确保 UI 升级不破坏已有功能，必须遵循以下原则：

- **数据绑定保持**：在修改 XML 布局时，保留原有的 `android:id` 和 `dataBinding` 变量，确保 `MainActivity` 和 `MainViewModel` 中的逻辑无需大规模重写。
- **业务逻辑隔离**：UI 升级仅限于视图层（XML 和 UI 类中的样式设置），严禁修改 `connector`, `ft8transmit`, `ft8listener` 等核心解码与通信逻辑。
- **逐步替换**：采用组件化替换策略，先升级通用的对话框和基础控件，验证稳定后再进行复杂页面的整体重排。
- **横屏适配**：FT8CN 有大量的横屏使用场景，所有 UI 变更必须同时在 `layout` 和 `layout-land` 下进行适配，确保不会出现布局重叠或溢出。

## 4. 预览设计说明 (Textual Previews)

为了直观展示升级效果，以下通过对比表格说明关键 UI 组件的变更：

| 组件类型 | 升级前 (Current) | 升级后 (Modern M3) |
| :--- | :--- | :--- |
| **对话框 (Dialog)** | 自定义 XML 布局，锐角边框，渐变背景 | `MaterialAlertDialogBuilder`，大圆角 (28dp)，纯色 Surface 背景 |
| **输入框 (Input)** | 扁平 `EditText`，带有 1dp 细线边框 | `TextInputLayout` + Outlined 边框，支持 Floating Label |
| **下拉菜单 (Spinner)** | 传统 `Spinner` 样式，弹出列表不统一 | `Exposed Dropdown Menu`，与输入框风格统一，可搜索 |
| **悬浮按钮 (FAB)** | 固定尺寸，简单阴影 | `Extended FAB`，支持图标+文字，滚动时可收缩为圆形图标 |
| **颜色体系** | 硬编码颜色（如紫色 500） | 遵循 M3 调色板，支持动态配色 (Material You) |

### 快速切换功能示意：
在“默认频率”输入框下方，将新增一排 `Filter Chips`，列出常用频段（如 7.074, 14.074 等），点击即可瞬间填充并生效。

