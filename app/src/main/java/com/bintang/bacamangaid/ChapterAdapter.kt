package com.bintang.bacamangaid

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChapterAdapter(
    private val chapters: List<Pair<Int, String>>, // (nomor chapter, download url pdf)
    private val onClick: (Int, String) -> Unit
) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.chapterLabel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chapter, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (num, url) = chapters[position]
        holder.label.text = "Chapter $num"
        holder.itemView.setOnClickListener { onClick(num, url) }
    }

    override fun getItemCount() = chapters.size
}
