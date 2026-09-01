# HyperDay

一个基于 [Miuix](https://github.com/miuix-kotlin-ui/miuix)（HyperOS 设计语言）组件库打造的 Android 倒数日应用，拥有 HyperOS 风格的界面、液态玻璃材质、莫奈动态取色与官方动态混色背景。

## 功能

- **倒数日管理** — 记录距未来某天还有多久、某天已过去多久，支持标题、备注、起始日期，长按卡片编辑 / 删除，删除前二次确认
- **重复倒数日** — 每天 / 每周 / 每月 / 每年 / 每年农历，日期与时间可调，内置 14 个节假日一键添加（春节、中秋、国庆……）
- **事件详情页** — 中央大卡片展示天数与起始日，可分享文本、保存程序化绘制的分享长图
- **自定义背景** — 纯色 / 相册壁纸（自动模糊），壁纸模糊度、遮罩浓度、卡片模糊度可自由调节，逐事件持久化，卡片文字与按钮颜色随壁纸明暗自适应
- **字体设置** — 逐事件定制字体大小、粗细、颜色（含自定义色板）、描边与阴影，全部带平滑过渡动画
- **主题风格** — 外观模式三卡预览（自动切换 / 浅色模式 / 深色模式）、应用风格切换（经典 / 液态玻璃）
- **液态玻璃** — 基于 [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)（backdrop）的玻璃卡片与悬浮底栏，底栏玻璃球支持拖拽、按压缩放与折射高亮（Android 13+）
- **莫奈取色** — 跟随系统壁纸动态取色，支持自定义种子色（官方 7 色）与调色风格（9 种）
- **磨砂玻璃材质** — 卡片与操作按钮的 glass blur 效果（Android 13+），顶部渐变模糊标题栏
- **动态混色背景** — 关于页移植自 Miuix 官方 example 的 AGSL 着色器动态混色效果；纯色模式下的分享长图背景也是它的静态定格
- **备份与导入** — 一键导出 / 导入自有备份格式，同时支持导入官方倒数日的 .idmbaks 加密备份文件
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

## 更新日志

### v1.0.0

新增 倒数日管理，距未来还有多少天、已过去多少天一目了然
新增 重复倒数日，支持每天、每周、每月、每年、每年农历自动重复
新增 节假日一键添加，春节、中秋、国庆等 14 个节日无需手动输入日期
新增 事件详情页，中央大卡片展示天数，支持分享与存为图片
新增 自定义背景，相册壁纸自动模糊，模糊度、遮罩浓度、卡片模糊度均可自由调节
新增 字体设置，每个事件可单独定制字体大小、粗细、颜色、描边与阴影
新增 应用风格切换，经典与液态玻璃两种视觉风格
新增 液态玻璃底栏，玻璃球支持拖拽、按住折射挤压，还原官方手感
新增 莫奈取色，跟随系统壁纸动态取色，支持自定义种子色与调色风格
新增 备份与导入，支持自有备份格式与官方倒数日 .idmbaks 备份文件
新增 关于页动态混色背景，完整移植官方 OS3 着色器效果
优化 详情页背景切换与字体调节均带平滑过渡动画
优化 保存的分享长图背景为官方混色效果定格，文字颜色随明暗自适应
优化 顶部渐变模糊标题栏，内容滚动到栏下自动磨砂
优化 列表增删带位移动画，滚动到底有震动反馈
优化 状态栏颜色跟随应用主题，深色模式不再刺眼
修复 部分场景下二级页面返回直接退出应用的问题
修复 部分场景下弹层点击后需要退出重进才生效的问题
修复 部分场景下配置壁纸后进入详情页闪白屏的问题

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
