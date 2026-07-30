# FT8CN 编译、签名与回退说明

## 版本规则

版本唯一来源是 `ft8cn/gradle.properties`：

```properties
ft8cn.versionName=0.93.005
ft8cn.versionCode=93005
```

格式必须是 `主版本.次版本.三位构建号`。当前决定发布线为 `0.93.005` / `93005`，对应 tag `v0.93.005`。历史 tag `v0.93.004` 已存在，禁止覆盖、重打或用同名 Release 资产替换它。

正式 APK 文件名为：

```text
FT8CN-v0.93.005-release-<短commit>.apk
```

本机和 CI 的 TEST/BETA APK 文件名为：

```text
FT8CN-v0.93.005-beta-<短commit>.apk
```

TEST/BETA 使用 debug 证书和包名 `com.bg7yoz.ft8cn.beta`，桌面名称为“FT8CN测试版”；正式版使用 `com.bg7yoz.ft8cn` 和正式证书。两者可并存，数据目录也按包名隔离。TEST/BETA 不是正式 Release，不能作为正式签名或正式发布产物。

## 本机构建

在 `ft8cn/` 目录执行：

```powershell
.\gradlew :app:packageTestApk
python ..\scripts\verify_apk_signature.py --apk .artifacts\FT8CN-v0.93.005-beta-<短commit>.apk --expect debug
```

Gradle 会把 debug APK 复制到 `ft8cn/.artifacts/`，该目录已被 Git 忽略。实际短 commit 以 Gradle 输出的路径为准。

## 正式签名

正式构建只接受下面四个配置项，环境变量优先于 `ft8cn/keystore.properties`：

```text
FT8CN_RELEASE_STORE_FILE
FT8CN_RELEASE_STORE_PASSWORD
FT8CN_RELEASE_KEY_ALIAS
FT8CN_RELEASE_KEY_PASSWORD
```

本机可以从 `ft8cn/keystore.properties.example` 复制模板到未跟踪的 `ft8cn/keystore.properties`，也可以只设置环境变量。正式构建还会检查 keystore 文件真实存在；缺少任一项或文件不存在会在 Gradle 配置阶段失败，绝不会回退到 debug 证书。

PowerShell 示例只展示变量名和占位符，不要把真实密码写入仓库、日志或 Issue：

```powershell
$env:FT8CN_RELEASE_STORE_FILE = "release-keystore.jks"
$env:FT8CN_RELEASE_STORE_PASSWORD = "<SECRET>"
$env:FT8CN_RELEASE_KEY_ALIAS = "<KEY_ALIAS>"
$env:FT8CN_RELEASE_KEY_PASSWORD = "<SECRET>"
.\gradlew :app:verifyReleaseSigning
.\gradlew :app:assembleRelease
python ..\scripts\verify_apk_signature.py --apk app\build\outputs\apk\release\app-release.apk --expect release
```

GitHub Actions 还需要以下变量名对应的 Secrets：

```text
FT8CN_RELEASE_STORE_FILE
FT8CN_RELEASE_STORE_PASSWORD
FT8CN_RELEASE_KEY_ALIAS
FT8CN_RELEASE_KEY_PASSWORD
FT8CN_RELEASE_KEYSTORE_B64
```

`FT8CN_RELEASE_KEYSTORE_B64` 仅用于 CI 临时还原 keystore；workflow 会拒绝绝对路径、父目录路径和空文件。文档、workflow 和日志只引用变量名，不包含任何 Secret 值。

## 发布门禁与敏感信息检查

提交或建 tag 前，从仓库根目录执行：

```powershell
python scripts\check_release_contract.py
git ls-files "*.apk" "*.jks" "*.keystore" "ft8cn/keystore.properties"
git grep -n -I -E "BEGIN (RSA )?PRIVATE KEY|^(storePassword|keyPassword)=" -- . ":!ft8cn/keystore.properties.example"
```

第一条会检查三位构建号、`versionCode` 对应关系、JDK 17、真实 Release notes 路径、release 不使用 debug 证书以及 Git 中不能有 APK/keystore。后两条用于人工复核敏感信息；有输出时先移除并检查历史，不要继续发布。

证书检查命令：

```powershell
$env:APKSIGNER = "<ANDROID_SDK>\build-tools\<VERSION>\apksigner.bat"
python scripts\verify_apk_signature.py --apk ft8cn\.artifacts\FT8CN-v0.93.005-beta-<短commit>.apk --expect debug
python scripts\verify_apk_signature.py --apk ft8cn\app\build\outputs\apk\release\app-release.apk --expect release
```

TEST/BETA 必须显示 `CN=Android Debug`；正式 APK 若显示 Android Debug，门禁失败。正式证书的 SHA-256 指纹应与维护者保存的正式证书指纹人工比对，不能只看文件名。

## GitHub Actions

- 普通 CI 使用 JDK 17，构建并上传明确命名的 TEST/BETA APK，不需要正式 Secrets。
- tag workflow 使用 JDK 17，并在构建前检查 tag 与 Gradle 版本完全一致。
- 正式 workflow 缺少任一正式签名变量、`FT8CN_RELEASE_KEYSTORE_B64` 或还原后的 keystore 时立即失败。
- Release notes 使用仓库实际存在的 `doc/RELEASES.md`；文件不存在或同名 GitHub Release 已存在时失败。
- workflow 不使用 `--clobber`，不覆盖已有资产；本任务不创建、不推送 tag，也不把 APK 二进制加入 Git。

## Android 回退与并存边界

Android 使用 `versionCode` 防止同包安装较低版本。正式包 `com.bg7yoz.ft8cn` 从 `93005` 回退到已安装的 `93004` 时，普通安装会被系统拒绝；回退测试应先卸载正式包（会清除该包数据），或在已授权的 adb 测试设备上使用：

```powershell
adb -s <serial> uninstall com.bg7yoz.ft8cn
adb -s <serial> install <旧正式APK>
```

如果必须保留数据，可在调试/测试设备尝试：

```powershell
adb -s <serial> install -r -d <旧正式APK>
```

安装命令中的 `-d` 是 adb 的“允许 version code 降级”选项，但不是所有设备策略、安装器或正式签名场景都保证接受；失败时仍需卸载后安装。`adb -d` 另有“选择 USB 真机”的含义，因此这里使用 `-s <serial>` 明确设备。卸载会删除应用数据，先按需要备份 QSO/配置。正式版和 BETA 由于包名不同，不是同包 downgrade：可以同时安装、分别升级/卸载；BETA 的 debug 签名不能升级正式包，正式签名也不能把 BETA 当成正式包更新。

官方依据：[Android Debug Bridge install options](https://developer.android.com/tools/adb#install)，[Version your app](https://developer.android.com/studio/publish/versioning)。
