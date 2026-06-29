package com.bintang.bacamangaid

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GitHubWriteApi {

    private const val OWNER = "webanh9-droid"
    private const val REPO = "Bacamangaid"

    // ====== Token GitHub (scope: repo) buat upload file dari app ======
    // PERINGATAN: token ini ada di dalam APK. Repo ini publik, jadi risikonya
    // "cukup diterima" sesuai keputusan awal. Kalau mau lebih aman nanti,
    // ganti pendekatan ini pakai backend perantara.
    // Token diisi otomatis dari GitHub Actions secret saat build (lihat .github/workflows/build.yml)
    private val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN

    /**
     * Upload (atau replace) file ke repo GitHub di path tertentu (root repo).
     * fileName contoh: "Si Ocong Chapter 5.pdf" atau "Si Ocong Cover.jpg"
     */
    fun uploadFile(fileName: String, content: ByteArray, commitMessage: String) {
        val existingSha = getExistingFileSha(fileName)

        val url = URL("https://api.github.com/repos/$OWNER/$REPO/contents/$fileName")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 30000

        val base64Content = Base64.encodeToString(content, Base64.NO_WRAP)

        val body = JSONObject()
        body.put("message", commitMessage)
        body.put("content", base64Content)
        body.put("branch", "main")
        if (existingSha != null) {
            body.put("sha", existingSha)
        }

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Upload gagal ($responseCode): $errorBody")
        }
        connection.disconnect()

        // GitHub API listing kadang nge-cache; reset cache lokal biar list ke-refresh
        GitHubApi.clearCache()
    }

    /** Cek apakah file dengan nama itu udah ada di repo, kalau ada return sha-nya (perlu buat update/replace). */
    private fun getExistingFileSha(fileName: String): String? {
        return try {
            val url = URL("https://api.github.com/repos/$OWNER/$REPO/contents/$fileName")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "token $GITHUB_TOKEN")
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("sha", null)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
