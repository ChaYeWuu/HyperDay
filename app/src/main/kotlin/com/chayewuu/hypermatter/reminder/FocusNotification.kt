package com.chayewuu.hypermatter.reminder

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import org.json.JSONObject

/**
 * Xiaomi HyperOS 焦点通知 / 小米超级岛 integration, client-side path per
 * dev.mi.com guide (pId=2131) and the official island template library:
 * attach a JSON payload under the `miui.focus.param` notification extra and
 * any referenced icons under the `miui.focus.pics` Bundle.
 *
 * - baseInfo renders the HyperOS 焦点通知 (banner + status-bar focus + AOD)
 * - param_island renders the 小米超级岛 (summary pill around the camera +
 *   expanded island) on protocol 3 (HyperOS 3) devices
 * - unsupported ROMs ignore the extras and degrade to a plain notification
 *   (filterWhenNoPermission defaults to false)
 *
 * Note: local island display may additionally require the app's device to
 * be whitelisted in Xiaomi's dev console (接入流程 pId=2132).
 */
object FocusNotification {

    /** Notification extra key carrying the island/focus JSON payload. */
    const val EXTRA_PARAM = "miui.focus.param"

    /** Extra Bundle key carrying icon resources referenced by the JSON. */
    const val EXTRA_PICS = "miui.focus.pics"

    /** pics-Bundle key of the calendar icon used by the island template. */
    const val PIC_CALENDAR = "miui.focus.pic_calendar"

    /** pics-Bundle keys of the small-island (summary pill) icons. */
    const val PIC_SMALL = "miui.focus.pic_small"
    const val PIC_SMALL_DARK = "miui.focus.pic_small_dark"
    const val PIC_APP_ICON = "miui.focus.pic_app_icon"
    const val PIC_APP_ICON_DARK = "miui.focus.pic_app_icon_dark"

    /** 通知更新序号：多次更新（倒计时→已到期）防乱序（官方 sequence 字段）。 */
    private val sequenceCounter = java.util.concurrent.atomic.AtomicLong(
        System.currentTimeMillis() / 1000,
    )

    /**
     * 0 = unsupported, 1 = OS1 focus template, 2 = OS2 focus template,
     * 3 = OS3 超级岛 template.
     */
    fun focusProtocolVersion(context: Context): Int = try {
        Settings.System.getInt(
            context.contentResolver,
            "notification_focus_protocol",
            0,
        )
    } catch (_: Exception) {
        0
    }

    /** Whether the device has a dynamic-island capable display (HyperOS 3). */
    fun isIslandSupported(context: Context): Boolean = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getMethod(
            "getBoolean",
            String::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.invoke(null, "persist.sys.feature.island", false) as Boolean
    } catch (_: Exception) {
        // Fallback: protocol 3 implies OS3 island support.
        focusProtocolVersion(context) >= 3
    }

    /** Whether the app's focus-notification permission is enabled. */
    fun canShowFocus(context: Context): Boolean = try {
        val uri = Uri.parse("content://miui.statusbar.notification.public")
        val extras = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(uri, "canShowFocus", null, extras)
            ?.getBoolean("canShowFocus", false) ?: false
    } catch (_: Exception) {
        false
    }

    /**
     * 焦点通知 + 超级岛 payload（对齐 NexioSchedule 实测可用的结构）。
     *
     * baseInfo: 焦点通知上半文本（事件名 + 倒数描述）。
     * hintInfo: 焦点通知下半按钮组件 + 实时倒计时（timerInfo 指向目标日零点）。
     * param_island: 大岛 templateNo=2（A 区事件名图文 + B 区 sameWidthDigitInfo
     * 等宽倒计时），小岛为 pic 小图标。
     */
    fun buildParams(
        context: Context,
        title: String,
        content: String,
        targetTimestamp: Long,
        hintContent: String,
        hintTitle: String,
        colorTitle: String = "#3482FF",
    ): String {
        val now = System.currentTimeMillis()
        val counting = targetTimestamp > now
        val root = JSONObject()
        val v2 = JSONObject().apply {
            put("business", "hyperday")
            put("protocol", 1)
            put("ticker", content)
            put("aodTitle", "$title · $content")
            put("enableFloat", true)
            put("updatable", true)
            // HyperOS 展开态辉光效果。
            put("outEffectSrc", "outer_glow")
            // 同 id 通知允许再次弹岛。
            put("reopen", "reopen")
            // 每次更新递增，避免多状态展示乱序。
            put("sequence", sequenceCounter.incrementAndGet())

            put("baseInfo", JSONObject().apply {
                put("type", 2)
                put("title", title)
                put("content", content)
                put("subTitle", "")
                put("extraTitle", "")
                put("specialTitle", "")
                put("subContent", "")
                put("picFunction", "")
                put("showDivider", true)
                put("showContentDivider", false)
                put("colorTitle", colorTitle)
                put("colorTitleDark", "#ffffff")
                put("colorContent", "#333333")
                put("colorContentDark", "#cccccc")
            })

            // 识别图形：pic 留空 = 自动使用应用图标。
            put("picInfo", JSONObject().apply {
                put("type", 1)
                put("pic", "")
            })

            // 按钮组件 + 动态倒计时。
            put("hintInfo", JSONObject().apply {
                put("type", 2)
                put("content", hintContent)
                put("title", hintTitle)
                put("timerInfo", JSONObject().apply {
                    if (counting) {
                        put("timerType", -1) // -1 倒计时
                        put("timerWhen", targetTimestamp)
                        put("timerTotal", 0L)
                        put("timerSystemCurrent", now)
                    } else {
                        put("timerType", 0) // 0 静态文本
                        put("timerWhen", 0)
                        put("timerTotal", 0)
                        put("timerSystemCurrent", 0)
                    }
                })
                put("subContent", "目标日期")
                put("subTitle", content)
                put("colorContent", "#666666")
                put("colorContentDark", "#aaaaaa")
                put("colorTitle", "#222222")
                put("colorTitleDark", "#eeeeee")
                put("colorSubContent", "#666666")
                put("colorSubContentDark", "#aaaaaa")
                put("colorSubTitle", "#222222")
                put("colorSubTitleDark", "#eeeeee")
                put("actionInfo", JSONObject().apply {
                    put("actionTitle", "查看倒数日")
                    put("actionIntentType", 1)
                    put(
                        "actionIntent",
                        "intent:#Intent;component=${context.packageName}/.MainActivity;end",
                    )
                })
            })

            put("param_island", JSONObject().apply {
                put("islandProperty", 1)
                put("islandTimeout", 3600)
                put("bigIslandArea", JSONObject().apply {
                    put("templateNo", 2)
                    // A 区：事件名。
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("textInfo", JSONObject().apply {
                            put("title", title)
                            put("content", "")
                            put("showHighlightColor", true)
                            put("narrowFont", false)
                        })
                    })
                    // B 区：等宽倒计时数字（已到期则退化为静态文本）。
                    if (counting) {
                        put("sameWidthDigitInfo", JSONObject().apply {
                            put("content", "到来")
                            put("showHighlightColor", true)
                            put("timerInfo", JSONObject().apply {
                                put("timerType", -1)
                                put("timerWhen", targetTimestamp)
                                put("timerTotal", 0L)
                                put("timerSystemCurrent", now)
                            })
                        })
                        put("textInfo", JSONObject().apply {
                            put("frontTitle", "")
                            put("title", "")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    } else {
                        put("textInfo", JSONObject().apply {
                            put("frontTitle", "")
                            put("title", "已到期")
                            put("content", "")
                            put("showHighlightColor", false)
                            put("narrowFont", false)
                        })
                    }
                })
                put("smallIslandArea", JSONObject().apply {
                    put("picInfo", JSONObject().apply {
                        put("type", 1)
                        put("pic", PIC_SMALL)
                        put("picDark", PIC_SMALL_DARK)
                    })
                })
            })
        }
        root.put("param_v2", v2)
        return root.toString()
    }
}
