package com.bintang.bacamangaid

import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {

    private var mangaTitles: List<String> = emptyList()
    private var genres: List<GenreItem> = emptyList()
    private var statuses: List<StatusItem> = emptyList()

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

        val storedToken = SessionManager.getAccessToken(this)
        val userId = SessionManager.getUserId(this)

        if (storedToken == null || userId == null) {
            Toast.makeText(this, "Login dulu", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Thread {
            // Coba refresh token dulu biar session gak expired — kalau gagal, pakai token lama
            val token = try {
                val refreshToken = SessionManager.getRefreshToken(this)
                if (!refreshToken.isNullOrEmpty()) {
                    val refreshed = AuthApi.refreshSession(refreshToken)
                    SessionManager.saveSession(this, refreshed.accessToken, refreshed.refreshToken, refreshed.userId, refreshed.email)
                    refreshed.accessToken
                } else {
                    storedToken
                }
            } catch (e: Exception) {
                storedToken // fallback ke token lama kalau refresh gagal
            }

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

    /** Ambil judul manga yang dipakai: prioritaskan input judul baru kalau diisi, kalau kosong pakai pilihan spinner. */
    private fun getActiveTitle(newTitleInput: EditText, mangaSpinner: Spinner): String? {
        val newTitle = newTitleInput.text.toString().trim()
        if (newTitle.isNotEmpty()) return newTitle
        if (mangaTitles.isEmpty()) return null
        return mangaTitles.getOrNull(mangaSpinner.selectedItemPosition)
    }

    /** Bikin 1 CheckBox per genre di dalam container, masing-masing pakai genre.id sebagai tag. */
    private fun populateGenreCheckboxes(container: LinearLayout, genreList: List<GenreItem>) {
        container.removeAllViews()
        for (genre in genreList) {
            val checkBox = CheckBox(this)
            checkBox.text = genre.name
            checkBox.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            checkBox.tag = genre.id
            container.addView(checkBox)
        }
    }

    /** Baca semua CheckBox yang sedang dicentang di container, kembalikan list genre id-nya. */
    private fun getSelectedGenreIds(container: LinearLayout): List<Long> {
        val selected = mutableListOf<Long>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                (child.tag as? Long)?.let { selected.add(it) }
            }
        }
        return selected
    }

    private fun setupAdminUi(token: String) {
        val mangaSpinner = findViewById<Spinner>(R.id.mangaSpinner)
        val newTitleInput = findViewById<EditText>(R.id.newMangaTitleInput)
        val genreCheckboxContainer = findViewById<LinearLayout>(R.id.genreCheckboxContainer)
        val statusSpinner = findViewById<Spinner>(R.id.statusSpinner)
        val synopsisInput = findViewById<EditText>(R.id.synopsisInput)
        val chapterNumberInput = findViewById<EditText>(R.id.chapterNumberInput)
        val newAdminEmailInput = findViewById<EditText>(R.id.newAdminEmailInput)

        Thread {
            try {
                mangaTitles = GitHubApi.listMangaTitles()
                genres = try { SupabaseApi.fetchGenres() } catch (e: Exception) { emptyList() }
                statuses = try { SupabaseApi.fetchStatuses() } catch (e: Exception) { emptyList() }

                runOnUiThread {
                    mangaSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, mangaTitles
                    )

                    populateGenreCheckboxes(genreCheckboxContainer, genres)

                    val statusNames = statuses.map { it.name }
                    statusSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, statusNames
                    )

                    if (mangaTitles.isEmpty()) {
                        Toast.makeText(
                            this,
                            "Belum ada manga di repo. Isi 'judul manga baru' di bawah buat mulai upload chapter pertama.",
                            Toast.LENGTH_LONG
                        ).show()
                        chapterNumberInput.setText("1")
                    } else {
                        suggestNextChapterNumber(mangaTitles[0], chapterNumberInput)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Gagal memuat daftar manga/genre: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()

        mangaSpinner.setOnItemSelectedListener(object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: android.view.View?, pos: Int, id: Long) {
                if (mangaTitles.isNotEmpty() && newTitleInput.text.toString().isBlank()) {
                    suggestNextChapterNumber(mangaTitles[pos], chapterNumberInput)
                }
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        })

        newTitleInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                if (!s.isNullOrBlank()) {
                    chapterNumberInput.setText("1") // manga baru, mulai dari chapter 1
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        findViewById<Button>(R.id.btnSaveMeta).setOnClickListener {
            val title = getActiveTitle(newTitleInput, mangaSpinner)
            if (title == null) {
                Toast.makeText(this, "Pilih manga atau isi judul manga baru dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val synopsis = synopsisInput.text.toString()
            val genreIds = getSelectedGenreIds(genreCheckboxContainer)
            val statusId = statuses.getOrNull(statusSpinner.selectedItemPosition)?.id

            Thread {
                try {
                    AdminApi.upsertMangaMeta(token, title, synopsis, statusId, genreIds)
                    runOnUiThread { Toast.makeText(this, "Sinopsis, status & genre disimpan untuk \"$title\"", Toast.LENGTH_SHORT).show() }
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
            val title = getActiveTitle(newTitleInput, mangaSpinner)
            if (title == null) {
                Toast.makeText(this, "Pilih manga atau isi judul manga baru dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val chapterNum = chapterNumberInput.text.toString().toIntOrNull()
            if (chapterNum == null) {
                Toast.makeText(this, "Isi nomor chapter dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Mengupload chapter $chapterNum untuk \"$title\"...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Gagal baca file")
                    val fileName = "$title Chapter $chapterNum.pdf"
                    GitHubWriteApi.uploadFile(fileName, bytes, "Upload $fileName lewat admin panel")
                    runOnUiThread {
                        Toast.makeText(this, "Chapter $chapterNum \"$title\" berhasil di-upload!", Toast.LENGTH_LONG).show()
                        findViewById<TextView>(R.id.selectedPdfName).text = "Belum ada file dipilih"
                        selectedPdfUri = null
                        newTitleInput.setText("")
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Upload gagal: ${e.message}", Toast.LENGTH_LONG).show() }
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
            val title = getActiveTitle(newTitleInput, mangaSpinner)
            if (title == null) {
                Toast.makeText(this, "Pilih manga atau isi judul manga baru dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Mengupload cover untuk \"$title\"...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Gagal baca gambar")
                    val mime = contentResolver.getType(uri) ?: ""
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val fileName = "$title Cover.$ext"
                    GitHubWriteApi.uploadFile(fileName, bytes, "Upload cover $title lewat admin panel")
                    runOnUiThread {
                        Toast.makeText(this, "Cover \"$title\" berhasil di-upload!", Toast.LENGTH_LONG).show()
                        findViewById<TextView>(R.id.selectedCoverName).text = "Belum ada gambar dipilih"
                        selectedCoverUri = null
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Upload gagal: ${e.message}", Toast.LENGTH_LONG).show() }
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
