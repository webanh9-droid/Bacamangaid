package com.bintang.bacamangaid

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AccountActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        val emailInput = findViewById<EditText>(R.id.emailInput)
        val passwordInput = findViewById<EditText>(R.id.passwordInput)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val statusText = findViewById<TextView>(R.id.statusText)

        fun refreshUi() {
            if (SessionManager.isLoggedIn(this)) {
                statusText.text = "Login sebagai: ${SessionManager.getEmail(this)}"
                btnLogout.visibility = View.VISIBLE
                emailInput.visibility = View.GONE
                passwordInput.visibility = View.GONE
                btnLogin.visibility = View.GONE
                btnRegister.visibility = View.GONE
            } else {
                statusText.text = "Belum login. Login biar dapat rekomendasi manga sesuai genre favoritmu."
                btnLogout.visibility = View.GONE
                emailInput.visibility = View.VISIBLE
                passwordInput.visibility = View.VISIBLE
                btnLogin.visibility = View.VISIBLE
                btnRegister.visibility = View.VISIBLE
            }
        }

        refreshUi()

        btnLogin.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Isi email & password dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    val result = AuthApi.signIn(email, password)
                    SessionManager.saveSession(this, result.accessToken, result.refreshToken, result.userId, result.email)
                    runOnUiThread {
                        Toast.makeText(this, "Login berhasil", Toast.LENGTH_SHORT).show()
                        refreshUi()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, e.message ?: "Login gagal", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

        btnRegister.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            if (email.isEmpty() || password.length < 6) {
                Toast.makeText(this, "Email wajib, password minimal 6 karakter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    val result = AuthApi.signUp(email, password)
                    SessionManager.saveSession(this, result.accessToken, result.refreshToken, result.userId, result.email)
                    runOnUiThread {
                        Toast.makeText(this, "Registrasi berhasil & login otomatis", Toast.LENGTH_SHORT).show()
                        refreshUi()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, e.message ?: "Registrasi gagal", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

        btnLogout.setOnClickListener {
            SessionManager.logout(this)
            refreshUi()
        }
    }
}
