package com.chayewuu.hypermatter.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Remote notice (远程公告 / 入群提示) fetcher, ported from the HyperIntervals
 * NoticeFetcher pattern.
 *
 * A tiny JSON file at the repo root drives a startup dialog without
 * shipping an app update:
 *
 * ```json
 * { "id": "2026-09-10-1", "title": "标题", "content": "正文" }
 * ```
 *
 *  * Gitee raw (main source, reachable from CN) then GitHub raw mirror.
 *  * `latest` display mode: the dialog reappears whenever the server's
 *    `id` changes (bump the id to re-notify every user once).
 *  * `always` display mode: shows on every launch.
 *  * Tapping 完成 marks the id as seen; dismissing by tapping outside
 *    does NOT mark it, so the notice resurfaces next launch.
 */
object NoticeFetcher {

    data class RemoteNotice(
        val id: String,
        val title: String,
        val content: String,
    )

    private const val PREFERENCES_NAME = "remote_notice_prefs"
    private const val KEY_SEEN_ID = "seen_id"
    private const val KEY_DISPLAY_MODE = "display_mode"
    const val DISPLAY_MODE_LATEST = "latest"
    const val DISPLAY_MODE_ALWAYS = "always"

    // Gitee-only notice source (direct CN access, no fallback needed).
    // The file is notice.json at the repo root:
    // https://gitee.com/chayewuuu/HyperDay/blob/main/notice.json
    private const val GITEE_URL =
        "https://gitee.com/chayewuuu/HyperDay/raw/main/notice.json"

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val USER_AGENT = "HyperDay-Android"

    /** Blocking; call from Dispatchers.IO. Returns null on failure. */
    fun fetch(): RemoteNotice? = runCatching { fetchFrom(GITEE_URL) }.getOrNull()

    private fun fetchFrom(url: String): RemoteNotice? {
        val connection = URL(url).openConnection() as HttpURLConnection
        val body = try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        if (body.isBlank()) return null
        val json = JSONObject(body)
        val notice = RemoteNotice(
            id = json.optString("id"),
            title = json.optString("title"),
            content = json.optString("content"),
        )
        // An invalid entry (blank id/title) is treated as "no notice".
        return notice.takeIf { it.id.isNotBlank() && it.title.isNotBlank() }
    }

    /** True when the fetched notice should surface this launch. */
    fun shouldShow(context: Context, notice: RemoteNotice): Boolean {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return getDisplayMode(context) == DISPLAY_MODE_ALWAYS ||
            prefs.getString(KEY_SEEN_ID, null) != notice.id
    }

    fun getDisplayMode(context: Context): String {
        val mode = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISPLAY_MODE, DISPLAY_MODE_LATEST)
        return if (mode == DISPLAY_MODE_ALWAYS) DISPLAY_MODE_ALWAYS else DISPLAY_MODE_LATEST
    }

    fun setDisplayMode(context: Context, displayMode: String) {
        val mode = if (displayMode == DISPLAY_MODE_ALWAYS) DISPLAY_MODE_ALWAYS
        else DISPLAY_MODE_LATEST
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISPLAY_MODE, mode).apply()
    }

    /** Records the notice as read; suppresses it until the id changes. */
    fun markSeen(context: Context, notice: RemoteNotice) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SEEN_ID, notice.id).apply()
    }

    /** Local dev helper: preview notice.json without touching the network. */
    fun fetchLocal(file: File): RemoteNotice? = runCatching {
        val json = JSONObject(file.readText())
        RemoteNotice(
            id = json.optString("id"),
            title = json.optString("title"),
            content = json.optString("content"),
        ).takeIf { it.id.isNotBlank() && it.title.isNotBlank() }
    }.getOrNull()
}
