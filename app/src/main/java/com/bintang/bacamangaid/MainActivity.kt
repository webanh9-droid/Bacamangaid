package com.bintang.bacamangaid

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MangaListFragment())
                .commit()
        }
    }

    fun openChapterList(mangaTitle: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, ChapterListFragment.newInstance(mangaTitle))
            .addToBackStack(null)
            .commit()
    }
}
