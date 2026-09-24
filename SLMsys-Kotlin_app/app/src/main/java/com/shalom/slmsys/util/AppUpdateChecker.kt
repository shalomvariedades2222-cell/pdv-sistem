package com.shalom.slmsys.util

import com.shalom.slmsys.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verifica no GitHub Releases se existe versão mais nova do APK.
 *
 * Configure [BuildConfig.GITHUB_REPO] em `app/build.gradle.kts`
 * (ex.: `"sua-conta/slmsys-android"`).
 *
 * Fluxo no GitHub:
 * 1. Suba o código pro repositório
 * 2. Crie um Release com tag `v1.1.0` (ou igual ao versionName)
 * 3. Anexe o arquivo `app-debug.apk` ou `app-release.apk` nos assets
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val available: Boolean,
        val latestTag: String = "",
        val latestName: String = "",
        val apkUrl: String = "",
        val body: String = "",
        val error: String? = null
    )

    fun check(): UpdateInfo {
        val repo = BuildConfig.GITHUB_REPO.trim()
        if (repo.isBlank() || repo == "OWNER/REPO" || !repo.contains("/")) {
            return UpdateInfo(
                available = false,
                error = "Configure GITHUB_REPO no build.gradle.kts (ex.: conta/slmsys-android)"
            )
        }
        return try {
            val url = URL("https://api.github.com/repos/$repo/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "SLMsys-Android")
            }
            if (conn.responseCode !in 200..299) {
                return UpdateInfo(
                    available = false,
                    error = "GitHub HTTP ${conn.responseCode}. Crie um Release no repositório."
                )
            }
            val text = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(text)
            val tag = json.optString("tag_name").removePrefix("v").trim()
            val name = json.optString("name").ifBlank { tag }
            val body = json.optString("body")
            val assets = json.optJSONArray("assets")
            var apkUrl = ""
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val n = a.optString("name").lowercase()
                    if (n.endsWith(".apk")) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            val current = BuildConfig.VERSION_NAME.removePrefix("v").trim()
            val newer = isNewer(tag, current)
            UpdateInfo(
                available = newer && apkUrl.isNotBlank(),
                latestTag = tag,
                latestName = name,
                apkUrl = apkUrl,
                body = body,
                error = when {
                    !newer -> null
                    apkUrl.isBlank() -> "Release $tag sem APK anexo. Anexe o .apk no GitHub Release."
                    else -> null
                }
            )
        } catch (e: Exception) {
            UpdateInfo(available = false, error = e.message ?: "Falha ao checar atualização")
        }
    }

    /** Compara versões tipo 1.1.0 vs 1.0.1 */
    private fun isNewer(remote: String, local: String): Boolean {
        fun parts(s: String) = s.split(".", "-", "_")
            .mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val r = parts(remote)
        val l = parts(local)
        val n = maxOf(r.size, l.size)
        for (i in 0 until n) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a > b) return true
            if (a < b) return false
        }
        return false
    }
}
