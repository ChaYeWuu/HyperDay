# CLAUDE.md

HyperDay 是一款 HyperOS 风格的倒数日（Days Matter）Android 应用，基于 Miuix 组件库（v0.9.4-rc01）+ Jetpack Compose 构建。包名 `com.chayewuu.hypermatter`，仓库 https://github.com/ChaYeWuu/HyperDay.git。

## 构建与安装

```bash
# Debug 构建（workdir = 项目根）
cmd /c "gradlew.bat assembleDebug"
# 产物：app\build\outputs\apk\debug\app-debug.apk

# Release 构建（读取根目录 keystore.properties，文件缺失时自动跳过签名配置）
cmd /c "gradlew.bat assembleRelease"
# 产物：app\build\outputs\apk\release\app-release.apk（约 2.2MB，R8 已开启）

# 安装到目标设备并启动
adb -s GIV49969EQZ9TK4H install -r app\build\outputs\apk\debug\app-debug.apk
adb -s GIV49969EQZ9TK4H shell am start -n com.chayewuu.hypermatter/.MainActivity

# 崩溃排查（先清旧日志再复现）
adb -s GIV49969EQZ9TK4H logcat -c
adb -s GIV49969EQZ9TK4H logcat -d -b crash
```

- 测试设备：`GIV49969EQZ9TK4H`（Android 17 / API 37）。85bdb880 不是目标设备。
- **分工约定**：应用内 UI 测试由用户手动完成；agent 只负责代码、构建、安装（adb install）、崩溃日志排查。不做 input tap / 截图等 UI 自动化。
- git push 必须用 `git -c http.proxy= -c https.proxy= push origin main`（全局代理指向已失效的 127.0.0.1:7897）。

## 构建配置关键事实（勿随意改动）

- **AGP 9.2.1 内置 Kotlin**：plugins 块**不声明** `org.jetbrains.kotlin.android`（会报 Cannot add extension 'kotlin'）。只用 `android.application` + `kotlin.compose` + `kotlin.serialization`。
- **`gradle.properties` 里 `kotlin.version=2.4.0`**：覆盖 AGP 内置 Kotlin 2.2.0，否则读不了 Miuix 的 Kotlin 2.4.0 metadata。
- **绝不能引入 compose-bom**：会把 Compose 降到 1.7.6，Miuix 弹层（OverlayDialog/OverlayBottomSheet）运行时直接崩溃（No NavigationEventDispatcher）。Compose 版本由 miuix-ui 传递依赖决定，并显式依赖 `androidx.navigationevent:navigationevent-compose:1.1.2`。
- compileSdk 37 / minSdk 24 / targetSdk 36；JVM target 21（miuix-nav 是 JVM 21 字节码）。
- miuix-blur 声明 minSdk 33：Manifest 用 `tools:overrideLibrary` 越过 lint，运行时必须用 `isRuntimeShaderSupported()` / `isRenderEffectSupported()` 守卫降级。
- R8：`buildTypes.release` 开了 `optimization { enable = true }`（需 gradle.properties 的 `android.r8.gradual.support=true`），keep 规则在 `app/proguard-rules.pro`（kotlinx-serialization）。
- Release 签名：根目录 `keystore.properties` + `keystore.jks`（均已在 .gitignore）。

## 目录结构

```
app/src/main/kotlin/com/chayewuu/hypermatter/
├── MainActivity.kt          # App() 入口：miuix-nav NavDisplay 路由（Main/About/Theme/EventDetail），
│                            #   MainTabs：Scaffold + BlurredBar 顶栏 + LiquidGlassTabBar/NavigationBar 底栏
│                            #   + HorizontalPager(首页/设置)。MiuixAppTheme 响应 colorMode + monet。
├── data/
│   ├── CountdownEvent.kt    # @Serializable 事件模型：epochDay(Long) 日期 + repeat*(重复) + lunar*(农历)
│   │                        #   + wallpaperUri/壁纸滑杆 + font*(字体设置) 全部可空字段
│   ├── DateUtils.kt         # 天数计算/effectiveDate(重复事件下次日期)/repeatLabel
│   ├── LunarCalendar.kt     # 1900-2100 农历转换（位压缩表）
│   ├── EventStore.kt        # SharedPreferences + kotlinx.serialization JSON 持久化，StateFlow 暴露
│   ├── EventViewModel.kt    # events StateFlow + add/delete/update/importEvents/clearAll
│   ├── SettingsStore.kt     # colorMode(0/1/2) + appStyle(0经典/1液态玻璃) + monet*(莫奈取色)
│   └── BackupManager.kt     # 导出 HyperDay JSON；导入双格式（自有 JSON + 官方倒数日 .idmbaks）
└── ui/
    ├── HomePage.kt          # 列表(即将到来/已过去) + EventCard(长按弹 OverlayListPopup 编辑/删除)
    ├── AddEventBottomSheet.kt # 添加/编辑表单：OverlayBottomSheet + OverlayDropdownPreference(重复/节假日)
    ├── EventDetailPage.kt   # 详情页：大卡片 + 四按钮(分享/存图/背景/字体设置)，壁纸/纯色双模式，
    │                        #   字体与背景设置对话框，renderShareCard 程序化绘制分享长图
    ├── SettingsPage.kt      # 设置：外观(主题风格入口)/数据(清除/备份/导入)/其他(关于)
    ├── ThemePage.kt         # 主题风格：外观三卡(图片预览)/应用风格 dropdown/莫奈取色+色系+调色风格
    ├── AboutPage.kt         # 关于：官方 BgEffectBackground 混色背景 + 磨砂 logo + 玻璃卡
    ├── FontSettings.kt      # FancyText 组件（字体动画：大小/粗细/颜色/描边/阴影全 200ms 动画）
    ├── BlurBar.kt           # 顶部渐变模糊栏（progressiveTextureBlur，API<33 降级纯色）
    ├── effect/              # 官方混色背景移植（AGSL RuntimeShader：BgEffectBackground/Config/Painter...）
    └── glass/               # 液态玻璃（Kyant0 backdrop 库封装：LiquidGlassCard/GlassFab/
                             #   LiquidGlassTabBar 官方三层折射底栏 + DampedDragAnimation 等）
```

## 已验证的 Miuix API 约定

- `OverlayDialog` / `OverlayBottomSheet` / `OverlayListPopup` 都在 `top.yukonga.miuix.kmp.overlay` 包（不是 basic）。
- **弹层内再弹下拉/菜单必须传 `renderInRootScaffold = false`**（否则渲染进根 Scaffold popup host，被 sheet/NavDisplay 盖住不可见）。
- 官方示例固定快照 commit `4a6b750b`（v0.9.4-rc01）；源码拉取用 codeload tarball 直连（`https://codeload.github.com/compose-miuix-ui/miuix/tar.gz/4a6b750b...`，raw 单文件时通时断）。

## 重要的历史教训（勿重复踩坑）

1. **弹层内容 stale bug**：Miuix 弹层渲染在 Scaffold popup host 的独立子组合，外部捕获的普通值不随页面重组传入——弹层内容 lambda **内部**要直读 State（`val liveEvents by viewModel.events.collectAsState()` 再取值），不要用外部缓存的 event/fs。
2. **事件更新用 fresh 读取**：写 CountdownEvent 时从 `viewModel.events.value.firstOrNull { it.id == eventId }` 重读最新值再 copy（EventDetailPage 的 `updateEventFresh` 模式），避免连点从旧快照算出同一值。
3. **液态玻璃 layerBackdrop 不能自包含**：玻璃组件绝不能位于「录制自己的采样层」子树内（RenderNode 循环嵌套 → 原生栈溢出闪退）。backdrop 录制器必须是兄弟节点（GlassCanvasRecorder 模式）。
4. **lens 只支持 CornerBasedShape**：CircleShape 是 Oval，会抛异常——用 `RoundedCornerShape(50)`。
5. **overlay 按钮阴影 vs 裁切动画**：AnimatedVisibility 的 shrink 是矩形裁剪，任何阴影都会被切出矩形边缘——玻璃动作按钮统一 `shadow = { null }`。
6. **BottomSheet 内的返回手势**：把 `BackHandler` 写进 OverlayBottomSheet 的 content lambda 内部（注册晚于 sheet 自己的 NavigationBackHandler，优先级更高）；子页时 `allowDismiss = false`。
7. **日期一律存 epochDay: Long**（LocalDate.toEpochDay()），计算用 ChronoUnit.DAYS——避免时区歧义。
8. **持久化走 SharedPreferences + JSON**（EventStore/SettingsStore 模式），applicationContext 防泄漏。
9. **Markdown 更新日志每条前加 `- `**：纯文本行会被 GitHub 合并成一段。

## 其它约定

- 每次 UI 改动流程：改代码 → assembleDebug → adb install → am start → pidof/logcat 验证 → `git commit` + 直连 push。
- 文案全部简体中文；描述走 HyperOS 风格短句（「新增 xxx」「优化 xxx」「修复 xxx」一行一条）。
- 材料参考：Miuix 源码缓存目录（Temp 下）与 miuix skill 文档在需要时重新拉取，勿凭记忆猜 API 签名。
