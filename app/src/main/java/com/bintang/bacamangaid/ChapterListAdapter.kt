package com.bintang.bacamangaid

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL

private const val TYPE_HEADER = 0
private const val TYPE_CHAPTER = 1

class ChapterListAdapter(
    private val manga: MangaDisplay,
    private val chapters: List<Pair<Int, String>>, // (nomor chapter, url pdf)
    private val onChapterClick: (Int, String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.headerCover)
        val title: TextView = view.findViewById(R.id.headerTitle)
        val genre: TextView = view.findViewById(R.id.headerGenre)
        val synopsis: TextView = view.findViewById(R.id.headerSynopsis)
    }

    class ChapterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.chapterLabel)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_CHAPTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter, parent, false)
            ChapterViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.title.text = manga.title

            if (!manga.genreName.isNullOrBlank()) {
                holder.genre.text = manga.genreName
                holder.genre.visibility = View.VISIBLE
            } else {
                holder.genre.visibility = View.GONE
            }

            if (!manga.synopsis.isNullOrBlank()) {
                holder.synopsis.text = manga.synopsis
                holder.synopsis.visibility = View.VISIBLE
            } else {
                holder.synopsis.visibility = View.GONE
            }

            if (!manga.coverUrl.isNullOrBlank()) {
                Thread {
                    try {
                        val input = URL(manga.coverUrl).openStream()
                        val bitmap = BitmapFactory.decodeStream(input)
                        holder.cover.post { holder.cover.setImageBitmap(bitmap) }
                    } catch (e: Exception) { }
                }.start()
            }
        } else if (holder is ChapterViewHolder) {
            val (num, url) = chapters[position - 1]
            holder.label.text = "Chapter $num"
            holder.itemView.setOnClickListener { onChapterClick(num, url) }
        }
    }

    override fun getItemCount() = chapters.size + 1 // +1 buat header
}
