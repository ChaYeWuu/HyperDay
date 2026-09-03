package com.chayewuu.hypermatter.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.data.EventCategory
import com.chayewuu.hypermatter.reminder.ReminderScheduler
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.LiquidGlassCard
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.rememberBlurBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalCategoryStore
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 分类管理: built-in categories (rename only) and custom categories
 * (add / rename / delete). Deleting a category keeps events untouched —
 * they simply render as uncategorized.
 */
@Composable
fun CategoryPage(onBack: () -> Unit) {
    val categoryStore = LocalCategoryStore.current
    val viewModel = LocalEventViewModel.current
    val events by viewModel.events.collectAsState()
    val categories by categoryStore.categories.collectAsState()
    val context = LocalContext.current
    val view = LocalView.current
    val barBackdrop = rememberBlurBackdrop()
    val glassBackdrop = rememberGlassBackdrop()

    var renameTarget by remember { mutableStateOf<EventCategory?>(null) }
    var renameText by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<EventCategory?>(null) }

    fun countOf(categoryId: String) = events.count { it.category == categoryId }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(barBackdrop) {
                SmallTopAppBar(
                    title = "分类管理",
                    color = if (barBackdrop != null) Color.Transparent
                    else MiuixTheme.colorScheme.surface,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (barBackdrop != null)
                        Modifier.layerBackdrop(barBackdrop)
                    else Modifier
                ),
        ) {
            // Flat-canvas recorder for the glass cards: a sibling with no
            // glass inside it (glass surfaces must never be part of the
            // subtree recording their own sample — infinite render nesting).
            GlassCanvasRecorder(glassBackdrop)
            CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
                LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 24.dp,
                ),
            ) {
                item {
                    SmallTitle(text = "内置分类")
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        categories.filter { it.builtIn }.forEach { category ->
                            CategoryRow(
                                name = category.name,
                                count = countOf(category.id),
                                onClick = {
                                    renameTarget = category
                                    renameText = category.name
                                },
                            )
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(12.dp))
                    SmallTitle(text = "自定义分类")
                    LiquidGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    ) {
                        val customs = categories.filterNot { it.builtIn }
                        if (customs.isEmpty()) {
                            Text(
                                text = "还没有自定义分类，点击下方添加",
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                style = MiuixTheme.textStyles.body2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                            )
                        }
                        customs.forEach { category ->
                            CategoryRow(
                                name = category.name,
                                count = countOf(category.id),
                                onClick = {
                                    renameTarget = category
                                    renameText = category.name
                                },
                                onDelete = { deleteTarget = category },
                            )
                        }
                        // Add row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    addText = ""
                                    showAdd = true
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Add,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "添加分类",
                                color = MiuixTheme.colorScheme.primary,
                                style = MiuixTheme.textStyles.body1,
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "点击分类名可重命名；删除分类不会删除其中的倒数日",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 8.dp),
                    )
                }
                }
            }
        }

        // Dialogs must stay INSIDE this Scaffold's content lambda: a Scaffold
        // provides LocalDialogStates (rendered by its own MiuixPopupHost) only
        // to its slots. Outside the Scaffold the CompositionLocal falls back to
        // an unrendered default list — the dialog would silently never show.
        // (Same reason EventDetailPage/AboutPage keep their dialogs inside
        // with renderInRootScaffold = false.)

        // Add dialog
        if (showAdd) {
            OverlayDialog(
                title = "添加分类",
                show = true,
                onDismissRequest = { showAdd = false },
                renderInRootScaffold = false,
            ) {
                Column {
                    TextField(
                        value = addText,
                        onValueChange = { addText = it },
                        label = "分类名称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showAdd = false },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "添加",
                            onClick = {
                                if (addText.isNotBlank()) {
                                    categoryStore.add(addText)
                                    runCatching { ReminderScheduler.reschedule(context) }
                                }
                                showAdd = false
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Rename dialog
        renameTarget?.let { target ->
            OverlayDialog(
                title = "重命名分类",
                show = true,
                onDismissRequest = { renameTarget = null },
                renderInRootScaffold = false,
            ) {
                Column {
                    TextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = "分类名称",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { renameTarget = null },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = "保存",
                            onClick = {
                                if (renameText.isNotBlank()) {
                                    categoryStore.rename(target.id, renameText)
                                    runCatching { ReminderScheduler.reschedule(context) }
                                }
                                renameTarget = null
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Delete confirmation
        deleteTarget?.let { target ->
            OverlayDialog(
                title = "删除分类",
                summary = "确定要删除「${target.name}」吗？其中的倒数日会保留，只是变为未分类。",
                show = true,
                onDismissRequest = { deleteTarget = null },
                renderInRootScaffold = false,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { deleteTarget = null },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = "删除",
                        onClick = {
                            categoryStore.delete(target.id)
                            runCatching { ReminderScheduler.reschedule(context) }
                            deleteTarget = null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One category entry: name + event count, optional trailing delete icon. */
@Composable
private fun CategoryRow(
    name: String,
    count: Int,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count 个事件",
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            style = MiuixTheme.textStyles.body2,
        )
        if (onDelete != null) {
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = MiuixIcons.Delete,
                contentDescription = "删除分类",
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete),
            )
        }
    }
}
