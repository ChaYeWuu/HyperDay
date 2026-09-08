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
        version = "v1.2.0 (3)",
        date = "2026-09-08",
        changes = listOf(
            "新增 同步系统日历，可自由勾选要同步的倒数日，重复事件按周期写入",
            "新增 在线更新，支持 GitHub 与 Gitee 双下载源自动识别",
            "新增 底部新增「小工具」页面，更多实用工具即将到来",
            "新增 危险操作增加确认提醒，全局确认按钮调整为品牌蓝色",
            "优化 列表小部件 (4×2) 改为官方双行布局，事件名不再被截断",
            "优化 纪念日分类自动按每年重复，无需手动设置",
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
