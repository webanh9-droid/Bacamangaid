package com.bintang.bacamangaid

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {

    private var mangaTitles: List<String> = emptyList()
    private var genres: List<GenreItem> = emptyList()

    private var selectedPdfUri: Uri? = null
    private var selectedCoverUri: Uri? = null

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedPdfUri = uri
            findViewById<TextView>(R.id.selectedPdfName).text = uri.lastPathSegment ?: "File terpilih"
        }
    }

    private val pickCoverLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedCoverUri = uri
            findViewById<TextView>(R.id.selectedCoverName).text = uri.lastPathSegment ?: "Gambar terpilih"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val token = SessionManager.getAccessToken(this)
        val userId = SessionManager.getUserId(this)

        if (token == null || userId == null) {
            Toast.makeText(this, "Login dulu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Cek status admin dulu sebelum tampilin apa pun
        Thread {
            val isAdmin = try {
                AdminApi.isAdmin(token, userId)
            } catch (e: Exception) {
                false
            }

            runOnUiThread {
                if (!isAdmin) {
                    Toast.makeText(this, "Akun ini bukan admin", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    setupAdminUi(token)
                }
            }
        }.start()
    }

    private fun setupAdminUi(token: String) {
        val mangaSpinner = findViewById<Spinner>(R.id.mangaSpinner)
        val genreSpinner = findViewById<Spinner>(R.id.genreSpinner)
        val synopsisInput = findViewById<EditText>(R.id.synopsisInput)
        val chapterNumberInput = findViewById<EditText>(R.id.chapterNumberInput)
        val newAdminEmailInput = findViewById<EditText>(R.id.newAdminEmailInput)

        // Load daftar manga (dari GitHub) + genre (dari Supabase)
        Thread {
            try {
                mangaTitles = GitHubApi.listMangaTitles()
                genres = try { SupabaseApi.fetchGenres() } catch (e: Exception) { emptyList() }

                runOnUiThread {
                    mangaSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, mangaTitles
                    )

                    val genreNames = genres.map { it.name }
                    genreSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, genreNames
                    )

                    if (mangaTitles.isNotEmpty()) {
                        suggestNextChapterNumber(mangaTitles[0], chapterNumberInput)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal memuat daftar manga/genre", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()

        mangaSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                if (mangaTitles.isNotEmpty()) {
                    suggestNextChapterNumber(mangaTitles[pos], chapterNumberInput)
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })

        findViewById<Button>(R.id.btnSaveMeta).setOnClickListener {
            if (mangaTitles.isEmpty()) return@setOnClickListener
            val title = mangaTitles[mangaSpinner.selectedItemPosition]
            val synopsis = synopsisInput.text.toString()
            val genreId = genres.getOrNull(genreSpinner.selectedItemPosition)?.id

            Thread {
                try {
                    AdminApi.upsertMangaMeta(token, title, synopsis, genreId)
                    runOnUiThread { Toast.makeText(this, "Sinopsis & genre disimpan", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, e.message ?: "Gagal menyimpan", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

        findViewById<Button>(R.id.btnPickPdf).setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        findViewById<Button>(R.id.btnUploadPdf).setOnClickListener {
            val uri = selectedPdfUri
            if (uri == null) {
                Toast.makeText(this, "Pilih file PDF dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mangaTitles.isEmpty()) return@setOnClickListener

            val title = mangaTitles[mangaSpinner.selectedItemPosition]
            val chapterNum = chapterNumberInput.text.toString().toIntOrNull()
            if (chapterNum == null) {
                Toast.makeText(this, "Isi nomor chapter dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Gagal baca file")
                    val fileName = "$title Chapter $chapterNum.pdf"
                    GitHubWriteApi.uploadFile(fileName, bytes, "Upload $fileName lewat admin panel")
                    runOnUiThread {
                        Toast.makeText(this, "Chapter $chapterNum berhasil di-upload!", Toast.LENGTH_LONG).show()
                        findViewById<TextView>(R.id.selectedPdfName).text = "Belum ada file dipilih"
                        selectedPdfUri = null
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, e.message ?: "Upload gagal", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

        findViewById<Button>(R.id.btnPickCover).setOnClickListener {
            pickCoverLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btnUploadCover).setOnClickListener {
            val uri = selectedCoverUri
            if (uri == null) {
                Toast.makeText(this, "Pilih gambar cover dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (mangaTitles.isEmpty()) return@setOnClickListener

            val title = mangaTitles[mangaSpinner.selectedItemPosition]

            Thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Gagal baca gambar")
                    val mime = contentResolver.getType(uri) ?: ""
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val fileName = "$title Cover.$ext"
                    GitHubWriteApi.uploadFile(fileName, bytes, "Upload cover $title lewat admin panel")
                    runOnUiThread {
                        Toast.makeText(this, "Cover berhasil di-upload!", Toast.LENGTH_LONG).show()
                        findViewById<TextView>(R.id.selectedCoverName).text = "Belum ada gambar dipilih"
                        selectedCoverUri = null
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, e.message ?: "Upload gagal", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

        findViewById<Button>(R.id.btnAddAdmin).setOnClickListener {
            val email = newAdminEmailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Isi email dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Thread {
                try {
                    AdminApi.addAdminByEmail(token, email)
                    runOnUiThread {
                        Toast.makeText(this, "$email berhasil dijadikan admin", Toast.LENGTH_LONG).show()
                        newAdminEmailInput.setText("")
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, e.message ?: "Gagal menambah admin", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }
    }

    private fun suggestNextChapterNumber(title: String, chapterNumberInput: EditText) {
        Thread {
            try {
                val chapters = GitHubApi.listChaptersForTitle(title)
                val nextNum = (chapters.maxOfOrNull { it.first } ?: 0) + 1
                runOnUiThread { chapterNumberInput.setText(nextNum.toString()) }
            } catch (e: Exception) { }
        }.start()
    }
}
