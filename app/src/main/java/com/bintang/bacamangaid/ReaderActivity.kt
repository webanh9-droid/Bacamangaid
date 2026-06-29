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
        const val EXTRA_PDF_URL = "pdf_url"
        const val EXTRA_TITLE = "title"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val pdfUrl = intent.getStringExtra(EXTRA_PDF_URL)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reader"
        this.title = title

        val recyclerView = findViewById<RecyclerView>(R.id.pageRecyclerView)
        val loadingContainer = findViewById<View>(R.id.loadingContainer)
        val loadingStatusText = findViewById<TextView>(R.id.loadingStatusText)

        recyclerView.layoutManager = LinearLayoutManager(this)

        if (pdfUrl == null) {
            Toast.makeText(this, "URL PDF tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Thread {
            try {
                // 1. Download PDF ke file cache lokal
                Handler(Looper.getMainLooper()).post {
                    loadingStatusText.text = "Mengunduh chapter..."
                }

                val cacheFile = File(cacheDir, "temp_chapter_${System.currentTimeMillis()}.pdf")
                URL(pdfUrl).openStream().use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 2. Render tiap halaman PDF jadi Bitmap
                Handler(Looper.getMainLooper()).post {
                    loadingStatusText.text = "Merender halaman..."
                }

                val fd = ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val pages = mutableListOf<Bitmap>()

                val targetWidthPx = resources.displayMetrics.widthPixels

                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    val scale = targetWidthPx.toFloat() / page.width
                    val width = targetWidthPx
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
                    recyclerView.adapter = PageAdapter(pages)
                }

            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this, "Gagal memuat chapter: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }.start()
    }
}
