# HyperDay

一个基于 [Miuix](https://github.com/compose-miuix-ui/miuix)（HyperOS 设计语言）组件库打造的 Android 倒数日应用，拥有 HyperOS 风格的界面、液态玻璃材质、莫奈动态取色与官方动态混色背景。

## 功能

- **倒数日管理** — 记录距未来某天还有多久、某天已过去多久，支持标题、备注、起始日期，长按卡片编辑 / 删除，删除前二次确认
- **重复倒数日** — 每天 / 每周 / 每月 / 每年 / 每年农历，日期与时间可调，内置 14 个节假日一键添加（春节、中秋、国庆……）
- **事件详情页** — 中央大卡片展示天数与起始日，一键分享程序化绘制的分享长图
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
| UI 框架 | [Miuix](https://github.com/compose-miuix-ui/miuix) v0.9.4-rc01（HyperOS Design） |
| 液态玻璃 | [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) backdrop 2.0.1（Apache-2.0） |
| 动态取色 | [MaterialKolor](https://github.com/jordond/materialkolor)（经 Miuix 传递依赖） |
| 语言 | Kotlin 2.4.0 |
| 构建 | Gradle 9.4.1 + AGP 9.2.1 |
| 最低支持 | Android 7.0（API 24），液态玻璃需 Android 12+（API 31），磨砂模糊等效果需 Android 13+（API 33） |

## 构建

```bash
git clone https://github.com/ChaYeWuu/HyperDay.git
cd HyperDay
./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

要求：JDK 21+、Android SDK（compileSdk 37）。

## 更新日志

### v1.1.0

- 新增 桌面小部件（卡片 / 列表 / 极简三款，点击直达事件，卡片支持绑定事件）
- 新增 分类系统（内置纪念日 / 生活 / 工作，支持自定义分类与重命名）
- 新增 首页分类筛选与事件分类标签
- 新增 日程提醒（当天 / 提前 1–3 天，可按分组与倒数日自由选择）
- 新增 小米超级岛弹窗提醒（需 Shizuku 授权）
- 新增 实时动态通知（Android 16 持续通知，最后 12 小时秒表倒计时）
- 新增 超级岛与实时动态独立设置页
- 新增 天数点击换算为年月天
- 新增 错过提醒时刻的当天补发
- 优化 实时动态通知每日同步剩余天数
- 优化 提醒设置入口拆分与文案精简
- 修复 实时动态通知发出后被误删的问题
- 修复 延迟送达的提醒闹钟误发未选中事件的问题
- 修复 分类管理添加分类无效的问题

### v1.0.0

- 新增 倒数日管理
- 新增 重复倒数日
- 新增 节假日一键添加
- 新增 事件详情页
- 新增 自定义背景
- 新增 字体设置
- 新增 应用风格切换
- 新增 液态玻璃底栏
- 新增 莫奈取色
- 新增 备份与导入
- 新增 关于页动态混色背景
- 优化 详情页切换动画
- 优化 分享长图背景
- 优化 分享直接分享渲染的长图
- 优化 顶部渐变模糊标题栏
- 优化 列表滚动体验
- 优化 状态栏跟随主题
- 优化 应用图标显示大小
- 优化 页面转场圆角跟随系统
- 修复 二级页面返回退出应用的问题
- 修复 弹层点击需退出重进才生效的问题
- 修复 配置壁纸后进入详情页闪白屏的问题
- 修复 公历节假日添加后日期显示为今天的问题
- 修复 主题风格卡片边框圆角显示异常的问题
- 修复 输入法弹出时表单布局挤压的问题

## 目录结构

```
app/src/main/kotlin/com/chayewuu/hypermatter/
├── MainActivity.kt          # 入口、导航（miuix-nav）与主页双 Tab
├── data/
│   ├── CountdownEvent.kt    # 事件数据模型（kotlinx.serialization）
│   ├── DateUtils.kt         # epochDay 日期计算
│   ├── EventStore.kt        # SharedPreferences + JSON 持久化
│   ├── EventViewModel.kt    # 事件 ViewModel
│   ├── SettingsStore.kt     # 外观 / 应用风格 / 莫奈取色设置
│   ├── BackupManager.kt    # 备份导出与导入（含官方 .idmbaks 解析）
│   └── LunarCalendar.kt     # 1900–2100 农历换算（重复事件用）
└── ui/
    ├── HomePage.kt          # 首页事件列表
    ├── AddEventBottomSheet.kt
    ├── EventDetailPage.kt   # 详情页（自定义背景 / 玻璃材质 / 字体设置）
    ├── FontSettings.kt      # 详情页字体样式渲染（大小 / 粗细 / 描边 / 阴影，带动画）
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

- [Miuix](https://github.com/compose-miuix-ui/miuix) — HyperOS 风格 Compose 组件库（Apache-2.0），动态混色背景与磨砂玻璃效果移植自其官方 example
- [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — 液态玻璃效果库 backdrop（Apache-2.0），可拖拽折射底栏移植自其官方 catalog 示例
- [MaterialKolor](https://github.com/jordond/materialkolor) — Material You 动态取色（Miuix 莫奈取色传递依赖）
- [Jetpack Compose](https://developer.android.com/compose) — Android 声明式 UI 框架

## 许可

本项目仅供学习交流。所依赖的开源项目分别遵循其各自的开源许可（Miuix、AndroidLiquidGlass 均为 Apache-2.0）。
