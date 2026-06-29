package com.bintang.bacamangaid

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AdminApi {

    private const val SUPABASE_URL = "https://epuyvcwrdrltxbhdegsi.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVwdXl2Y3dyZHJsdHhiaGRlZ3NpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIwODQzNDcsImV4cCI6MjA4NzY2MDM0N30.kOZ381kxAGFkI_rz4L3G9lJ8ioxVIp6ujiD0xrgI7cE"

    /** Cek apakah user ini admin (ada di tabel admins). */
    fun isAdmin(accessToken: String, userId: String): Boolean {
        val url = "$SUPABASE_URL/rest/v1/admins?select=id&user_id=eq.$userId"
        val response = get(url, accessToken)
        val arr = JSONArray(response)
        return arr.length() > 0
    }

    /**
     * Simpan / update sinopsis + status + genre (many-to-many) untuk 1 judul manga (upsert by title).
     * genreIds bisa lebih dari satu sekarang karena genre disimpan di tabel relasi manga_genres,
     * bukan kolom genre_id di tabel manga lagi.
     */
    fun upsertMangaMeta(accessToken: String, title: String, synopsis: String, statusId: Long?, genreIds: List<Long>) {
        // 1) Upsert baris manga (tanpa genre_id, karena kolom itu udah nggak dipakai)
        val mangaId = upsertManga(accessToken, title, synopsis, statusId)

        // 2) Sinkronkan manga_genres: hapus relasi lama, lalu insert yang baru sesuai pilihan checkbox
        deleteMangaGenres(accessToken, mangaId)
        if (genreIds.isNotEmpty()) {
            insertMangaGenres(accessToken, mangaId, genreIds)
        }
    }

    /** Upsert baris manga by title, kembalikan id-nya (dipakai buat relasi manga_genres). */
    private fun upsertManga(accessToken: String, title: String, synopsis: String, statusId: Long?): Long {
        val url = URL("$SUPABASE_URL/rest/v1/manga?on_conflict=title")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        // return=representation supaya kita dapat id manga-nya balik dari response
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        body.put("title", title)
        body.put("synopsis", synopsis)
        if (statusId != null) body.put("status_id", statusId) else body.put("status_id", JSONObject.NULL)

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal simpan data manga ($responseCode): $errorBody")
        }

        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val arr = JSONArray(responseBody)
        if (arr.length() == 0) throw Exception("Manga \"$title\" tersimpan tapi id tidak didapat dari server")
        return arr.getJSONObject(0).getLong("id")
    }

    /** Hapus semua relasi genre lama untuk manga ini (biar bisa di-replace dengan pilihan checkbox terbaru). */
    private fun deleteMangaGenres(accessToken: String, mangaId: Long) {
        val url = URL("$SUPABASE_URL/rest/v1/manga_genres?manga_id=eq.$mangaId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "DELETE"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal hapus genre lama ($responseCode): $errorBody")
        }
        connection.disconnect()
    }

    /** Insert baris baru ke manga_genres untuk tiap genre yang dicentang. */
    private fun insertMangaGenres(accessToken: String, mangaId: Long, genreIds: List<Long>) {
        val url = URL("$SUPABASE_URL/rest/v1/manga_genres")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val bodyArray = JSONArray()
        for (genreId in genreIds) {
            val row = JSONObject()
            row.put("manga_id", mangaId)
            row.put("genre_id", genreId)
            bodyArray.put(row)
        }

        connection.outputStream.use { it.write(bodyArray.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal simpan genre ($responseCode): $errorBody")
        }
        connection.disconnect()
    }

    /** Tambah admin baru lewat email (cuma berhasil kalau pemanggilnya udah admin, dicek di server). */
    fun addAdminByEmail(accessToken: String, email: String) {
        val url = URL("$SUPABASE_URL/rest/v1/rpc/add_admin_by_email")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        body.put("new_admin_email", email)

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val json = try { JSONObject(errorBody) } catch (e: Exception) { null }
            val message = json?.optString("message") ?: "Gagal menambah admin"
            throw Exception(message)
        }
        connection.disconnect()
    }

    private fun get(urlStr: String, accessToken: String): String {
        val connection = URL(urlStr).openConnection() as HttpURLConnection
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
