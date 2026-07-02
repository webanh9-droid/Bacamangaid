package com.bintang.bacamangaid

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

private const val TYPE_PAGE   = 0
private const val TYPE_FOOTER = 1

class PageAdapter(
    val pages: List<Bitmap>,
    private val allChapterNums: List<Int>,
    private val currentChapterNum: Int,
    private val onPrevChapter: () -> Unit,
    private val onNextChapter: () -> Unit,
    private val onJumpChapter: (Int) -> Unit,
    private val onRate: (Int) -> Unit,
    private var savedRating: Int = 0
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.pageImage)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val star1: TextView = view.findViewById(R.id.star1)
        val star2: TextView = view.findViewById(R.id.star2)
        val star3: TextView = view.findViewById(R.id.star3)
        val star4: TextView = view.findViewById(R.id.star4)
        val star5: TextView = view.findViewById(R.id.star5)
        val chapterListContainer: LinearLayout = view.findViewById(R.id.chapterListContainer)
        val btnPrev: Button = view.findViewById(R.id.btnPrevChapter)
        val btnNext: Button = view.findViewById(R.id.btnNextChapter)
    }

    override fun getItemViewType(position: Int) =
        if (position < pages.size) TYPE_PAGE else TYPE_FOOTER

    override fun getItemCount() = pages.size + 1 // +1 buat footer

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_PAGE) {
            PageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_page, parent, false))
        } else {
            FooterViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_reader_footer, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PageViewHolder) {
            holder.imageView.setImageBitmap(pages[position])
        } else if (holder is FooterViewHolder) {
            bindFooter(holder)
        }
    }

    private fun bindFooter(holder: FooterViewHolder) {
        val stars = listOf(holder.star1, holder.star2, holder.star3, holder.star4, holder.star5)

        fun updateStars(rating: Int) {
            stars.forEachIndexed { i, tv -> tv.text = if (i < rating) "★" else "☆" }
        }
        updateStars(savedRating)

        stars.forEachIndexed { i, tv ->
            tv.setOnClickListener {
                val rating = i + 1
                savedRating = rating
                updateStars(rating)
                onRate(rating)
            }
        }

        // Daftar chapter scroll horizontal
        holder.chapterListContainer.removeAllViews()
        val context = holder.chapterListContainer.context
        for (num in allChapterNums) {
            val chip = TextView(context).apply {
                text = "$num"
                textSize = 13f
                setPadding(24, 12, 24, 12)
                setTextColor(if (num == currentChapterNum) 0xFF000000.toInt() else 0xFFAAAAFF.toInt())
                background = context.getDrawable(
                    if (num == currentChapterNum) R.drawable.bg_tag_genre else R.drawable.bg_tag_status
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.marginEnd = 8
                layoutParams = lp
                setOnClickListener { if (num != currentChapterNum) onJumpChapter(num) }
            }
            holder.chapterListContainer.addView(chip)
        }

        val currentIndex = allChapterNums.indexOf(currentChapterNum)
        holder.btnPrev.visibility = if (currentIndex > 0) View.VISIBLE else View.GONE
        holder.btnNext.visibility = if (currentIndex < allChapterNums.size - 1) View.VISIBLE else View.GONE

        holder.btnPrev.setOnClickListener { onPrevChapter() }
        holder.btnNext.setOnClickListener { onNextChapter() }
    }
}
