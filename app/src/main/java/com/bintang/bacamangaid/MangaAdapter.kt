package com.bintang.bacamangaid

import android.graphics.BitmapFactory
import android.graphics.Color
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
        val status: TextView = view.findViewById(R.id.mangaStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manga = items[position]
        holder.name.text = manga.title
        holder.cover.setImageDrawable(null)

        // Genre tags — tampilkan semua genre dipisah koma
        if (manga.genres.isNotEmpty()) {
            holder.genre.text = manga.genres.joinToString(" · ") { it.name }
            holder.genre.visibility = View.VISIBLE
        } else {
            holder.genre.visibility = View.GONE
        }

        // Status tag dengan warna dinamis
        if (!manga.statusName.isNullOrBlank()) {
            holder.status.text = manga.statusName
            holder.status.visibility = View.VISIBLE

            val (bgHex, strokeHex) = when (manga.statusName.lowercase()) {
                "ongoing"   -> Pair("#0A2A14", "#22C55E")
                "completed" -> Pair("#0A1A3A", "#4D9FFF")
                "hiatus"    -> Pair("#2A1E00", "#F59E0B")
                "dropped"   -> Pair("#2A0A0A", "#EF4444")
                else        -> Pair("#1A2444", "#546480")
            }
            val bg = holder.status.background
            if (bg is android.graphics.drawable.GradientDrawable) {
                bg.setColor(Color.parseColor(bgHex))
                bg.setStroke(2, Color.parseColor(strokeHex))
            }
        } else {
            holder.status.visibility = View.GONE
        }

        // Load cover
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
