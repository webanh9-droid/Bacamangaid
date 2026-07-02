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
        val name: TextView   = view.findViewById(R.id.mangaName)
        val genre: TextView  = view.findViewById(R.id.mangaGenre)
        val status: TextView = view.findViewById(R.id.mangaStatus)
        val rating: TextView = view.findViewById(R.id.mangaRating)
        val views: TextView  = view.findViewById(R.id.mangaViews)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manga = items[position]
        holder.name.text = manga.title
        holder.cover.setImageDrawable(null)

        // Genre
        if (manga.genres.isNotEmpty()) {
            holder.genre.text = manga.genres.joinToString(" · ") { it.name }
            holder.genre.visibility = View.VISIBLE
        } else {
            holder.genre.visibility = View.GONE
        }

        // Status
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

        // Rating — selalu tampilkan (kosong kalau belum ada rating)
        if (manga.avgRating > 0f) {
            val full  = manga.avgRating.toInt()
            val half  = if (manga.avgRating - full >= 0.3f && full < 5) 1 else 0
            val empty = 5 - full - half
            val stars = "★".repeat(full) + "½".repeat(half) + "☆".repeat(empty)
            holder.rating.text = "$stars ${"%.1f".format(manga.avgRating)}"
        } else {
            holder.rating.text = "☆☆☆☆☆ Belum ada rating"
        }
        holder.rating.visibility = View.VISIBLE

        // Views — selalu tampilkan
        val viewText = when {
            manga.totalViews >= 1000 -> "${"%.1f".format(manga.totalViews / 1000f)}k"
            else -> manga.totalViews.toString()
        }
        holder.views.text = "👁 $viewText pembaca"
        holder.views.visibility = View.VISIBLE

        // Cover
        if (!manga.coverUrl.isNullOrBlank()) {
            Thread {
                try {
                    val bitmap = BitmapFactory.decodeStream(URL(manga.coverUrl).openStream())
                    holder.cover.post { holder.cover.setImageBitmap(bitmap) }
                } catch (e: Exception) { }
            }.start()
        }

        holder.itemView.setOnClickListener { onClick(manga.title) }
    }

    override fun getItemCount() = items.size
}
