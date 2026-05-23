# AGP 9.2.1 升级适配说明

## 问题描述
在升级 Android Gradle Plugin (AGP) 到 9.2.1（及 9.0.0+）时，`android.defaults.buildfeatures.buildconfig` 属性已被移除。如果 `gradle.properties` 中仍然存在该属性且未设置为 `false`，升级助手将无法继续。

## 修复步骤
1. **修改 `gradle.properties`**:
   移除了以下行：
   ```properties
   android.defaults.buildfeatures.buildconfig=true
   ```

2. **修改 `app/build.gradle`**:
   由于项目使用了 `buildConfigField`，需要在 `android` 块中显式启用 `buildConfig` 特性：
   ```gradle
   android {
       ...
       buildFeatures {
           buildConfig true
       }
       ...
   }
   ```

## 注意事项
AGP 8.0 之后，`BuildConfig` 默认不再自动生成。通过在每个模块的 `build.gradle` 中设置 `buildFeatures { buildConfig true }` 可以恢复该功能。
