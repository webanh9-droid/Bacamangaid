package com.bintang.bacamangaid

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class RepoItem(
    val name: String,
    val type: String, // "dir" atau "file"
    val downloadUrl: String?
)

data class ChapterFile(
    val title: String,
    val chapterNum: Int,
    val downloadUrl: String,
    val fileName: String
)

data class CoverFile(
    val title: String,
    val downloadUrl: String
)

object GitHubApi {

    private const val OWNER = "webanh9-droid"
    private const val REPO = "Bacamangaid"

    // Pattern nama file: "Judul Manga Chapter 3.pdf" -> title="Judul Manga", chapterNum=3
    private val CHAPTER_REGEX = Regex("""^(.*?)\s*chapter\s*(\d+)\.pdf$""", RegexOption.IGNORE_CASE)

    // Pattern nama file cover: "Judul Manga Cover.jpg/png" -> title="Judul Manga"
    private val COVER_REGEX = Regex("""^(.*?)\s*cover\.(jpg|jpeg|png)$""", RegexOption.IGNORE_CASE)

    // Cache sederhana biar nggak fetch root listing berkali-kali dalam 1 sesi buka app
    private var cachedRootListing: List<RepoItem>? = null

    /**
     * Ambil daftar isi folder dari repo GitHub.
     * path = "" untuk root.
     */
    fun listContents(path: String = ""): List<RepoItem> {
        if (path.isEmpty() && cachedRootListing != null) {
            return cachedRootListing!!
        }

        val url = if (path.isEmpty()) {
            "https://api.github.com/repos/$OWNER/$REPO/contents"
        } else {
            "https://api.github.com/repos/$OWNER/$REPO/contents/$path"
        }

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val response = connection.inputStream.bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)

        val items = mutableListOf<RepoItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val name = obj.getString("name")
            val type = obj.getString("type")
            val downloadUrl = obj.optString("download_url", null)
            items.add(RepoItem(name, type, downloadUrl))
        }

        if (path.isEmpty()) {
            cachedRootListing = items
        }
        return items
    }

    /** Reset cache (panggil ini kalau mau force refresh, misal swipe-to-refresh) */
    fun clearCache() {
        cachedRootListing = null
    }

    /**
     * Ambil semua file PDF chapter di root yang cocok pola "Judul Chapter N.pdf",
     * lalu pecah jadi title + nomor chapter.
     */
    fun listAllChapterFiles(): List<ChapterFile> {
        return listContents("")
            .filter { it.type == "file" && it.downloadUrl != null }
            .mapNotNull { item ->
                val match = CHAPTER_REGEX.find(item.name) ?: return@mapNotNull null
                val title = match.groupValues[1].trim()
                val num = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                if (title.isEmpty()) return@mapNotNull null
                ChapterFile(title, num, item.downloadUrl!!, item.name)
            }
    }

    /**
     * Ambil semua file cover di root yang cocok pola "Judul Cover.jpg/png".
     */
    fun listAllCoverFiles(): List<CoverFile> {
        return listContents("")
            .filter { it.type == "file" && it.downloadUrl != null }
            .mapNotNull { item ->
                val match = COVER_REGEX.find(item.name) ?: return@mapNotNull null
                val title = match.groupValues[1].trim()
                if (title.isEmpty()) return@mapNotNull null
                CoverFile(title, item.downloadUrl!!)
            }
    }

    /**
     * Daftar judul manga unik (hasil deteksi dari nama file chapter), urut alfabet.
     */
    fun listMangaTitles(): List<String> {
        return listAllChapterFiles()
            .map { it.title }
            .distinctBy { it.lowercase() }
            .sorted()
    }

    /**
     * Daftar chapter (nomor + url download) untuk 1 judul manga tertentu, urut nomor chapter.
     */
    fun listChaptersForTitle(title: String): List<Pair<Int, String>> {
        return listAllChapterFiles()
            .filter { it.title.equals(title, ignoreCase = true) }
            .map { Pair(it.chapterNum, it.downloadUrl) }
            .sortedBy { it.first }
    }

    /**
     * Cari URL cover untuk 1 judul manga, kalau ada file "Judul Cover.jpg/png" di root.
     */
    fun getCoverUrlForTitle(title: String): String? {
        return listAllCoverFiles()
            .firstOrNull { it.title.equals(title, ignoreCase = true) }
            ?.downloadUrl
    }
}
