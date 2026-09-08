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
 * GitHub-Releases-based in-app updater (在线更新).
 *
 * Checks https://api.github.com/repos/ChaYeWuu/HyperDay/releases/latest,
 * compares the tag with the local version name, downloads the release APK
 * asset into filesDir and hands it to the system installer via FileProvider.
 *
 * api.github.com / github.com are often unreachable from CN networks, so
 * every request tries the direct host first and then a couple of public
 * GitHub proxy mirrors.
 */
object AppUpdater {

    private const val REPO = "ChaYeWuu/HyperDay"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT = "HyperDay-Android"

    /** API hosts to try in order: direct GitHub, then public mirrors. */
    private val API_BASES = listOf(
        "https://api.github.com",
        "https://gh-proxy.com/https://api.github.com",
        "https://ghproxy.net/https://api.github.com",
    )

    /** Download URL prefixes to try in order ("" = direct). */
    private val DOWNLOAD_PREFIXES = listOf("", "https://gh-proxy.com/", "https://ghproxy.net/")

    data class Release(
        val tagName: String,
        val name: String,
        val body: String,
        val apkUrl: String,
        val htmlUrl: String,
        val publishedAt: String,
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

    /** Blocking; call from Dispatchers.IO. */
    fun checkForUpdate(): CheckResult {
        var lastError = "无法连接更新服务器"
        for (base in API_BASES) {
            try {
                val body = httpGet("$base/repos/$REPO/releases/latest")
                    ?: continue
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
                    publishedAt = json.optString("published_at"),
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
     */
    fun downloadApk(
        context: Context,
        release: Release,
        onProgress: (Float) -> Unit,
    ): File {
        val target = File(context.filesDir, "update-${release.tagName}.apk")
        var lastError: IOException? = null
        for (prefix in DOWNLOAD_PREFIXES) {
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
        is java.net.UnknownHostException -> "无法连接 GitHub，请检查网络或 DNS"
        is IOException -> "网络错误，请稍后重试"
        else -> "更新数据无法解析"
    }
}
