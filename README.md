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
- **桌面小部件** — 卡片 / 列表 / 极简三款倒数日小部件，外加 🦌🦌记录器状态（2×1）与本月记录一览（2×2），点击直达对应页面，卡片支持绑定指定倒数日
- **分类系统** — 内置纪念日 / 生日 / 生活 / 工作，支持自定义分类、重命名与首页分类筛选
- **日程提醒** — 当天或提前 1–3 天提醒，可按分组与倒数日自由选择，错过时刻当天补发
- **小米超级岛** — 提醒以 HyperOS 超级岛样式弹出（需 [Shizuku](https://github.com/RikkaApps/Shizuku) 授权）
- **实时动态通知** — Android 16 持续通知样式，最后 12 小时秒表倒计时
- **同步系统日历** — 自由勾选要同步的倒数日，重复事件按周期写入本地日历，支持自动同步（数据变动后自动更新）与一键移除
- **从系统日历导入** — 把系统日历中的日程勾选导入为倒数日，带来源标记、重复导入自动更新
- **生日年龄与生肖** — 「生日」分类自动按年重复，卡片与详情页展示周岁与生肖（按春节切换）
- **应用内在线更新** — 检查新版本、下载进度与安装一条龙，GitHub / Gitee 双下载源自动回退
- **🦌🦌记录器** — 长按日期打卡的戒断记录器，月历视图与戒断统计（已戒天数 / 最长戒断 / 破戒次数）
- **远程公告** — 启动时拉取云端公告，重要通知直达所有用户
- **小工具页** — 底部独立工具集合页，更多工具持续加入

## 技术栈

| 项目 | 说明 |
| --- | --- |
| UI 框架 | [Miuix](https://github.com/compose-miuix-ui/miuix) v0.9.4-rc01（HyperOS Design） |
| 液态玻璃 | [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) backdrop 2.0.1（Apache-2.0） |
| 动态取色 | [MaterialKolor](https://github.com/jordond/materialkolor)（经 Miuix 传递依赖） |
| 超级岛支持 | [Shizuku](https://github.com/RikkaApps/Shizuku) v13.1.5（dev.rikka.shizuku，超级岛提醒的特权服务） |
| 语言 | Kotlin 2.4.0 + kotlinx.serialization |
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

### v1.3.0

- 新增 🦌🦌记录器桌面小部件两款：2×1 状态条（已戒天数 / 今日状态 / 最长纪录）与 2×2 本月记录一览（破戒日高亮、今天描边、本月破戒次数），点击直达记录器
- 新增 「生日」内置分类：选择后按年自动重复，首页卡片与详情页展示周岁与生肖（生肖按春节切换，用内置农历表换算）
- 新增 同步系统日历支持「自动同步」开关：倒数日新增 / 修改 / 删除后自动重建系统日历，无需手动点同步
- 新增 「从系统日历导入」：读取系统日历中的日程（自动跳过 HyperDay 自己的镜像日历），自由勾选后导入为倒数日，重复日程保留重复规则
- 优化 导入的系统日程带来源标记，重复导入时原地更新而非重复添加，且保留卡片外观自定义
- 优化 🦌🦌记录器记录后立即刷新桌面小部件

### v1.2.0

- 新增 同步系统日历（自由勾选要同步的倒数日，重复事件按周期写入，支持一键移除）
- 新增 应用内在线更新（GitHub / Gitee 双下载源自动回退，含软件版本页、更新日志与下载进度）
- 新增 底部「小工具」页面与首个工具：🦌🦌记录器（长按打卡 + 戒断统计）
- 新增 远程公告弹窗，重要通知直达所有用户
- 新增 危险操作统一确认弹窗，确认按钮统一品牌蓝
- 优化 列表小部件 (4×2) 改为 HyperOS 官方双行布局，事件名不再被截断
- 优化 纪念日分类默认按每年重复，无需手动设置
- 修复 生日等重复事件同步到系统日历时日期变成添加日的问题
- 修复 同步的日历在系统日历应用中不显示的问题
- 修复 列表小部件第三行日期被裁剪半截的问题

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
│   ├── CategoryStore.kt     # 分类数据（内置 + 自定义）
│   ├── ReminderStore.kt     # 提醒开关与选择集
│   ├── SettingsStore.kt     # 外观 / 应用风格 / 莫奈取色设置
│   ├── BackupManager.kt    # 备份导出与导入（含官方 .idmbaks 解析）
│   └── LunarCalendar.kt     # 1900–2100 农历换算（重复事件用）
├── reminder/                # 日程提醒链（AlarmManager 精确闹钟 + 错过补发）
│   ├── ReminderScheduler.kt # 闹钟账本与全量重排
│   ├── ReminderReceiver.kt  # 提醒触发（岛 / 实时动态 / 普通通知）
│   ├── IslandNotifier.kt    # 小米超级岛通知（Shizuku XMSF 旁路）
│   ├── LiveUpdateNotifier.kt # Android 16 实时动态（持续通知）
│   └── FocusNotification.kt # 超级岛 param_v2 JSON 构建
├── shizuku/                 # 特权服务（AIDL + UserService 绑定）
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
- [Shizuku](https://github.com/RikkaApps/Shizuku) — 特权 UserService 框架（Apache-2.0），小米超级岛提醒的 XMSF 网络旁路基于其实现
- [NexioSchedule](https://github.com/HaoZai000/NexioSchedule) — 小米超级岛与 Android 16 实时动态通知的实现参考
- [HyperIntervals](https://www.coolapk.com/u/2292343) — 酷安作者 ShallowY_ 的作品，软件版本页、工具页与更新流程的设计参考
- [Jetpack Compose](https://developer.android.com/compose) — Android 声明式 UI 框架

## 许可

本项目基于 [MIT License](LICENSE) 开源，可自由使用、修改与再分发（请保留版权声明）。

所依赖的开源项目分别遵循其各自的开源许可（Miuix、AndroidLiquidGlass、Shizuku 均为 Apache-2.0）。
