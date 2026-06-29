package com.bintang.bacamangaid

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class MangaMeta(
    val title: String,
    val synopsis: String?,
    val genreId: Long?,
    val genreName: String?,
    val coverUrlOverride: String?
)

data class GenreItem(val id: Long, val name: String)

object SupabaseApi {

    private const val SUPABASE_URL = "https://epuyvcwrdrltxbhdegsi.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVwdXl2Y3dyZHJsdHhiaGRlZ3NpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIwODQzNDcsImV4cCI6MjA4NzY2MDM0N30.kOZ381kxAGFkI_rz4L3G9lJ8ioxVIp6ujiD0xrgI7cE"

    fun fetchAllManga(): List<MangaMeta> {
        val url = "$SUPABASE_URL/rest/v1/manga?select=title,synopsis,genre_id,cover_url,genres(name)"
        val response = getRequest(url, null)
        val jsonArray = JSONArray(response)

        val list = mutableListOf<MangaMeta>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val title = obj.getString("title")
            val synopsis = if (obj.isNull("synopsis")) null else obj.optString("synopsis")
            val genreId = if (obj.isNull("genre_id")) null else obj.optLong("genre_id")
            val coverOverride = if (obj.isNull("cover_url")) null else obj.optString("cover_url")
            val genreObj = obj.optJSONObject("genres")
            val genreName = genreObj?.optString("name")
            list.add(MangaMeta(title, synopsis, genreId, genreName, coverOverride))
        }
        return list
    }

    fun fetchGenres(): List<GenreItem> {
        val url = "$SUPABASE_URL/rest/v1/genres?select=id,name&order=name"
        val response = getRequest(url, null)
        val jsonArray = JSONArray(response)

        val list = mutableListOf<GenreItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(GenreItem(obj.getLong("id"), obj.getString("name")))
        }
        return list
    }

    /** Catat history baca (cuma kalau user login). */
    fun recordRead(accessToken: String, userId: String, mangaTitle: String, genreId: Long?) {
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
        if (genreId != null) body.put("genre_id", genreId)

        connection.outputStream.use { it.write(body.toString().toByteArray()) }
        connection.responseCode // trigger request beneran terkirim
        connection.disconnect()
    }

    /** Hitung genre apa yang paling sering dibaca user ini -> Map<genreId, jumlahDibaca> */
    fun fetchUserGenreCounts(accessToken: String, userId: String): Map<Long, Int> {
        val url = "$SUPABASE_URL/rest/v1/reading_history?select=genre_id&user_id=eq.$userId"
        val response = getRequest(url, accessToken)
        val arr = JSONArray(response)

        val counts = mutableMapOf<Long, Int>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (!obj.isNull("genre_id")) {
                val gid = obj.getLong("genre_id")
                counts[gid] = (counts[gid] ?: 0) + 1
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
