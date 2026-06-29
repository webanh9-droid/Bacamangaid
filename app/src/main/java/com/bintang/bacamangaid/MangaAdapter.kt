package com.bintang.bacamangaid

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL

class MangaAdapter(
    private val items: List<MangaDisplay>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<MangaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.mangaCover)
        val name: TextView = view.findViewById(R.id.mangaName)
        val genre: TextView = view.findViewById(R.id.mangaGenre)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manga = items[position]
        holder.name.text = manga.title
        holder.cover.setImageDrawable(null)

        if (!manga.genreName.isNullOrBlank()) {
            holder.genre.text = manga.genreName
            holder.genre.visibility = View.VISIBLE
        } else {
            holder.genre.visibility = View.GONE
        }

        if (!manga.coverUrl.isNullOrBlank()) {
            Thread {
                try {
                    val input = URL(manga.coverUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(input)
                    holder.cover.post { holder.cover.setImageBitmap(bitmap) }
                } catch (e: Exception) {
                    // biarkan kosong kalau gagal load cover
                }
            }.start()
        }

        holder.itemView.setOnClickListener { onClick(manga.title) }
    }

    override fun getItemCount() = items.size
}
