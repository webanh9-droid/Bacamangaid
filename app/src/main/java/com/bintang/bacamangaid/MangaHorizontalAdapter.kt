package com.bintang.bacamangaid

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL

class MangaHorizontalAdapter(
    private val items: List<MangaDisplay>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<MangaHorizontalAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.mangaCover)
        val name: TextView = view.findViewById(R.id.mangaName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga_horizontal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manga = items[position]
        holder.name.text = manga.title
        holder.cover.setImageDrawable(null)

        if (!manga.coverUrl.isNullOrBlank()) {
            Thread {
                try {
                    val input = URL(manga.coverUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(input)
                    holder.cover.post { holder.cover.setImageBitmap(bitmap) }
                } catch (e: Exception) { }
            }.start()
        }

        holder.itemView.setOnClickListener { onClick(manga.title) }
    }

    override fun getItemCount() = items.size
}
