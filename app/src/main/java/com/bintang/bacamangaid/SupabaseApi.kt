package com.bintang.bacamangaid

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MangaMeta(
    val title: String,
    val synopsis: String?,
    val genres: List<GenreItem> = emptyList(),
    val coverUrlOverride: String?,
    val statusId: Long? = null,
    val statusName: String? = null
)

data class GenreItem(val id: Long, val name: String)
data class StatusItem(val id: Long, val name: String)

object SupabaseApi {

    private const val SUPABASE_URL = "https://epuyvcwrdrltxbhdegsi.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVwdXl2Y3dyZHJsdHhiaGRlZ3NpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIwODQzNDcsImV4cCI6MjA4NzY2MDM0N30.kOZ381kxAGFkI_rz4L3G9lJ8ioxVIp6ujiD0xrgI7cE"

    fun fetchAllManga(): List<MangaMeta> {
        // Pakai Supabase nested select lewat tabel relasi manga_genres
        val url = "$SUPABASE_URL/rest/v1/manga?select=title,synopsis,cover_url,status_id,manga_statuses(name),manga_genres(genre_id,genres(id,name))"
        val response = getRequest(url, null)
        val jsonArray = JSONArray(response)

        val list = mutableListOf<MangaMeta>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val title = obj.getString("title")
            val synopsis = if (obj.isNull("synopsis")) null else obj.optString("synopsis")
            val coverOverride = if (obj.isNull("cover_url")) null else obj.optString("cover_url")

            // Status
            val statusId = if (obj.isNull("status_id")) null else obj.optLong("status_id")
            val statusObj = obj.optJSONObject("manga_statuses")
            val statusName = statusObj?.optString("name")

            // Genres — array dari manga_genres -> genres
            val genres = mutableListOf<GenreItem>()
            val mangaGenresArr = obj.optJSONArray("manga_genres")
            if (mangaGenresArr != null) {
                for (j in 0 until mangaGenresArr.length()) {
                    val mg = mangaGenresArr.getJSONObject(j)
                    val genreObj = mg.optJSONObject("genres")
                    if (genreObj != null) {
                        genres.add(GenreItem(genreObj.getLong("id"), genreObj.getString("name")))
                    }
                }
            }

            list.add(MangaMeta(title, synopsis, genres, coverOverride, statusId, statusName))
        }
        return list
    }

    fun fetchGenres(): List<GenreItem> {
        val url = "$SUPABASE_URL/rest/v1/genres?select=id,name&order=name"
        val response = getRequest(url, null)
        val jsonArray = JSONArray(response)
        return (0 until jsonArray.length()).map {
            val obj = jsonArray.getJSONObject(it)
            GenreItem(obj.getLong("id"), obj.getString("name"))
        }
    }

    fun fetchStatuses(): List<StatusItem> {
        val url = "$SUPABASE_URL/rest/v1/manga_statuses?select=id,name&order=name"
        val response = getRequest(url, null)
        val jsonArray = JSONArray(response)
        return (0 until jsonArray.length()).map {
            val obj = jsonArray.getJSONObject(it)
            StatusItem(obj.getLong("id"), obj.getString("name"))
        }
    }

    /** Catat history baca user (cuma judul manga — genre udah nggak dicatat di reading_history). */
    fun recordRead(accessToken: String, userId: String, mangaTitle: String) {
        val url = URL("$SUPABASE_URL/rest/v1/reading_history")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        body.put("user_id", userId)
        body.put("manga_title", mangaTitle)

        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        connection.responseCode
        connection.disconnect()
    }

    /**
     * Hitung genre apa yang paling sering dibaca user, dengan cara:
     * 1) Ambil semua manga_title dari reading_history user ini.
     * 2) Untuk tiap title, cari genre-genre-nya lewat manga -> manga_genres -> genres.
     * 3) Genre dihitung sekali per baca (manga dengan 2 genre nambah count ke kedua genre itu).
     *
     * Pakai ini (bukan baca genre_id langsung dari reading_history) karena genre sekarang
     * many-to-many lewat manga_genres, jadi nggak ada lagi genre_id tunggal yang bisa dibaca langsung.
     */
    fun fetchUserGenreCounts(accessToken: String, userId: String): Map<Long, Int> {
        val historyUrl = "$SUPABASE_URL/rest/v1/reading_history?select=manga_title&user_id=eq.$userId"
        val historyResponse = getRequest(historyUrl, accessToken)
        val historyArr = JSONArray(historyResponse)

        val readTitles = mutableListOf<String>()
        for (i in 0 until historyArr.length()) {
            val obj = historyArr.getJSONObject(i)
            if (!obj.isNull("manga_title")) readTitles.add(obj.getString("manga_title"))
        }
        if (readTitles.isEmpty()) return emptyMap()

        // Genre per judul manga didapat dari data manga lengkap (sudah include manga_genres -> genres)
        val allManga = fetchAllManga().associateBy { it.title.lowercase() }

        val counts = mutableMapOf<Long, Int>()
        for (title in readTitles) {
            val meta = allManga[title.lowercase()] ?: continue
            for (genre in meta.genres) {
                counts[genre.id] = (counts[genre.id] ?: 0) + 1
            }
        }
        return counts
    }

    private fun getRequest(urlStr: String, accessToken: String?): String {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer ${accessToken ?: SUPABASE_ANON_KEY}")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
