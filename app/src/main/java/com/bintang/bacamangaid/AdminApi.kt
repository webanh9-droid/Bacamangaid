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
     * Ambil id manga by title kalau sudah ada (exact match); kalau belum, bikin baris baru
     * (cuma title doang, kolom lain kosong) dan balikin id-nya.
     * Dipakai sekali doang per manga (baik pas nambah manga baru, atau resolve id manga yang
     * dipilih dari spinner) — SETELAHNYA semua relasi (chapter, genre, dst) pakai id ini,
     * jadi nggak ada lagi ketergantungan sama title harus persis sama di semua tempat.
     */
    fun getOrCreateMangaId(accessToken: String, title: String): Long {
        val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
        val selectUrl = "$SUPABASE_URL/rest/v1/manga?select=id&title=eq.$encodedTitle"
        val existingArr = JSONArray(get(selectUrl, accessToken))
        if (existingArr.length() > 0) return existingArr.getJSONObject(0).getLong("id")

        val url = URL("$SUPABASE_URL/rest/v1/manga")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "return=representation")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        body.put("title", title)
        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal bikin manga baru ($responseCode): $errorBody")
        }
        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val arr = JSONArray(responseBody)
        if (arr.length() == 0) throw Exception("Manga \"$title\" tersimpan tapi id tidak didapat dari server")
        return arr.getJSONObject(0).getLong("id")
    }

    /**
     * Simpan / update sinopsis + status + genre (many-to-many) untuk 1 manga by id (bukan title lagi).
     * genreIds bisa lebih dari satu sekarang karena genre disimpan di tabel relasi manga_genres,
     * bukan kolom genre_id di tabel manga lagi.
     */
    fun upsertMangaMeta(accessToken: String, mangaId: Long, synopsis: String, statusId: Long?, genreIds: List<Long>) {
        updateMangaFields(accessToken, mangaId, synopsis = synopsis, statusId = statusId)

        // Sinkronkan manga_genres: hapus relasi lama, lalu insert yang baru sesuai pilihan checkbox
        deleteMangaGenres(accessToken, mangaId)
        if (genreIds.isNotEmpty()) {
            insertMangaGenres(accessToken, mangaId, genreIds)
        }
    }

    /** Simpan chapter baru (atau replace PDF-nya kalau nomor chapter itu sudah ada) — upsert by (manga_id, chapter_number). */
    fun insertChapter(accessToken: String, mangaId: Long, chapterNumber: Int, pdfUrl: String) {
        val url = URL("$SUPABASE_URL/rest/v1/chapters?on_conflict=manga_id,chapter_number")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Prefer", "resolution=merge-duplicates")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        body.put("manga_id", mangaId)
        body.put("chapter_number", chapterNumber)
        body.put("pdf_url", pdfUrl)
        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal simpan chapter ke database ($responseCode): $errorBody")
        }
        connection.disconnect()
    }

    /** Update URL cover manga (dipanggil setelah upload cover ke GitHub berhasil). */
    fun updateMangaCoverUrl(accessToken: String, mangaId: Long, coverUrl: String) {
        updateMangaFields(accessToken, mangaId, coverUrl = coverUrl)
    }

    /** Helper umum buat PATCH sebagian kolom tabel manga by id (cuma kirim field yang di-isi). */
    private fun updateMangaFields(
        accessToken: String,
        mangaId: Long,
        synopsis: String? = null,
        statusId: Long? = null,
        coverUrl: String? = null
    ) {
        val url = URL("$SUPABASE_URL/rest/v1/manga?id=eq.$mangaId")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "PATCH"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        if (synopsis != null) body.put("synopsis", synopsis)
        if (statusId != null) body.put("status_id", statusId) else if (synopsis != null) body.put("status_id", JSONObject.NULL)
        if (coverUrl != null) body.put("cover_url", coverUrl)
        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal update data manga ($responseCode): $errorBody")
        }
        connection.disconnect()
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
