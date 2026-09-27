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

    private var mangaOptions: List<MangaMeta> = emptyList()
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
            val token = try {
                freshToken()
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
                    setupAdminUi()
                }
            }
        }.start()
    }

    /**
     * Selalu refresh access token dulu sebelum dipakai, jangan pakai token lama yang di-capture
     * di awal — access token Supabase umurnya cuma 1 jam, kalau admin panel dibiarkan terbuka
     * lebih lama dari itu, token lama bakal expired dan semua request kelihatan kayak "akun hilang"
     * padahal cuma token basi. Dipanggil ulang di tiap aksi (save, upload, add admin), bukan cuma sekali.
     */
    private fun freshToken(): String {
        val refreshToken = SessionManager.getRefreshToken(this)
            ?: return SessionManager.getAccessToken(this) ?: throw Exception("Belum login")
        return try {
            val refreshed = AuthApi.refreshSession(refreshToken)
            SessionManager.saveSession(this, refreshed.accessToken, refreshed.refreshToken, refreshed.userId, refreshed.email)
            refreshed.accessToken
        } catch (e: Exception) {
            // fallback ke access token lama yang masih tersimpan, kalau-kalau refresh endpoint lagi gangguan
            SessionManager.getAccessToken(this) ?: throw e
        }
    }

    /**
     * Pastikan ada baris manga di database buat judul yang lagi aktif, balikin (id, title)-nya:
     * - kalau input "judul manga baru" diisi -> get-or-create by title itu (bikin baris baru kalau
     *   belum ada, atau pakai yang sudah ada kalau ternyata judulnya sama — jadi typo pun aman,
     *   nggak bakal kebuat manga duplikat, tinggal nyambung ke manga yang sudah ada)
     * - kalau kosong -> pakai manga yang dipilih di spinner (idnya sudah pasti valid dari database)
     * null kalau nggak ada satupun manga buat dipakai (spinner kosong & input judul baru kosong).
     */
    private fun resolveActiveManga(token: String, newTitleInput: EditText, mangaSpinner: Spinner): Pair<Long, String>? {
        val newTitle = newTitleInput.text.toString().trim()
        if (newTitle.isNotEmpty()) {
            val id = AdminApi.getOrCreateMangaId(token, newTitle)
            return Pair(id, newTitle)
        }
        if (mangaOptions.isEmpty()) return null
        val selected = mangaOptions.getOrNull(mangaSpinner.selectedItemPosition) ?: return null
        return Pair(selected.id, selected.title)
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

    private fun setupAdminUi() {
        val mangaSpinner = findViewById<Spinner>(R.id.mangaSpinner)
        val newTitleInput = findViewById<EditText>(R.id.newMangaTitleInput)
        val genreCheckboxContainer = findViewById<LinearLayout>(R.id.genreCheckboxContainer)
        val statusSpinner = findViewById<Spinner>(R.id.statusSpinner)
        val synopsisInput = findViewById<EditText>(R.id.synopsisInput)
        val chapterNumberInput = findViewById<EditText>(R.id.chapterNumberInput)
        val newAdminEmailInput = findViewById<EditText>(R.id.newAdminEmailInput)

        Thread {
            try {
                // Daftar manga buat spinner sekarang datang dari tabel manga di Supabase (punya id),
                // bukan hasil scan nama file PDF di repo GitHub lagi.
                mangaOptions = try { SupabaseApi.fetchAllManga().sortedBy { it.title.lowercase() } } catch (e: Exception) { emptyList() }
                genres = try { SupabaseApi.fetchGenres() } catch (e: Exception) { emptyList() }
                statuses = try { SupabaseApi.fetchStatuses() } catch (e: Exception) { emptyList() }

                runOnUiThread {
                    mangaSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, mangaOptions.map { it.title }
                    )

                    populateGenreCheckboxes(genreCheckboxContainer, genres)

                    val statusNames = statuses.map { it.name }
                    statusSpinner.adapter = ArrayAdapter(
                        this, android.R.layout.simple_spinner_dropdown_item, statusNames
                    )

                    if (mangaOptions.isEmpty()) {
                        Toast.makeText(
                            this,
                            "Belum ada manga. Isi 'judul manga baru' di bawah buat mulai upload chapter pertama.",
                            Toast.LENGTH_LONG
                        ).show()
                        chapterNumberInput.setText("1")
                    } else {
                        suggestNextChapterNumber(mangaOptions[0].id, chapterNumberInput)
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
                if (mangaOptions.isNotEmpty() && newTitleInput.text.toString().isBlank()) {
                    suggestNextChapterNumber(mangaOptions[pos].id, chapterNumberInput)
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
            val synopsis = synopsisInput.text.toString()
            val genreIds = getSelectedGenreIds(genreCheckboxContainer)
            val statusId = statuses.getOrNull(statusSpinner.selectedItemPosition)?.id

            Thread {
                try {
                    val token = freshToken()
                    val active = resolveActiveManga(token, newTitleInput, mangaSpinner)
                    if (active == null) {
                        runOnUiThread { Toast.makeText(this, "Pilih manga atau isi judul manga baru dulu", Toast.LENGTH_SHORT).show() }
                        return@Thread
                    }
                    val (mangaId, title) = active
                    AdminApi.upsertMangaMeta(token, mangaId, synopsis, statusId, genreIds)
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
            val chapterNum = chapterNumberInput.text.toString().toIntOrNull()
            if (chapterNum == null) {
                Toast.makeText(this, "Isi nomor chapter dulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Mengupload chapter $chapterNum...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val token = freshToken()
                    val active = resolveActiveManga(token, newTitleInput, mangaSpinner)
                        ?: throw Exception("Pilih manga atau isi judul manga baru dulu")
                    val (mangaId, title) = active

                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Gagal baca file")
                    val fileName = "$title Chapter $chapterNum.pdf"
                    val pdfUrl = GitHubWriteApi.uploadFile(fileName, bytes, "Upload $fileName lewat admin panel")
                    // Catat chapter ini ke database (manga_id + nomor + url) — sekali insert, gak perlu
                    // scan ulang semua file tiap buka app lagi.
                    AdminApi.insertChapter(token, mangaId, chapterNum, pdfUrl)

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

            Toast.makeText(this, "Mengupload cover...", Toast.LENGTH_SHORT).show()

            Thread {
                try {
                    val token = freshToken()
                    val active = resolveActiveManga(token, newTitleInput, mangaSpinner)
                        ?: throw Exception("Pilih manga atau isi judul manga baru dulu")
                    val (mangaId, title) = active

                    val bytes = contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Gagal baca gambar")
                    val mime = contentResolver.getType(uri) ?: ""
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val fileName = "$title Cover.$ext"
                    val coverUrl = GitHubWriteApi.uploadFile(fileName, bytes, "Upload cover $title lewat admin panel")
                    // Simpan langsung URL cover-nya ke baris manga di database.
                    AdminApi.updateMangaCoverUrl(token, mangaId, coverUrl)

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
                    val token = freshToken()
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

    private fun suggestNextChapterNumber(mangaId: Long, chapterNumberInput: EditText) {
        Thread {
            try {
                val nextNum = SupabaseApi.fetchMaxChapterNumber(mangaId) + 1
                runOnUiThread { chapterNumberInput.setText(nextNum.toString()) }
            } catch (e: Exception) { }
        }.start()
    }
}
