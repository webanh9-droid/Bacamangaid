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

    /** Simpan / update sinopsis + genre untuk 1 judul manga (upsert by title). */
    fun upsertMangaMeta(accessToken: String, title: String, synopsis: String, genreId: Long?) {
        val url = URL("$SUPABASE_URL/rest/v1/manga?on_conflict=title")
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
        body.put("title", title)
        body.put("synopsis", synopsis)
        if (genreId != null) body.put("genre_id", genreId)

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw Exception("Gagal simpan data manga ($responseCode): $errorBody")
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
