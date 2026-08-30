package com.chayewuu.hypermatter.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chayewuu.hypermatter.data.DateUtils
import com.chayewuu.hypermatter.ui.glass.liquidGlass
import com.chayewuu.hypermatter.ui.glass.rememberGlassBackdrop
import com.chayewuu.hypermatter.ui.theme.LocalEventViewModel
import com.chayewuu.hypermatter.ui.theme.LocalSettingsStore
import com.kyant.backdrop.backdrops.LayerBackdrop as LiquidBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop as liquidLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Background
import top.yukonga.miuix.kmp.icon.extended.Image
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.ScreenCapture
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.io.FileOutputStream

/** Detail-page background modes. */
private enum class BgMode { SOLID, WALLPAPER }

/** Which tuning slider is currently being dragged (floating pill shown). */
private enum class ActiveSlider { BG_BLUR, BG_DIM, CARD_BLUR }

// Official Miuix example card-blend presets (ColorBlendToken.kt):
// frosted glass blends for the glass card and action buttons.
private val GlassBlendDark = listOf(
    BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
    BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
)

private val GlassBlendLight = listOf(
    // Neutral gray tint (same tone family as GlassBlendDark) instead of the
    // official Pured_Regular_Light blue cast (0x340034F9) — keeps the frosted
    // look without shifting the card blue.
    BlendColorEntry(Color(0x339C9C9C), BlurBlendMode.Overlay),
    BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
)

// Default tuning values for the customizable background parameters.
private const val DEFAULT_WALLPAPER_BLUR = 28f
private const val DEFAULT_WALLPAPER_DIM = 0.35f
private const val DEFAULT_CARD_BLUR = 60f

/** Decoded wallpaper plus its average luminance (drives adaptive colors). */
private data class WallpaperInfo(
    val bitmap: ImageBitmap,
    val luminance: Float,
)

/**
 * In-memory decode cache keyed by the wallpaper Uri string: revisiting a
 * detail page shows the wallpaper on the very first frame instead of
 * flashing the solid canvas while the bitmap re-decodes.
 */
private val wallpaperCache = LruCache<String, WallpaperInfo>(4)

/**
 * Full-screen detail page for a single countdown event: a central frosted
 * card (content on top, start date at the bottom) with actions below —
 * share, save as image, and customize background. Three background modes:
 * solid color, the official dynamic color-blending shader, or a custom
 * gallery wallpaper (blurred, with card/button colors adapted to the
 * wallpaper's brightness).
 */
@Composable
fun EventDetailPage(
    eventId: String,
    onBack: () -> Unit,
) {
    val viewModel = LocalEventViewModel.current
    val settingsStore = LocalSettingsStore.current
    val events by viewModel.events.collectAsState()
    val event = events.firstOrNull { it.id == eventId }

    if (event == null) {
        // Event no longer exists (e.g. cleared from settings) — leave the page.
        LaunchedEffect(eventId) { onBack() }
        return
    }

    // System back is owned by the NavDisplay navigation-event bridge: it pops
    // this entry off the back stack (with the official predictive-back
    // animation), so no BackHandler is needed here.

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showBackgroundDialog by remember { mutableStateOf(false) }

    val isPast = DateUtils.isPastEvent(event)
    val dayNum = DateUtils.dayNumber(event)
    val dateStr = DateUtils.formatDate(event.epochDay)
    val weekday = DateUtils.weekdayLabel(event.epochDay)

    val glassSupported = isRuntimeShaderSupported()
    val colorMode by settingsStore.colorMode.collectAsState()
    val isDarkTheme = when (colorMode) {
        2 -> true
        1 -> false
        else -> isSystemInDarkTheme()
    }

    // Decode the chosen wallpaper (downsampled) off the main thread, together
    // with its average luminance for adaptive text colors. A cached decode
    // (previous visit) is the produceState initial value, so the first frame
    // already renders the wallpaper.
    val wallpaper by produceState<WallpaperInfo?>(
        initialValue = event.wallpaperUri?.let { wallpaperCache.get(it) },
        key1 = event.wallpaperUri,
    ) {
        val uriStr = event.wallpaperUri ?: return@produceState
        if (wallpaperCache.get(uriStr) != null) return@produceState
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriStr)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val sample = maxOf(
                    1,
                    minOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1) / 1080,
                )
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()?.let { bmp ->
                WallpaperInfo(bmp.asImageBitmap(), avgLuminance(bmp))
            }
        }
        if (decoded != null) {
            wallpaperCache.put(uriStr, decoded)
            value = decoded
        }
    }
    // A configured wallpaper renders the wallpaper-mode shell even while
    // the bitmap is still decoding — the solid canvas must never flash
    // through (that was the white/black blink on entering the page).
    val wallpaperConfigured = event.wallpaperUri != null
    val hasWallpaper = wallpaperConfigured && wallpaper != null
    // Bright wallpapers flip the card/button text to dark for readability.
    val isLightWallpaper = (wallpaper?.luminance ?: 0f) > 0.5f

    val bgMode = when {
        wallpaperConfigured -> BgMode.WALLPAPER
        else -> BgMode.SOLID
    }

    // The frosted-glass card/buttons only make sense over a wallpaper (they
    // sample what's behind them). On the solid canvas they would blur a flat
    // color and blend into the background, so the solid mode uses the
    // standard Miuix card (white / #242424) instead.
    // In the Liquid Glass app style the sampling/rendering is upgraded from
    // Miuix textureBlur to the Kyant backdrop stack (vibrancy + blur + lens
    // refraction).
    val liquidBackdrop = rememberGlassBackdrop()
    val glassMode = glassSupported && hasWallpaper && liquidBackdrop == null

    // User-tunable parameters (persisted per event, live-adjustable in the
    // customize-background dialog).
    val bgBlur = event.wallpaperBlur?.toFloat() ?: DEFAULT_WALLPAPER_BLUR
    val bgDim = event.wallpaperDim ?: DEFAULT_WALLPAPER_DIM
    val cardBlur = event.cardBlur ?: DEFAULT_CARD_BLUR

    // The glass card/backdrop records the background layer (wallpaper or
    // shader) so the card and buttons can frost it.
    val cardBackdrop: LayerBackdrop? = if (glassMode) {
        val surfaceColor = MiuixTheme.colorScheme.surface
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }
    val glassBlend = if (isDarkTheme) GlassBlendDark else GlassBlendLight

    fun shareEvent() {
        val text = "「${event.title}」${DateUtils.describe(event)}" +
            "（${dateStr} $weekday）—— 来自 HyperDay"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享倒数日"))
        }
    }

    fun saveCardAsImage() {
        scope.launch {
            try {
                val safeTitle = event.title.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
                val fileName = "HyperDay_${safeTitle}_${System.currentTimeMillis()}"
                // Render the share card programmatically (a graphics-layer
                // capture would bake the transparent window background to
                // black in the saved PNG).
                val bitmap = withContext(Dispatchers.Default) {
                    renderShareCard(
                        title = event.title,
                        note = event.note,
                        describe = DateUtils.describe(event),
                        dayNum = dayNum,
                        dateLine = "$dateStr $weekday",
                        statusLabel = if (isPast) "已经过去" else "即将到来",
                        isPast = isPast,
                        cardArgb = event.cardColor,
                    )
                }
                val saved = withContext(Dispatchers.IO) {
                    saveBitmapToGallery(context, bitmap, fileName)
                }
                if (saved != null) {
                    Toast.makeText(context, "已保存到 $saved", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Photo picker for the custom wallpaper.
    val wallpaperPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.updateEvent(
                event.copy(wallpaperUri = uri.toString(), dynamicBg = null, cardColor = null)
            )
            showBackgroundDialog = false
        }
    }

    // Live-adjustable slider values: initialized from the event when the
    // dialog opens, previewed live while dragging, persisted on release.
    var liveBgBlur by remember { mutableFloatStateOf(bgBlur) }
    var liveBgDim by remember { mutableFloatStateOf(bgDim) }
    var liveCardBlur by remember { mutableFloatStateOf(cardBlur) }
    // While any slider thumb is held down, the customize-background dialog
    // itself sinks and fades away so the wallpaper can be observed live;
    // which slider is active is tracked so a floating progress pill can
    // keep it visible.
    var activeSlider by remember { mutableStateOf<ActiveSlider?>(null) }
    LaunchedEffect(showBackgroundDialog) {
        if (showBackgroundDialog) {
            liveBgBlur = event.wallpaperBlur?.toFloat() ?: DEFAULT_WALLPAPER_BLUR
            liveBgDim = event.wallpaperDim ?: DEFAULT_WALLPAPER_DIM
            liveCardBlur = event.cardBlur ?: DEFAULT_CARD_BLUR
            activeSlider = null
        }
    }
    // The preview follows the live slider values while the dialog is open.
    val effBgBlur = if (showBackgroundDialog) liveBgBlur else bgBlur
    val effBgDim = if (showBackgroundDialog) liveBgDim else bgDim
    val effCardBlur = if (showBackgroundDialog) liveCardBlur else cardBlur

    // ONE shared brightness decision for EVERY element drawn over the
    // wallpaper — the card content, the action-button icons/labels and the
    // back arrow: the photo's luminance after the dim scrim. They all flip
    // black/white together (separate thresholds previously made mid-bright
    // wallpapers flip the labels but not the card). Solid mode keeps the
    // theme's own colors.
    val wpLum = wallpaper?.luminance ?: 0f
    val wallpaperTextDark = hasWallpaper && wpLum * (1f - effBgDim) > 0.5f
    val overlayTextColor =
        if (wallpaperTextDark) Color(0xFF1B1B1F) else Color.White
    val onCard =
        if (wallpaperConfigured) overlayTextColor
        else MiuixTheme.colorScheme.onSurface
    val onCardSummary =
        if (wallpaperConfigured) onCard.copy(alpha = 0.78f)
        else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val accent =
        if (wallpaperConfigured) onCard
        else MiuixTheme.colorScheme.onSurface
    val pillFg = when {
        wallpaperConfigured -> onCard
        isPast -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> MiuixTheme.colorScheme.onSurface
    }
    // Elements directly on the background (back arrow, button labels) share
    // the same color in wallpaper mode; solid mode keeps the summary tone.
    val bgTextColor =
        if (wallpaperConfigured) onCard
        else MiuixTheme.colorScheme.onSurfaceVariantSummary

    val pageCanvas = MiuixTheme.colorScheme.surface
    val backdrop = rememberBlurBackdrop()
    Scaffold(
        containerColor = if (wallpaperConfigured) Color.Black else pageCanvas,
        topBar = {
            if (wallpaperConfigured) {
                // Wallpaper mode: the image fills the whole scaffold (under
                // the bar too), so the bar is plain transparent. The back
                // icon adapts to the wallpaper brightness. No title — the
                // card below already shows the event name big.
                SmallTopAppBar(
                    title = "",
                    color = Color.Transparent,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = "返回",
                                tint = bgTextColor,
                            )
                        }
                    },
                )
            } else {
                BlurredBar(backdrop) {
                    SmallTopAppBar(
                        title = "",
                        color = if (backdrop != null)
                            Color.Transparent
                        else
                            pageCanvas,
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = MiuixIcons.Back,
                                    contentDescription = "返回",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        // Liquid Glass mode: the big card becomes a refracting glass panel.
        val liquidActive = liquidBackdrop != null && hasWallpaper
        val liquidTint = if (isLightWallpaper)
            Color.Black.copy(alpha = 0.22f)
        else
            Color.White.copy(alpha = 0.18f)
        // Freshly decoded wallpaper fades in over the black shell (a cache
        // hit composes with the bitmap already present, so no fade runs).
        val wallpaperAlpha by animateFloatAsState(
            targetValue = if (hasWallpaper) 1f else 0f,
            animationSpec = tween(250),
            label = "wallpaperAlpha",
        )

        val cardContent: @Composable () -> Unit = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        when {
                            liquidActive -> Modifier.liquidGlass(
                                backdrop = liquidBackdrop!!,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = (effCardBlur * 0.35f).dp,
                                tint = liquidTint,
                            )
                            cardBackdrop != null -> Modifier.textureBlur(
                                backdrop = cardBackdrop,
                                shape = RoundedCornerShape(16.dp),
                                blurRadius = effCardBlur,
                                noiseCoefficient = BlurDefaults.NoiseCoefficient,
                                colors = BlurDefaults.blurColors(blendColors = glassBlend),
                            )
                            else -> Modifier
                        }
                    ),
                insideMargin = PaddingValues(24.dp),
                colors = if (cardBackdrop != null || liquidActive || wallpaperConfigured) {
                    // Wallpaper mode (including the brief decode-pending
                    // window) keeps the card transparent so the solid card
                    // background never flashes before the glass kicks in.
                    CardDefaults.defaultColors(Color.Transparent, Color.Transparent)
                } else {
                    CardDefaults.defaultColors()
                },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                        // Status pill: upcoming vs past
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(pillFg.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = if (isPast) "已经过去" else "即将到来",
                                color = pillFg,
                                style = MiuixTheme.textStyles.footnote1,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = event.title,
                            color = onCard,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!event.note.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = event.note,
                                color = onCardSummary,
                                style = MiuixTheme.textStyles.body2,
                            )
                        }
                        Spacer(Modifier.height(26.dp))
                        Text(
                            text = DateUtils.describe(event),
                            color = onCardSummary,
                            style = MiuixTheme.textStyles.subtitle,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                text = dayNum.toString(),
                                color = accent,
                                fontSize = 88.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "天",
                                color = accent,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(bottom = 18.dp),
                            )
                        }
                        Spacer(Modifier.height(30.dp))
                        // Hairline divider above the start date block
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.72f)
                                .height(1.dp)
                                .background(onCardSummary.copy(alpha = 0.25f)),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "起始日",
                            color = onCardSummary,
                            style = MiuixTheme.textStyles.footnote2,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "$dateStr $weekday",
                            color = onCard,
                            style = MiuixTheme.textStyles.body1,
                        )
                    }
            }
        }

        val content: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                cardContent()

                Spacer(Modifier.height(44.dp))

                // Three actions: share / save as image / customize background.
                // Labels sit directly on the page background (outside the
                // glass circles), so they adapt to the background's
                // brightness — not to the card text colors.
                val onBackgroundText = bgTextColor
                // Non-glass fallback look: wallpaper mode (incl. the brief
                // decode-pending window) uses a translucent circle so the
                // solid surfaceContainer never pops on the dark shell.
                val btnFallbackContainer = if (wallpaperConfigured)
                    Color.White.copy(alpha = 0.15f)
                else
                    MiuixTheme.colorScheme.surfaceContainer
                val btnFallbackContent = if (wallpaperConfigured)
                    onBackgroundText
                else
                    MiuixTheme.colorScheme.onSurface
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ActionButton(
                        icon = MiuixIcons.Share,
                        label = "分享",
                        onCardText = onBackgroundText,
                        glassBackdrop = cardBackdrop,
                        glassBlend = glassBlend,
                        blurRadius = effCardBlur * 0.6f,
                        liquidBackdrop = if (liquidActive) liquidBackdrop else null,
                        liquidTint = liquidTint,
                        fallbackContainer = btnFallbackContainer,
                        fallbackContent = btnFallbackContent,
                    ) { shareEvent() }
                    ActionButton(
                        icon = MiuixIcons.ScreenCapture,
                        label = "存为图片",
                        onCardText = onBackgroundText,
                        glassBackdrop = cardBackdrop,
                        glassBlend = glassBlend,
                        blurRadius = effCardBlur * 0.6f,
                        liquidBackdrop = if (liquidActive) liquidBackdrop else null,
                        liquidTint = liquidTint,
                        fallbackContainer = btnFallbackContainer,
                        fallbackContent = btnFallbackContent,
                    ) { saveCardAsImage() }
                    ActionButton(
                        icon = MiuixIcons.Background,
                        label = "自定义背景",
                        onCardText = onBackgroundText,
                        glassBackdrop = cardBackdrop,
                        glassBlend = glassBlend,
                        blurRadius = effCardBlur * 0.6f,
                        liquidBackdrop = if (liquidActive) liquidBackdrop else null,
                        liquidTint = liquidTint,
                        fallbackContainer = btnFallbackContainer,
                        fallbackContent = btnFallbackContent,
                    ) { showBackgroundDialog = true }
                }
            }
        }

        // Record everything under the top bar so the progressive blur can
        // sample it, then layer the chosen background mode.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null && !wallpaperConfigured) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            when {
                // Custom gallery wallpaper: fills the whole scaffold —
                // including behind the transparent top bar — blurred at the
                // user-chosen radius with an adjustable dark scrim. While the
                // bitmap is still decoding this branch renders the black
                // shell only (no flash of the solid canvas).
                wallpaperConfigured -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (cardBackdrop != null)
                                    Modifier.layerBackdrop(cardBackdrop)
                                else
                                    Modifier
                            )
                            .then(
                                if (liquidBackdrop != null)
                                    Modifier.liquidLayerBackdrop(liquidBackdrop)
                                else
                                    Modifier
                            ),
                    ) {
                        if (wallpaper != null) {
                            Image(
                                bitmap = wallpaper!!.bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(effBgBlur.dp)
                                    .graphicsLayer { alpha = wallpaperAlpha },
                            )
                            // Dark scrim for text readability over any photo.
                            if (effBgDim > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = wallpaperAlpha }
                                        .background(Color.Black.copy(alpha = effBgDim)),
                                )
                            }
                        }
                    }
                    content()
                }
                // Solid Miuix canvas.
                else -> content()
            }

            // Custom window-dim scrim for the customize-background dialog.
            // The dialog's built-in enableWindowDim scrim can only snap on
            // and off, so it would stay grayed while the dialog is sunk for
            // slider tuning. This one animates: it fades out together with
            // the sinking dialog and back in when it returns.
            val scrimAlpha by animateFloatAsState(
                targetValue = if (showBackgroundDialog && activeSlider == null) 1f else 0f,
                animationSpec = tween(200),
                label = "dialogScrim",
            )
            if (scrimAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = scrimAlpha }
                        .background(MiuixTheme.colorScheme.windowDimming),
                )
            }

            // Floating progress pill: while a slider thumb is held (the
            // dialog has sunk away), keep the active slider's title, value
            // and progress visible at the bottom of the screen.
            if (showBackgroundDialog && activeSlider != null) {
                val pill = when (activeSlider!!) {
                    ActiveSlider.BG_BLUR ->
                        Triple("背景模糊度", "${liveBgBlur.toInt()} dp", liveBgBlur / 50f)
                    ActiveSlider.BG_DIM ->
                        Triple("背景遮罩", "${(liveBgDim * 100).toInt()}%", liveBgDim / 0.8f)
                    ActiveSlider.CARD_BLUR ->
                        Triple("卡片模糊度", "${liveCardBlur.toInt()}", liveCardBlur / 120f)
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 32.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = pill.first,
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                        Text(
                            text = pill.second,
                            color = Color.White,
                            style = MiuixTheme.textStyles.footnote1,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    // Slim progress track mirroring the slider position.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pill.third.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                    }
                }
            }
        }

        // Customize background. IMPORTANT: this dialog lives INSIDE the
        // Scaffold content and opts out of renderInRootScaffold — by default
        // an OverlayDialog renders into the ROOT (outermost, i.e. the main
        // tabs) Scaffold, which is covered by this page, so the dialog
        // would be invisible.
        // While any slider thumb is held down, THIS dialog (not the event
        // card behind it) sinks and fades out of the way so the wallpaper
        // being tuned stays fully visible. The in-progress drag keeps working
        // (pointer events follow the pointer id, not the on-screen position).
        val dialogSink by animateFloatAsState(
            targetValue = if (activeSlider != null) 1f else 0f,
            animationSpec = tween(200),
            label = "dialogSink",
        )
        OverlayDialog(
            title = "自定义背景",
            summary = "选择背景样式，壁纸模式下卡片会自适应壁纸明暗",
            show = showBackgroundDialog,
            onDismissRequest = { showBackgroundDialog = false },
            renderInRootScaffold = false,
            modifier = Modifier.graphicsLayer {
                translationY = dialogSink * 240.dp.toPx()
                alpha = 1f - dialogSink
            },
            // Dimming is handled by the page's own animated scrim above.
            enableWindowDim = false,
        ) {
            Column {
                BgModeRow(
                    icon = MiuixIcons.Background,
                    label = "纯色背景",
                    selected = bgMode == BgMode.SOLID,
                ) {
                    viewModel.updateEvent(
                        event.copy(wallpaperUri = null, dynamicBg = null, cardColor = null)
                    )
                }
                BgModeRow(
                    icon = MiuixIcons.Image,
                    label = "自定义壁纸",
                    selected = bgMode == BgMode.WALLPAPER,
                ) {
                    wallpaperPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }

                // ---- Live tuning sliders ----
                if (bgMode == BgMode.WALLPAPER) {
                    SliderPreference(
                        title = "背景模糊度",
                        value = liveBgBlur,
                        onValueChange = {
                            activeSlider = ActiveSlider.BG_BLUR
                            liveBgBlur = it
                        },
                        valueRange = 0f..50f,
                        valueText = "${liveBgBlur.toInt()} dp",
                        onValueChangeFinished = {
                            activeSlider = null
                            viewModel.updateEvent(event.copy(wallpaperBlur = liveBgBlur.toInt()))
                        },
                    )
                    SliderPreference(
                        title = "背景遮罩",
                        value = liveBgDim,
                        onValueChange = {
                            activeSlider = ActiveSlider.BG_DIM
                            liveBgDim = it
                        },
                        valueRange = 0f..0.8f,
                        valueText = "${(liveBgDim * 100).toInt()}%",
                        onValueChangeFinished = {
                            activeSlider = null
                            viewModel.updateEvent(event.copy(wallpaperDim = liveBgDim))
                        },
                    )
                }
                if (bgMode == BgMode.WALLPAPER && glassSupported) {
                    SliderPreference(
                        title = "卡片模糊度",
                        value = liveCardBlur,
                        onValueChange = {
                            activeSlider = ActiveSlider.CARD_BLUR
                            liveCardBlur = it
                        },
                        valueRange = 0f..120f,
                        valueText = "${liveCardBlur.toInt()}",
                        onValueChangeFinished = {
                            activeSlider = null
                            viewModel.updateEvent(event.copy(cardBlur = liveCardBlur))
                        },
                    )
                }
            }
        }
    }
}

/** Full-width tappable row for background-mode selection (with check mark). */
@Composable
private fun BgModeRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            })
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurface,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = MiuixIcons.Ok,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Circular action button with a label below it. When [liquidBackdrop] is set
 * (Liquid Glass app style), the circle becomes a refracting glass lens;
 * otherwise when [glassBackdrop] is set it becomes frosted glass (official
 * textureBlur) instead of solid surfaceContainer.
 */
@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onCardText: Color,
    glassBackdrop: LayerBackdrop?,
    glassBlend: List<BlendColorEntry>,
    blurRadius: Float,
    liquidBackdrop: LiquidBackdrop?,
    liquidTint: Color,
    fallbackContainer: Color,
    fallbackContent: Color,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    // The clickable sits on the clipped circular icon container, so both the
    // ripple and the long-press highlight are bounded to the circle shape
    // instead of the whole column (rectangular box).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(
                    when {
                        liquidBackdrop != null -> Modifier.liquidGlass(
                            backdrop = liquidBackdrop,
                            // Percent-rounded CornerBasedShape — supported by
                            // the lens effect (plain CircleShape is an oval).
                            shape = RoundedCornerShape(50),
                            blurRadius = (blurRadius * 0.25f).dp,
                            tint = liquidTint,
                            lensHeight = 10.dp,
                            lensAmount = 14.dp,
                        )
                        glassBackdrop != null -> Modifier.textureBlur(
                            backdrop = glassBackdrop,
                            shape = CircleShape,
                            blurRadius = blurRadius,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurDefaults.blurColors(blendColors = glassBlend),
                        )
                        else -> Modifier.background(fallbackContainer)
                    }
                )
                .clickable(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick()
                }),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (glassBackdrop != null || liquidBackdrop != null) onCardText
                else fallbackContent,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            color = if (glassBackdrop != null || liquidBackdrop != null) onCardText
            else fallbackContent,
            style = MiuixTheme.textStyles.footnote1,
        )
    }
}

/** Average perceptual luminance (0..1) of a bitmap, stride-sampled. */
private fun avgLuminance(bitmap: Bitmap): Float {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return 0f
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    val step = maxOf(1, pixels.size / 10000)
    var sum = 0f
    var count = 0
    var index = 0
    while (index < pixels.size) {
        val p = pixels[index]
        val r = (p shr 16 and 0xFF) / 255f
        val g = (p shr 8 and 0xFF) / 255f
        val b = (p and 0xFF) / 255f
        sum += 0.2126f * r + 0.7152f * g + 0.0722f * b
        count++
        index += step
    }
    return if (count == 0) 0f else sum / count
}

/**
 * Programmatically render a 1080x1620 share card: a vertical gradient
 * background (derived from the custom card color, or HyperDay blue), a
 * rounded card with the event content, and a signature at the bottom.
 */
private fun renderShareCard(
    title: String,
    note: String?,
    describe: String,
    dayNum: Long,
    dateLine: String,
    statusLabel: String,
    isPast: Boolean,
    cardArgb: Long?,
): Bitmap {
    val w = 1080
    val h = 1620
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    val custom = cardArgb?.let { Color(it) }
    val cardColor = (custom ?: Color.White).toArgb()

    // Contrast-aware text colors over the card.
    val onCard = when {
        custom != null && custom.luminance() > 0.5f -> 0xFF1B1B1F.toInt()
        custom != null -> 0xFFFFFFFF.toInt()
        else -> 0xFF1B1B1F.toInt()
    }
    val onCardSummary = (onCard ushr 24 shl 24) or
        (((onCard shr 16 and 0xFF) * 200 / 255) shl 16) or
        (((onCard shr 8 and 0xFF) * 200 / 255) shl 8) or
        ((onCard and 0xFF) * 200 / 255)
    val accent = when {
        custom != null -> onCard
        isPast -> 0xFF8A8A8E.toInt()
        else -> 0xFF3482FF.toInt()
    }
    val pillFg = when {
        custom != null -> onCard
        isPast -> 0xFF8A8A8E.toInt()
        else -> 0xFF3482FF.toInt()
    }

    // Background gradient from the card hue (or HyperDay blue).
    fun shade(c: Color, f: Float): Int {
        fun ch(x: Float) = (x * f).coerceIn(0f, 1f)
        return Color(ch(c.red), ch(c.green), ch(c.blue), 1f).toArgb()
    }

    val (bgTop, bgBottom) = if (custom != null) {
        shade(custom, 1.15f) to shade(custom, 0.55f)
    } else {
        0xFF4A8DFF.toInt() to 0xFF1E5FD0.toInt()
    }
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, h.toFloat(), bgTop, bgBottom, android.graphics.Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

    // Card
    val cardL = 90f
    val cardT = 210f
    val cardR = 990f
    val cardB = 1410f
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardColor }
    canvas.drawRoundRect(cardL, cardT, cardR, cardB, 56f, 56f, cardPaint)

    fun textPaint(size: Float, bold: Boolean, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    val cx = w / 2f

    // Status pill
    val pillTextPaint = textPaint(36f, true, pillFg)
    val pillTextWidth = pillTextPaint.measureText(statusLabel)
    val pillW = pillTextWidth + 64f
    val pillH = 72f
    val pillL = cx - pillW / 2f
    val pillT = cardT + 90f
    val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pillFg and 0x00FFFFFF or 0x1F000000 }
    canvas.drawRoundRect(pillL, pillT, pillL + pillW, pillT + pillH, pillH / 2f, pillH / 2f, pillPaint)
    canvas.drawText(statusLabel, cx, pillT + 47f, pillTextPaint)

    // Title
    canvas.drawText(title, cx, cardT + 260f, textPaint(56f, true, onCard))

    // Note (optional)
    if (!note.isNullOrBlank()) {
        canvas.drawText(note, cx, cardT + 330f, textPaint(38f, false, onCardSummary))
    }

    // Describe line
    canvas.drawText(describe, cx, cardT + 400f, textPaint(40f, false, onCardSummary))

    // Big day number + unit
    val dayPaint = textPaint(220f, true, accent)
    val dayWidth = dayPaint.measureText(dayNum.toString())
    val dayX = cx - 30f
    canvas.drawText(dayNum.toString(), dayX, cardT + 670f, dayPaint)
    val unitPaint = textPaint(60f, true, accent)
    canvas.drawText("天", dayX + dayWidth / 2f + 70f, cardT + 670f, unitPaint)

    // Hairline divider
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = onCardSummary and 0x00FFFFFF or 0x40000000
        strokeWidth = 3f
    }
    canvas.drawLine(cx - 300f, cardT + 745f, cx + 300f, cardT + 745f, dividerPaint)

    // Start date block
    canvas.drawText("起始日", cx, cardT + 815f, textPaint(34f, false, onCardSummary))
    canvas.drawText(dateLine, cx, cardT + 880f, textPaint(46f, true, onCard))

    // Signature on the gradient, below the card
    canvas.drawText("HyperDay", cx, 1520f, textPaint(44f, true, 0xB3FFFFFF.toInt()))

    return bmp
}

/**
 * Save a bitmap to the system gallery on API 29+ (MediaStore, no permission needed),
 * or to the app's external pictures directory on older versions.
 * Returns a human-readable location, or null on failure.
 */
private fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    fileName: String,
): String? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/HyperDay",
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            } ?: return null
            "相册 Pictures/HyperDay"
        } else {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "HyperDay",
            )
            dir.mkdirs()
            val file = File(dir, "$fileName.png")
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            }
            file.absolutePath
        }
    } catch (e: Exception) {
        null
    }
}
