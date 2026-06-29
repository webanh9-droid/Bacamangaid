package com.bintang.bacamangaid

import android.content.Context

object SessionManager {
    private const val PREF_NAME = "bacamangaid_session"

    fun saveSession(context: Context, accessToken: String, userId: String, email: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("user_id", userId)
            .putString("email", email)
            .apply()
    }

    fun getAccessToken(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("access_token", null)

    fun getUserId(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("user_id", null)

    fun getEmail(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString("email", null)

    fun isLoggedIn(context: Context): Boolean = getAccessToken(context) != null

    fun logout(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
