package com.chayewuu.hypermatter.ui.data

/**
 * Local version history shown on the update page when the app is already
 * up to date (and as the in-page reference of what shipped when).
 *
 * Lines follow the HyperOS changelog style: a "新增 / 优化 / 修复" prefix
 * followed by a short sentence; the update page parses the prefix into a
 * colored tag. Plain lines (no prefix) render as regular text.
 */
data class ChangelogEntry(
    val version: String,
    val date: String,
    val changes: List<String>,
)

val changelogData = listOf(
    ChangelogEntry(
        version = "v1.3.0 (4)",
        date = "2026-09-17",
        changes = listOf(
            "新增 🦌🦌记录器桌面小部件，2×1 状态条和 2×2 本月一览，点击直达记录器",
            "新增 月历小部件每格显示日期，破戒日显示 🦌",
            "新增 生日分类，按年倒数到下一岁，显示年龄和生肖",
            "新增 系统日历自动同步，倒数日变动后自动更新",
            "新增 从系统日历导入日程，自由勾选导入为倒数日",
            "优化 小部件预览页补齐五款，记录器小部件也能预览",
            "优化 月历小部件填满格子，5 周月份不再底部留空",
            "优化 小部件点击直达记录器，记录后立即刷新",
            "修复 导入的系统日程重复添加",
        ),
    ),
    ChangelogEntry(
        version = "v1.2.0 (3)",
        date = "2026-09-08",
        changes = listOf(
            "新增 同步系统日历，可自由勾选要同步的倒数日，重复事件按周期写入",
            "新增 在线更新，支持 GitHub 与 Gitee 双下载源自动回退，含软件版本页与下载进度",
            "新增 底部新增「小工具」页面与首个工具：🦌🦌记录器",
            "新增 🦌🦌记录器：长按日期打卡，自动统计已戒天数与最长戒断",
            "新增 远程公告弹窗，重要通知直达所有用户",
            "新增 危险操作增加确认提醒，全局确认按钮调整为品牌蓝色",
            "优化 列表小部件 (4×2) 改为官方双行布局，事件名不再被截断",
            "优化 纪念日分类自动按每年重复，无需手动设置",
            "修复 生日等重复事件同步到系统日历时日期变成添加日的问题",
            "修复 同步的日历在系统日历应用中不显示的问题",
            "修复 列表小部件第三行日期被裁剪半截的问题",
        ),
    ),
    ChangelogEntry(
        version = "v1.1.0 (2)",
        date = "2026-09-03",
        changes = listOf(
            "新增 事件分类系统，支持自定义分类与筛选",
            "新增 日程提醒、超级岛通知与实时动态通知",
            "优化 关于页版本号动态读取，作者主页支持酷安跳转",
        ),
    ),
    ChangelogEntry(
        version = "v1.0.0 (1)",
        date = "2026-09-01",
        changes = listOf(
            "新增 倒数日记录与提醒，支持农历与多种重复周期",
            "新增 三款桌面小部件：卡片、列表与极简样式",
            "新增 事件详情卡片自定义背景与字体样式，支持生成分享长图",
            "新增 主题风格与莫奈取色，深浅色跟随系统",
        ),
    ),
)
