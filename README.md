# HyperDay

一个基于 [Miuix](https://github.com/miuix-kotlin-ui/miuix)（HyperOS 设计语言）组件库打造的 Android 倒数日应用，拥有 HyperOS 风格的界面、液态玻璃材质、莫奈动态取色与官方动态混色背景。

## 功能

- **倒数日管理** — 记录距未来某天还有多久、某天已过去多久，支持标题、备注、起始日期，删除前二次确认
- **事件详情页** — 中央大卡片展示天数与起始日，可分享文本、保存程序化绘制的分享长图
- **自定义背景** — 纯色 / 相册壁纸（自动模糊），壁纸模糊度、遮罩浓度、卡片模糊度可自由调节，逐事件持久化，卡片文字与按钮颜色随壁纸明暗自适应
- **主题风格** — 外观模式三卡预览（自动切换 / 浅色模式 / 深色模式）、应用风格切换（经典 / 液态玻璃）
- **液态玻璃** — 基于 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（backdrop）的玻璃卡片与悬浮底栏，底栏玻璃球支持拖拽、按压缩放与折射高亮（Android 13+）
- **莫奈取色** — 跟随系统壁纸动态取色，支持自定义种子色（官方 7 色）与调色风格（9 种）
- **磨砂玻璃材质** — 卡片与操作按钮的 glass blur 效果（Android 13+），顶部渐变模糊标题栏
- **动态混色背景** — 关于页移植自 Miuix 官方 example 的 AGSL 着色器动态混色效果
- **官方导航转场** — miuix-nav 的 MiuixDefault 转场 + 预测性返回 + 边缘滑动返回
- **Miuix 交互细节** — 过滚动回弹、滚动到底震动反馈、列表增删位移动画

## 技术栈

| 项目 | 说明 |
| --- | --- |
| UI 框架 | [Miuix](https://github.com/miuix-kotlin-ui/miuix) v0.9.4-rc01（HyperOS Design） |
| 液态玻璃 | [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) backdrop 2.0.1（Apache-2.0） |
| 动态取色 | [MaterialKolor](https://github.com/materialkolor/materialkolor)（经 Miuix 传递依赖） |
| 语言 | Kotlin 2.4.0 |
| 构建 | Gradle 9.4.1 + AGP 9.2.1 |
| 最低支持 | Android 7.0（API 24），液态玻璃 / 磨砂模糊等材质效果需 Android 13+（API 33） |

## 构建

```bash
git clone https://github.com/ChaYeWuu/HyperDay.git
cd HyperDay
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 21+、Android SDK（compileSdk 37）。

## 目录结构

```
app/src/main/kotlin/com/chayewuu/hypermatter/
├── MainActivity.kt          # 入口、导航（miuix-nav）与主页双 Tab
├── data/
│   ├── CountdownEvent.kt    # 事件数据模型（kotlinx.serialization）
│   ├── DateUtils.kt         # epochDay 日期计算
│   ├── EventStore.kt        # SharedPreferences + JSON 持久化
│   ├── EventViewModel.kt    # 事件 ViewModel
│   └── SettingsStore.kt     # 外观 / 应用风格 / 莫奈取色设置
└── ui/
    ├── HomePage.kt          # 首页事件列表
    ├── AddEventBottomSheet.kt
    ├── EventDetailPage.kt   # 详情页（自定义背景 / 玻璃材质）
    ├── SettingsPage.kt      # 设置页
    ├── ThemePage.kt         # 主题风格页（外观三卡 / 应用风格 / 莫奈取色）
    ├── AboutPage.kt         # 关于页（动态混色背景）
    ├── BlurBar.kt           # 官方顶部渐变模糊封装
    ├── effect/              # 官方动态混色背景移植（AGSL，7 文件）
    └── glass/               # 液态玻璃（backdrop 封装 + 官方底栏交互移植）
        ├── LiquidGlass.kt          # LocalGlass* / liquidGlass / LiquidGlassCard / LiquidGlassTabBar
        ├── DampedDragAnimation.kt  # 官方阻尼拖拽 + 按压缩放动画
        ├── InteractiveHighlight.kt # 官方跟手径向光晕
        └── DragGestures.kt         # 官方手势探测器
```

## 致谢

- [Miuix](https://github.com/miuix-kotlin-ui/miuix) — HyperOS 风格 Compose 组件库（Apache-2.0），动态混色背景与磨砂玻璃效果移植自其官方 example
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — 液态玻璃效果库 backdrop（Apache-2.0），可拖拽折射底栏移植自其官方 catalog 示例
- [MaterialKolor](https://github.com/materialkolor/materialkolor) — Material You 动态取色（Miuix 莫奈取色传递依赖）
- [Jetpack Compose](https://developer.android.com/compose) — Android 声明式 UI 框架

## 许可

本项目仅供学习交流。所依赖的开源项目分别遵循其各自的开源许可（Miuix、AndroidLiquidGlass 均为 Apache-2.0）。
