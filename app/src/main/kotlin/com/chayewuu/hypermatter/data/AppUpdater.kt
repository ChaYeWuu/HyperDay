package com.chayewuu.hypermatter.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.chayewuu.hypermatter.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Releases-based in-app updater (在线更新), with a selectable download
 * source: Gitee (国内直连) or GitHub (direct + public proxy mirrors).
 *
 * Gitee: https://gitee.com/api/v5/repos/ChaYeWuu/HyperDay/releases/latest —
 * same tag_name / name / body / assets[].browser_download_url shape as the
 * GitHub API, and the asset URL is directly reachable from CN networks.
 * GitHub: api.github.com first, then gh-proxy / ghproxy mirrors.
 *
 * The chosen source is persisted in the "update_prefs" preferences
 * ("download_source": auto | gitee | github; auto = gitee → github).
 */
object AppUpdater {

    private const val REPO = "ChaYeWuu/HyperDay"
    // Gitee mirror (owner differs: chayewuuu vs ChaYeWuu on GitHub).
    private const val GITEE_API =
        "https://gitee.com/api/v5/repos/chayewuuu/HyperDay/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "HyperDay-Android"

    /** GitHub API hosts to try in order: direct, then public mirrors. */
    private val GITHUB_API_BASES = listOf(
        "https://api.github.com",
        "https://gh-proxy.com/https://api.github.com",
        "https://ghproxy.net/https://api.github.com",
    )

    /** GitHub download URL prefixes to try in order ("" = direct). */
    private val GITHUB_DOWNLOAD_PREFIXES = listOf("", "https://gh-proxy.com/", "https://ghproxy.net/")

    // ------------------------------------------------------------------
    // Download source preference
    // ------------------------------------------------------------------

    const val SOURCE_AUTO = "auto"
    const val SOURCE_GITEE = "gitee"
    const val SOURCE_GITHUB = "github"

    fun getDownloadSource(context: Context): String =
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            .getString("download_source", SOURCE_AUTO) ?: SOURCE_AUTO

    fun setDownloadSource(context: Context, source: String) {
        context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
            .edit().putString("download_source", source).apply()
    }

    data class Release(
        val tagName: String,
        val name: String,
        val body: String,
        val apkUrl: String,
        val htmlUrl: String,
        val publishedAt: String,
        /** Where this release was fetched from: "gitee" or "github". */
        val source: String,
    ) {
        /** "v1.2.0" style display version derived from the tag. */
        val versionLabel: String get() = tagName.removePrefix("v")
    }

    sealed interface CheckResult {
        /** Completed a check; [release] is the latest GitHub release. */
        data class Checked(val hasUpdate: Boolean, val release: Release) : CheckResult

        /** Network / parsing failure on every host. */
        data class Failed(val message: String) : CheckResult
    }

    // ------------------------------------------------------------------
    // Version comparison
    // ------------------------------------------------------------------

    /** True when [remote] is strictly newer than the installed version. */
    fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.removePrefix("v").trim()
        val l = local.removePrefix("v").trim()
        val rParts = r.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        val lParts = l.substringBefore("-").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(rParts.size, lParts.size)) {
            val rv = rParts.getOrElse(i) { 0 }
            val lv = lParts.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    // ------------------------------------------------------------------
    // Check
    // ------------------------------------------------------------------

    /**
     * Blocking; call from Dispatchers.IO. Tries the sources selected by the
     * persisted download-source preference (auto = Gitee → GitHub).
     */
    fun checkForUpdate(context: Context): CheckResult {
        val source = getDownloadSource(context)
        val urls = when (source) {
            SOURCE_GITEE -> listOf(GITEE_API)
            SOURCE_GITHUB -> GITHUB_API_BASES.map { "$it/repos/$REPO/releases/latest" }
            else -> listOf(GITEE_API) + GITHUB_API_BASES.map { "$it/repos/$REPO/releases/latest" }
        }
        var lastError = "无法连接更新服务器"
        for (url in urls) {
            val isGitee = url.contains("gitee.com")
            try {
                val body = httpGet(url) ?: continue
                val json = JSONObject(body)
                val tagName = json.optString("tag_name")
                if (tagName.isBlank()) {
                    lastError = "Release 数据不完整"
                    continue
                }
                val assets = json.optJSONArray("assets")
                var apkUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val url = asset.optString("browser_download_url")
                        if (url.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = url
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) {
                    lastError = "最新 Release 没有 APK 附件"
                    continue
                }
                val release = Release(
                    tagName = tagName,
                    name = json.optString("name").ifBlank { tagName },
                    body = json.optString("body"),
                    apkUrl = apkUrl,
                    htmlUrl = json.optString("html_url"),
                    // Gitee uses created_at; GitHub uses published_at.
                    publishedAt = json.optString("published_at")
                        .ifBlank { json.optString("created_at") },
                    source = if (isGitee) SOURCE_GITEE else SOURCE_GITHUB,
                )
                return CheckResult.Checked(
                    hasUpdate = isNewerVersion(release.versionLabel, BuildConfig.VERSION_NAME),
                    release = release,
                )
            } catch (e: Exception) {
                lastError = friendlyError(e)
            }
        }
        return CheckResult.Failed(lastError)
    }

    // ------------------------------------------------------------------
    // Download & install
    // ------------------------------------------------------------------

    /** Already-downloaded APK for the tag, if any. */
    fun downloadedApk(context: Context, tag: String): File? =
        File(context.filesDir, "update-$tag.apk")
            .takeIf { it.exists() && it.length() > 0 }

    /**
     * Downloads the release APK into filesDir, reporting progress 0..1.
     * Blocking; call from Dispatchers.IO. Throws IOException on failure.
     * Gitee asset URLs are directly reachable; GitHub URLs fall back to
     * the public proxy mirrors.
     */
    fun downloadApk(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit,
    ): File {
        val target = File(context.filesDir, "update-${release.tagName}.apk")
        val prefixes = if (release.source == SOURCE_GITEE) listOf("")
        else GITHUB_DOWNLOAD_PREFIXES
        var lastError: IOException? = null
        for (prefix in prefixes) {
            try {
                downloadTo(prefix + release.apkUrl, target, onProgress)
                if (target.length() > 0) {
                    cleanOldApks(context, keepTag = release.tagName)
                    return target
                }
                target.delete()
            } catch (e: IOException) {
                lastError = e
            }
        }
        throw lastError ?: IOException("下载失败")
    }

    /** Deletes previously downloaded update APKs except [keepTag]'s. */
    fun cleanOldApks(context: Context, keepTag: String) {
        runCatching {
            context.filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("update-") &&
                    file.name.endsWith(".apk") &&
                    file.name != "update-$keepTag.apk"
                ) {
                    file.delete()
                }
            }
        }
    }

    /** Opens the system installer for the downloaded APK. */
    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private fun httpGet(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadTo(url: String, target: File, onProgress: (Float) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            target.outputStream().use { out ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toDouble() / total).toFloat().coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun friendlyError(e: Exception): String = when (e) {
        is java.net.SocketTimeoutException -> "连接超时，请稍后重试"
        is java.net.UnknownHostException -> "无法连接更新服务器，请检查网络或 DNS"
        is IOException -> "网络错误，请稍后重试"
        else -> "更新数据无法解析"
    }
}
