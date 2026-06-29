package com.bintang.bacamangaid

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AuthResult(val accessToken: String, val userId: String, val email: String)

object AuthApi {

    private const val SUPABASE_URL = "https://epuyvcwrdrltxbhdegsi.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImVwdXl2Y3dyZHJsdHhiaGRlZ3NpIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzIwODQzNDcsImV4cCI6MjA4NzY2MDM0N30.kOZ381kxAGFkI_rz4L3G9lJ8ioxVIp6ujiD0xrgI7cE"

    fun signUp(email: String, password: String): AuthResult {
        return postAuth("$SUPABASE_URL/auth/v1/signup", email, password)
    }

    fun signIn(email: String, password: String): AuthResult {
        return postAuth("$SUPABASE_URL/auth/v1/token?grant_type=password", email, password)
    }

    private fun postAuth(urlStr: String, email: String, password: String): AuthResult {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val body = JSONObject()
        body.put("email", email)
        body.put("password", password)

        connection.outputStream.use { it.write(body.toString().toByteArray()) }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }
        val json = JSONObject(responseText)

        if (responseCode !in 200..299) {
            val message = json.optString("msg", json.optString("error_description", "Login/registrasi gagal"))
            throw Exception(message)
        }

        val accessToken = json.optString("access_token", "")
        val userObj = json.optJSONObject("user")
        val userId = userObj?.optString("id") ?: ""
        val userEmail = userObj?.optString("email") ?: email

        if (accessToken.isEmpty() || userId.isEmpty()) {
            throw Exception("Registrasi berhasil. Cek email untuk konfirmasi, lalu login.")
        }

        return AuthResult(accessToken, userId, userEmail)
    }
}
