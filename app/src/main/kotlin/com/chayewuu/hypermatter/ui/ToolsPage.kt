package com.chayewuu.hypermatter.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 底部「小工具」tab — 工具集合页占位（参考 HyperIntervals ToolsScreen 布局：
 * SmallTitle 分组 + 卡片内 ArrowPreference 工具行）。
 *
 * 目前尚无具体工具，仅渲染占位入口；后续工具在此追加分组卡片。
 * 渲染于 MainTabs pager 内：模糊顶栏与玻璃宿主由 MainTabs 提供。
 */
@Composable
fun ToolsPage(
    contentPadding: PaddingValues,
    onOpenDeerTracker: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .overScrollVertical()
            .scrollEndHaptic(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item {
            SmallTitle(text = "工具列表")
            LiquidGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            ) {
                ArrowPreference(
                    title = "🦌🦌记录器",
                    summary = "日历打卡，记录坚持的每一天",
                    onClick = onOpenDeerTracker,
                )
                ArrowPreference(
                    title = "更多工具即将到来",
                    summary = "更多小工具正在开发中，敬请期待",
                    onClick = {},
                )
            }
            Text(
                text = "这里将提供与倒数日搭配的实用小工具",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 8.dp),
            )
        }

        item {
            Spacer(Modifier.height(12.dp))
        }
    }
}
