package com.chayewuu.hypermatter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chayewuu.hypermatter.data.EventStore
import com.chayewuu.hypermatter.data.EventViewModel
import com.chayewuu.hypermatter.data.SettingsStore
import com.chayewuu.hypermatter.ui.AboutPage
import com.chayewuu.hypermatter.ui.AddEventBottomSheet
import com.chayewuu.hypermatter.ui.BlurredBar
import com.chayewuu.hypermatter.ui.EventDetailPage
import com.chayewuu.hypermatter.ui.HomePage
import com.chayewuu.hypermatter.ui.SettingsPage
import com.chayewuu.hypermatter.ui.ThemePage
import com.chayewuu.hypermatter.ui.prewarmWallpaperThumbs
import com.chayewuu.hypermatter.ui.rememberBlurBackdrop
import com.chayewuu.hypermatter.ui.glass.GlassCanvasRecorder
import com.chayewuu.hypermatter.ui.glass.GlassNavAction
import com.chayewuu.hypermatter.ui.glass.LiquidGlassTabBar
import com.chayewuu.hypermatter.ui.glass.LocalGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.LocalGlassEnabled
import com.chayewuu.hypermatter.ui.glass.rememberContentGlassBackdrop
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import com.chayewuu.hypermatter.ui.theme.MiuixAppTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidLayerBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

class MainActivity : ComponentActivity() {

    /** Event deep-link target set by the home-screen widgets. */
    private val pendingEventId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(0, 0),
            navigationBarStyle = SystemBarStyle.auto(0, 0),
        )
        pendingEventId.value = intent?.getStringExtra(EXTRA_EVENT_ID)

        val eventStore = EventStore(this)
        val settingsStore = SettingsStore(this)
        val eventViewModel = EventViewModel(eventStore)

        setContent {
            val colorMode by settingsStore.colorMode.collectAsState()
            val colorModeValue = colorMode
            val monetColor by settingsStore.monetColor.collectAsState()
            val monetColorValue = monetColor
            val monetPaletteStyle by settingsStore.monetPaletteStyle.collectAsState()
            val monetPaletteStyleValue = monetPaletteStyle
            val monetSeedColor by settingsStore.monetSeedColor.collectAsState()
            val monetSeedColorValue = monetSeedColor

            // Status / navigation bar icons must follow the APP theme (the
            // colorMode setting), not the system theme: auto() in onCreate
            // follows the system dark mode, so a forced light app theme on
            // a dark system would get invisible light icons. Re-apply
            // edge-to-edge with the matching style whenever the app theme
            // flips.
            val appDark = when (colorMode) {
                2 -> true
                1 -> false
                else -> isSystemInDarkTheme()
            }
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (appDark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0),
                    navigationBarStyle = if (appDark) SystemBarStyle.dark(0) else SystemBarStyle.light(0, 0),
                )
            }

            MiuixAppTheme(
                colorMode = colorModeValue,
                monetColor = monetColorValue,
                monetPaletteStyle = monetPaletteStyleValue,
                monetSeedColor = monetSeedColorValue,
            ) {
                CompositionLocalProvider(
                    LocalEventViewModel provides eventViewModel,
                    LocalSettingsStore provides settingsStore,
                ) {
                    App(pendingEventId)
                }
            }
        }
    }

    /** Warm-start deep link from widgets: singleTask relaunches via onNewIntent. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingEventId.value = intent.getStringExtra(EXTRA_EVENT_ID)
    }

    companion object {
        /** Widget PendingIntents use this extra to deep-link an event detail page. */
        const val EXTRA_EVENT_ID = "hyperday.extra.EVENT_ID"
    }
}

/** Serializable route keys for the miuix-nav back stack. */
@Serializable
private sealed interface Route : NavKey {
    @Serializable
    data object Main : Route

    @Serializable
    data object About : Route

    @Serializable
    data object Theme : Route

    @Serializable
    data class EventDetail(val id: String) : Route
}

/**
 * System-adaptive corner radius for page transitions: prefer the device's
 * actual display corner radius (WindowInsets RoundedCorner, API 31+), then
 * the system dialog corner radius (config_dialogCornerRadius), falling back
 * to 18.dp when neither is available.
 */
@Composable
private fun rememberSystemCornerRadius(): Dp {
    val view = LocalView.current
    return remember(view) {
        var radiusPx = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            radiusPx = view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
                ?.radius ?: 0
        }
        if (radiusPx <= 0) {
            radiusPx = try {
                val field = Class.forName("com.android.internal.R\$dimen")
                    .getField("config_dialogCornerRadius")
                view.resources.getDimensionPixelSize(field.getInt(null))
            } catch (_: Exception) {
                0
            }
        }
        if (radiusPx > 0) {
            (radiusPx / view.resources.displayMetrics.density).dp
        } else {
            18.dp
        }
    }
}

@Composable
private fun App(pendingEventId: MutableState<String?>) {
    // Liquid Glass app style: enabled by the settings switch and only when
    // the device supports RenderEffect (API 31+); below that every glass
    // component silently falls back to its classic Miuix counterpart.
    val settingsStore = LocalSettingsStore.current
    val appStyle by settingsStore.appStyle.collectAsState()
    val glassEnabled =
        appStyle == SettingsStore.STYLE_LIQUID_GLASS && isRenderEffectSupported()

    // Prewarm tiny wallpaper thumbnails on the IO dispatcher so entering a
    // detail page composes the (blurred) wallpaper on its very first frame.
    val eventViewModel = LocalEventViewModel.current
    val events by eventViewModel.events.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(events) {
        prewarmWallpaperThumbs(context, events.mapNotNull { it.wallpaperUri })
    }

    val backStack = rememberNavBackStack<Route>(Route.Main)
    val systemCorner = rememberSystemCornerRadius()

    // Widget deep link: push the event detail page once, then consume.
    LaunchedEffect(pendingEventId.value) {
        val eventId = pendingEventId.value
        if (eventId != null) {
            backStack.add(Route.EventDetail(eventId))
            pendingEventId.value = null
        }
    }

    CompositionLocalProvider(LocalGlassEnabled provides glassEnabled) {
        // Official Miuix navigation: NavDisplay + NavTransitions.MiuixDefault —
        // the detail page slides in full-width from the trailing (right) edge,
        // the covered main page parallaxes a quarter width toward the leading
        // edge with a light alpha falloff, and both programmatic pops and the
        // predictive back gesture run on the same depth driver. The detail route
        // also opts into the edge swipe-to-dismiss (finger left-to-right), and
        // the effects layer adds the official slide-style 0.5 dim scrim.
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            transition = NavTransitions.MiuixDefault,
            effects = NavDisplayEffects(
                // Official iOS-style corner clip: the entering page gets
                // rounded corners while it slides in from the edge. Radius
                // follows the system: the device's display corner radius
                // when available, else the system dialog corner radius.
                cornerClipRadius = systemCorner,
                dimAmount = 0.5f,
            ),
        ) {
            entry<Route.Main> {
                MainTabs(
                    onOpenEvent = { backStack.add(Route.EventDetail(it)) },
                    onOpenAbout = { backStack.add(Route.About) },
                    onOpenTheme = { backStack.add(Route.Theme) },
                )
            }
            entry<Route.About>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                AboutPage(onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.Theme>(swipeDismiss = NavSwipeDirection.LeftToRight) {
                ThemePage(onBack = { backStack.removeLastOrNull() })
            }
            entry<Route.EventDetail>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
                EventDetailPage(
                    eventId = route.id,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        }
    }
}

@Composable
private fun MainTabs(
    onOpenEvent: (String) -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTheme: () -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    val viewModel = LocalEventViewModel.current
    val view = LocalView.current

    // The add-event sheet is owned here (not inside HomePage) so that BOTH
    // triggers can open it: the classic in-page FAB and, in liquid-glass
    // mode, the + action docked next to the bottom tab bar.
    var showAddSheet by remember { mutableStateOf(false) }

    // Dark-theme flag for the liquid glass bar tint.
    val settingsStore = LocalSettingsStore.current
    val colorMode by settingsStore.colorMode.collectAsState()
    val isDarkTheme = when (colorMode) {
        2 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }

    val navItems = listOf(
        NavigationItem("首页", MiuixIcons.Home),
        NavigationItem("设置", MiuixIcons.Settings),
    )

    val titles = listOf("HyperDay", "设置")

    // Page canvas: `surface` (light: #F7F7F7 gray canvas + white cards;
    // dark: black canvas + #242424 cards). The top bar uses the official
    // progressive (gradient) blur: content scrolls under it and gets
    // frosted. On API < 33 it falls back to a solid surface-colored bar.
    val backdrop = rememberBlurBackdrop()
    // Liquid-glass: the cards/FAB sample the flat canvas (recorded by a
    // sibling recorder — glass surfaces must never sit inside the subtree
    // recording their own sample, or the render tree nests infinitely);
    // the bottom bar samples the live content (it lives outside the
    // recorded subtree, in the bottomBar slot).
    val glassCanvasBackdrop = rememberGlassBackdrop()
    val glassNavBackdrop = rememberContentGlassBackdrop()

    val navBarContent: @Composable RowScope.() -> Unit = {
        navItems.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = pagerState.currentPage == index,
                onClick = {
                    if (pagerState.currentPage != index) {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    }
                },
                icon = item.icon,
                label = item.label,
            )
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            BlurredBar(backdrop) {
                SmallTopAppBar(
                    title = titles[pagerState.currentPage],
                    color = if (backdrop != null)
                        Color.Transparent
                    else
                        MiuixTheme.colorScheme.surface,
                )
            }
        },
        bottomBar = {
            if (glassNavBackdrop != null) {
                // Official-style liquid glass tab bar: floating capsule +
                // refracting selection pill sliding under the active tab.
                // The + add action is docked to the right of the capsule —
                // primary-tinted glass — replacing the classic in-page FAB.
                // It only shows on the home page; on Settings it collapses
                // away and the capsule returns to full width.
                LiquidGlassTabBar(
                    backdrop = glassNavBackdrop,
                    tabs = navItems,
                    selected = pagerState.currentPage,
                    onSelect = { index ->
                        if (pagerState.currentPage != index) {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    },
                    isDarkTheme = isDarkTheme,
                    action = {
                        GlassNavAction(
                            icon = MiuixIcons.Add,
                            backdrop = glassNavBackdrop,
                            isDarkTheme = isDarkTheme,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showAddSheet = true
                            },
                        )
                    },
                    actionVisible = pagerState.currentPage == 0,
                )
            } else {
                NavigationBar(content = navBarContent)
            }
        },
    ) { paddingValues ->
        // Record the pager content into the backdrops so the blurred top bar
        // and the glass nav bar can sample whatever scrolls beneath them.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .then(
                    if (glassNavBackdrop != null)
                        Modifier.liquidLayerBackdrop(glassNavBackdrop)
                    else
                        Modifier
                ),
        ) {
            // Flat-canvas recorder for the glass cards/FAB: a sibling with no
            // glass inside, keeping the render tree acyclic.
            GlassCanvasRecorder(glassCanvasBackdrop)
            CompositionLocalProvider(LocalGlassBackdrop provides glassCanvasBackdrop) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (page) {
                        0 -> HomePage(
                            contentPadding = paddingValues,
                            onOpenEvent = onOpenEvent,
                            onAddClick = { showAddSheet = true },
                            // In glass mode the + lives in the bottom bar.
                            showFab = glassNavBackdrop == null,
                        )
                        1 -> SettingsPage(
                            contentPadding = paddingValues,
                            onOpenAbout = onOpenAbout,
                            onOpenTheme = onOpenTheme,
                        )
                    }
                }
            }

            // Add-event sheet: opened from the classic FAB or the glass
            // bottom-bar + action. It must live INSIDE the Scaffold content
            // so the overlay can render into the scaffold's popup host —
            // outside the Scaffold it would simply never appear.
            if (showAddSheet) {
                AddEventBottomSheet(
                    show = showAddSheet,
                    onDismiss = { showAddSheet = false },
                    onConfirm = { title, epochDay, note, repeatType, lunarMonth, lunarDay,
                                  repeatWeekday, repeatMonthDay, repeatYearMonth, timeHour, timeMinute ->
                        viewModel.addEvent(
                            title, epochDay, note, repeatType, lunarMonth, lunarDay,
                            repeatWeekday, repeatMonthDay, repeatYearMonth, timeHour, timeMinute,
                        )
                        showAddSheet = false
                    },
                )
            }
        }
    }
}
