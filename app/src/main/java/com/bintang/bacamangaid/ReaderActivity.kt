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
        const val EXTRA_PDF_URL     = "pdf_url"
        const val EXTRA_TITLE       = "title"
        const val EXTRA_CHAPTER_NUM = "chapter_num"
        // ArrayList<String> berisi semua PDF url chapter berurutan (index 0 = chapter 1)
        const val EXTRA_ALL_PDF_URLS    = "all_pdf_urls"
        const val EXTRA_ALL_CHAPTER_NUMS = "all_chapter_nums"
    }

    private var allPdfUrls: ArrayList<String> = arrayListOf()
    private var allChapterNums: ArrayList<Int> = arrayListOf()
    private var currentChapterNum: Int = 0
    private var isLoadingNext = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        allPdfUrls    = intent.getStringArrayListExtra(EXTRA_ALL_PDF_URLS) ?: arrayListOf()
        allChapterNums = intent.getIntegerArrayListExtra(EXTRA_ALL_CHAPTER_NUMS) ?: arrayListOf()
        currentChapterNum = intent.getIntExtra(EXTRA_CHAPTER_NUM, 0)

        val pdfUrl = intent.getStringExtra(EXTRA_PDF_URL)
        val title  = intent.getStringExtra(EXTRA_TITLE) ?: "Reader"
        this.title = title

        val recyclerView      = findViewById<RecyclerView>(R.id.pageRecyclerView)
        val loadingContainer  = findViewById<View>(R.id.loadingContainer)
        val loadingStatusText = findViewById<TextView>(R.id.loadingStatusText)

        recyclerView.layoutManager = LinearLayoutManager(this)

        if (pdfUrl == null) {
            Toast.makeText(this, "URL PDF tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadChapter(pdfUrl, recyclerView, loadingContainer, loadingStatusText, resetScroll = true)
    }

    private fun loadChapter(
        pdfUrl: String,
        recyclerView: RecyclerView,
        loadingContainer: View,
        loadingStatusText: TextView,
        resetScroll: Boolean
    ) {
        loadingContainer.visibility = View.VISIBLE

        Thread {
            try {
                Handler(Looper.getMainLooper()).post {
                    loadingStatusText.text = "Mengunduh chapter..."
                }

                val cacheFile = File(cacheDir, "temp_chapter_${System.currentTimeMillis()}.pdf")
                URL(pdfUrl).openStream().use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }

                Handler(Looper.getMainLooper()).post {
                    loadingStatusText.text = "Merender halaman..."
                }

                val fd       = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pages    = mutableListOf<Bitmap>()
                val targetWidthPx = resources.displayMetrics.widthPixels

                for (i in 0 until renderer.pageCount) {
                    val page   = renderer.openPage(i)
                    val scale  = targetWidthPx.toFloat() / page.width
                    val width  = targetWidthPx
                    val height = (page.height * scale).toInt()

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                    page.close()
                }

                renderer.close()
                fd.close()
                cacheFile.delete()

                Handler(Looper.getMainLooper()).post {
                    loadingContainer.visibility = View.GONE
                    isLoadingNext = false

                    if (resetScroll) {
                        recyclerView.adapter = PageAdapter(pages)
                    } else {
                        // Append halaman chapter baru di bawah halaman yang sudah ada
                        val existing = (recyclerView.adapter as? PageAdapter)?.pages ?: emptyList()
                        recyclerView.adapter = PageAdapter(existing + pages)
                    }

                    // Pasang scroll listener buat auto-lanjut chapter berikutnya
                    recyclerView.clearOnScrollListeners()
                    recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                            if (isLoadingNext) return
                            val lm = rv.layoutManager as LinearLayoutManager
                            val lastVisible = lm.findLastVisibleItemPosition()
                            val total = rv.adapter?.itemCount ?: 0
                            // Trigger auto-load ketika 3 halaman terakhir mulai keliatan
                            if (total > 0 && lastVisible >= total - 3) {
                                loadNextChapter(rv, loadingContainer, loadingStatusText)
                            }
                        }
                    })
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    isLoadingNext = false
                    Toast.makeText(this, "Gagal memuat chapter: ${e.message}", Toast.LENGTH_LONG).show()
                    if (resetScroll) finish()
                }
            }
        }.start()
    }

    private fun loadNextChapter(
        recyclerView: RecyclerView,
        loadingContainer: View,
        loadingStatusText: TextView
    ) {
        val currentIndex = allChapterNums.indexOf(currentChapterNum)
        val nextIndex    = currentIndex + 1

        if (currentIndex < 0 || nextIndex >= allChapterNums.size) {
            // Sudah chapter terakhir — hapus listener biar gak terus dicek
            recyclerView.clearOnScrollListeners()
            return
        }

        isLoadingNext = true
        currentChapterNum = allChapterNums[nextIndex]
        val nextUrl = allPdfUrls[nextIndex]

        // Update judul activity
        runOnUiThread {
            title = title.toString().replaceAfterLast("- Chapter ", "$currentChapterNum")
            Toast.makeText(this, "Lanjut Chapter $currentChapterNum...", Toast.LENGTH_SHORT).show()
        }

        loadChapter(nextUrl, recyclerView, loadingContainer, loadingStatusText, resetScroll = false)
    }
}
