package com.bintang.bacamangaid

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.net.URL

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PDF_URL          = "pdf_url"
        const val EXTRA_TITLE            = "title"
        const val EXTRA_MANGA_TITLE      = "manga_title"
        const val EXTRA_CHAPTER_NUM      = "chapter_num"
        const val EXTRA_ALL_PDF_URLS     = "all_pdf_urls"
        const val EXTRA_ALL_CHAPTER_NUMS = "all_chapter_nums"
    }

    private var allPdfUrls: ArrayList<String>  = arrayListOf()
    private var allChapterNums: ArrayList<Int> = arrayListOf()
    private var currentChapterNum: Int = 0
    private var mangaTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        allPdfUrls      = intent.getStringArrayListExtra(EXTRA_ALL_PDF_URLS) ?: arrayListOf()
        allChapterNums  = intent.getIntegerArrayListExtra(EXTRA_ALL_CHAPTER_NUMS) ?: arrayListOf()
        currentChapterNum = intent.getIntExtra(EXTRA_CHAPTER_NUM, 0)
        mangaTitle      = intent.getStringExtra(EXTRA_MANGA_TITLE) ?: ""

        val pdfUrl = intent.getStringExtra(EXTRA_PDF_URL)
        val displayTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Reader"
        title = displayTitle

        val recyclerView      = findViewById<RecyclerView>(R.id.pageRecyclerView)
        val loadingContainer  = findViewById<View>(R.id.loadingContainer)
        val loadingStatusText = findViewById<TextView>(R.id.loadingStatusText)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (pdfUrl == null) {
            Toast.makeText(this, "URL PDF tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadChapter(pdfUrl, recyclerView, loadingContainer, loadingStatusText)
    }

    private fun loadChapter(
        pdfUrl: String,
        recyclerView: RecyclerView,
        loadingContainer: View,
        loadingStatusText: TextView
    ) {
        loadingContainer.visibility = View.VISIBLE
        title = "$mangaTitle - Chapter $currentChapterNum"

        Thread {
            try {
                Handler(Looper.getMainLooper()).post { loadingStatusText.text = "Mengunduh chapter..." }

                val cacheFile = File(cacheDir, "temp_chapter_${System.currentTimeMillis()}.pdf")
                URL(pdfUrl).openStream().use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }

                Handler(Looper.getMainLooper()).post { loadingStatusText.text = "Merender halaman..." }

                val fd       = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pages    = mutableListOf<Bitmap>()
                val targetWidthPx = resources.displayMetrics.widthPixels

                for (i in 0 until renderer.pageCount) {
                    val page   = renderer.openPage(i)
                    val scale  = targetWidthPx.toFloat() / page.width
                    val bitmap = Bitmap.createBitmap(targetWidthPx, (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                    page.close()
                }
                renderer.close()
                fd.close()
                cacheFile.delete()

                // Ambil rating yang sudah pernah dikasih user (kalau login)
                val token  = SessionManager.getAccessToken(this)
                val userId = SessionManager.getUserId(this)
                val savedRating = if (token != null && userId != null) {
                    try { SupabaseApi.fetchRating(token, userId, mangaTitle, currentChapterNum) }
                    catch (e: Exception) { 0 }
                } else 0

                Handler(Looper.getMainLooper()).post {
                    loadingContainer.visibility = View.GONE
                    (recyclerView.layoutManager as LinearLayoutManager).scrollToPosition(0)

                    recyclerView.adapter = PageAdapter(
                        pages         = pages,
                        allChapterNums = allChapterNums.toList(),
                        currentChapterNum = currentChapterNum,
                        savedRating   = savedRating,
                        onPrevChapter = { navigateChapter(currentChapterNum - 1, recyclerView, loadingContainer, loadingStatusText) },
                        onNextChapter = { navigateChapter(currentChapterNum + 1, recyclerView, loadingContainer, loadingStatusText) },
                        onJumpChapter = { num -> navigateChapter(num, recyclerView, loadingContainer, loadingStatusText) },
                        onRate        = { rating ->
                            if (token != null && userId != null) {
                                Thread {
                                    try { SupabaseApi.submitRating(token, userId, mangaTitle, currentChapterNum, rating) }
                                    catch (e: Exception) { }
                                }.start()
                                runOnUiThread { Toast.makeText(this, "Rating $rating ★ tersimpan!", Toast.LENGTH_SHORT).show() }
                            } else {
                                Toast.makeText(this, "Login dulu untuk kasih rating", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Gagal memuat chapter: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }

    private fun navigateChapter(
        targetNum: Int,
        recyclerView: RecyclerView,
        loadingContainer: View,
        loadingStatusText: TextView
    ) {
        val index = allChapterNums.indexOf(targetNum)
        if (index < 0) return
        currentChapterNum = targetNum
        val url = allPdfUrls[index]
        loadChapter(url, recyclerView, loadingContainer, loadingStatusText)
    }
}
