package com.bintang.bacamangaid

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tabManga: TextView
    private lateinit var tabNovel: TextView
    private var activeTab: String = "manga"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabManga = findViewById(R.id.tabManga)
        tabNovel = findViewById(R.id.tabNovel)

        tabManga.setOnClickListener { switchTab("manga") }
        tabNovel.setOnClickListener { switchTab("novel") }

        if (savedInstanceState == null) {
            switchTab("manga")
        } else {
            updateTabStyle()
        }
    }

    private fun switchTab(tab: String) {
        activeTab = tab
        updateTabStyle()
        supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MangaListFragment.newInstance(tab))
            .commit()
    }

    private fun updateTabStyle() {
        val activeColor = ContextCompat.getColor(this, R.color.accent_blue)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_dim)
        tabManga.setTextColor(if (activeTab == "manga") activeColor else inactiveColor)
        tabNovel.setTextColor(if (activeTab == "novel") activeColor else inactiveColor)
    }

    fun openChapterList(mangaId: Long, mangaTitle: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ChapterListFragment.newInstance(mangaId, mangaTitle))
            .addToBackStack(null)
            .commit()
    }
}
