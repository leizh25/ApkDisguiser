package me.leizh25.apkdisguiser

import org.json.JSONObject
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal object AppUpdates {
    class MissingManifest : Exception()

    data class Release(
        val versionName: String,
        val versionCode: Long,
        val fileName: String,
        val size: Long?,
        val notes: String,
        val apkUrl: String
    )

    fun latest(): Release? {
        val release = fetch("https://api.github.com/repos/leizh25/ApkDisguiser/releases/latest") ?: return null
        val tag = release.getString("tag_name")
        require(Regex("v[0-9]+(?:\\.[0-9]+)+-[0-9]{8}").matches(tag))
        val assets = release.getJSONArray("assets")
        val names = (0 until assets.length()).map { assets.getJSONObject(it).getString("name") }
        val tagVersion = tag.substringBeforeLast('-').removePrefix("v")
        if ("update.json" !in names) throw MissingManifest()
        val manifest = checkNotNull(fetch("https://github.com/leizh25/ApkDisguiser/releases/download/$tag/update.json"))
        require(manifest.getString("packageName") == "me.leizh25.apkdisguiser")
        val apk = manifest.getString("apk")
        require(Regex("[A-Za-z0-9._-]+\\.apk").matches(apk) && apk in names)
        val downloadUrl = manifest.getString("downloadUrl")
        val downloadUri = URI(downloadUrl)
        require(downloadUri.scheme.equals("https", ignoreCase = true) && downloadUri.host != null)
        val version = manifest.optString("versionName", "").trim()
        require(version.isEmpty() || version == tagVersion)
        val size = (manifest.opt("size") as? Number)?.let { bytes ->
            bytes.toLong().takeIf { it > 0 && it.toDouble() == bytes.toDouble() }
        }
        val code = manifest.getLong("versionCode")
        require(code in 1..2100000000)
        val notes = release.optString("body").replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "").trim()
        return Release(version, code, apk, size, notes, downloadUrl)
    }

    private fun fetch(url: String): JSONObject? {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Accept", "application/json")
            if (connection.responseCode == 404) return null
            check(connection.responseCode == 200)
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally {
            connection.disconnect()
        }
    }
}
