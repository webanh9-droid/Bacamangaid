package com.bintang.bacamangaid

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MangaListFragment : Fragment(R.layout.fragment_manga_list) {

    private var fullList: List<MangaDisplay> = emptyList()
    private var selectedGenreId: Long? = null
    private var selectedStatusId: Long? = null
    private var searchQuery: String = ""
    private var cachedGenres: List<GenreItem> = emptyList()
    private var cachedStatuses: List<StatusItem> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var genreChipContainer: LinearLayout
    private lateinit var statusChipContainer: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerView = view.findViewById(R.id.mangaRecyclerView)
        val recommendedRecyclerView = view.findViewById<RecyclerView>(R.id.recommendedRecyclerView)
        val recommendedLabel = view.findViewById<TextView>(R.id.recommendedLabel)
        val loading = view.findViewById<ProgressBar>(R.id.loadingIndicator)
        val errorText = view.findViewById<TextView>(R.id.errorText)
        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        val searchInput = view.findViewById<EditText>(R.id.searchInput)
        val accountButton = view.findViewById<TextView>(R.id.accountButton)
        val adminButton = view.findViewById<TextView>(R.id.adminButton)
        genreChipContainer = view.findViewById(R.id.genreChipContainer)
        statusChipContainer = view.findViewById(R.id.statusChipContainer)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recommendedRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        adminButton.visibility = View.GONE
        checkAdminStatus(adminButton)

        accountButton.setOnClickListener {
            startActivity(Intent(requireContext(), AccountActivity::class.java))
        }
        adminButton.setOnClickListener {
            startActivity(Intent(requireContext(), AdminActivity::class.java))
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                searchQuery = s?.toString() ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        swipeRefresh.setOnRefreshListener {
            GitHubApi.clearCache()
            loadMangaList(recommendedRecyclerView, recommendedLabel, loading, errorText, swipeRefresh)
        }

        loadMangaList(recommendedRecyclerView, recommendedLabel, loading, errorText, swipeRefresh)
    }

    private fun checkAdminStatus(adminButton: TextView) {
        val token = SessionManager.getAccessToken(requireContext()) ?: return
        val userId = SessionManager.getUserId(requireContext()) ?: return
        Thread {
            val isAdmin = try { AdminApi.isAdmin(token, userId) } catch (e: Exception) { false }
            Handler(Looper.getMainLooper()).post {
                adminButton.visibility = if (isAdmin) View.VISIBLE else View.GONE
            }
        }.start()
    }

    private fun loadMangaList(
        recommendedRecyclerView: RecyclerView,
        recommendedLabel: TextView,
        loading: ProgressBar,
        errorText: TextView,
        swipeRefresh: SwipeRefreshLayout
    ) {
        loading.visibility = View.VISIBLE
        errorText.visibility = View.GONE

        Thread {
            try {
                val githubTitles = GitHubApi.listMangaTitles()
                val covers = GitHubApi.listAllCoverFiles().associateBy { it.title.lowercase() }
                val metaList = try { SupabaseApi.fetchAllManga() } catch (e: Exception) { emptyList() }
                val metaMap = metaList.associateBy { it.title.lowercase() }
                val genres = try { SupabaseApi.fetchGenres() } catch (e: Exception) { emptyList() }
                val statuses = try { SupabaseApi.fetchStatuses() } catch (e: Exception) { emptyList() }

                val combined = githubTitles.map { title ->
                    val meta = metaMap[title.lowercase()]
                    val coverFromGithub = covers[title.lowercase()]?.downloadUrl
                    MangaDisplay(
                        title = title,
                        coverUrl = coverFromGithub ?: meta?.coverUrlOverride,
                        synopsis = meta?.synopsis,
                        genres = meta?.genres ?: emptyList(),
                        statusId = meta?.statusId,
                        statusName = meta?.statusName
                    )
                }

                // Rekomendasi berdasarkan genre yang paling sering dibaca
                var recommended: List<MangaDisplay> = emptyList()
                val token = SessionManager.getAccessToken(requireContext())
                val userId = SessionManager.getUserId(requireContext())
                if (token != null && userId != null) {
                    try {
                        val genreCounts = SupabaseApi.fetchUserGenreCounts(token, userId)
                        val topGenreId = genreCounts.maxByOrNull { it.value }?.key
                        if (topGenreId != null) {
                            recommended = combined.filter { it.hasGenre(topGenreId) }
                        }
                    } catch (e: Exception) { }
                }

                Handler(Looper.getMainLooper()).post {
                    loading.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    fullList = combined
                    cachedGenres = genres
                    cachedStatuses = statuses

                    setupGenreChips(genres)
                    setupStatusChips(statuses)

                    if (combined.isEmpty()) {
                        errorText.text = "Belum ada manga tersedia."
                        errorText.visibility = View.VISIBLE
                    } else {
                        if (recommended.isNotEmpty()) {
                            recommendedLabel.visibility = View.VISIBLE
                            recommendedRecyclerView.visibility = View.VISIBLE
                            recommendedRecyclerView.adapter = MangaHorizontalAdapter(recommended) { title ->
                                (activity as? MainActivity)?.openChapterList(title)
                            }
                        } else {
                            recommendedLabel.visibility = View.GONE
                            recommendedRecyclerView.visibility = View.GONE
                        }
                        applyFilters()
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    loading.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    errorText.text = "Gagal memuat daftar manga. Tarik ke bawah untuk coba lagi."
                    errorText.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun setupGenreChips(genres: List<GenreItem>) {
        genreChipContainer.removeAllViews()
        genreChipContainer.addView(makeChip("Semua", selectedGenreId == null, ChipStyle.BLUE) {
            selectedGenreId = null
            setupGenreChips(genres)
            applyFilters()
        })
        for (genre in genres) {
            genreChipContainer.addView(makeChip(genre.name, selectedGenreId == genre.id, ChipStyle.BLUE) {
                selectedGenreId = genre.id
                setupGenreChips(genres)
                applyFilters()
            })
        }
    }

    private fun setupStatusChips(statuses: List<StatusItem>) {
        statusChipContainer.removeAllViews()
        statusChipContainer.addView(makeChip("Semua", selectedStatusId == null, ChipStyle.OUTLINE) {
            selectedStatusId = null
            setupStatusChips(statuses)
            applyFilters()
        })
        for (status in statuses) {
            val style = when (status.name.lowercase()) {
                "ongoing"   -> ChipStyle.GREEN
                "completed" -> ChipStyle.BLUE
                "hiatus"    -> ChipStyle.AMBER
                "dropped"   -> ChipStyle.RED
                else        -> ChipStyle.OUTLINE
            }
            statusChipContainer.addView(makeChip(status.name, selectedStatusId == status.id, style) {
                selectedStatusId = status.id
                setupStatusChips(statuses)
                applyFilters()
            })
        }
    }

    private enum class ChipStyle { BLUE, GREEN, AMBER, RED, OUTLINE }

    private fun makeChip(label: String, selected: Boolean, style: ChipStyle, onClick: () -> Unit): TextView {
        val chip = TextView(requireContext())
        chip.text = label
        chip.setPadding(28, 14, 28, 14)
        chip.textSize = 12f

        val bgColor = if (selected) when (style) {
            ChipStyle.BLUE    -> Color.parseColor("#4D9FFF")
            ChipStyle.GREEN   -> Color.parseColor("#22C55E")
            ChipStyle.AMBER   -> Color.parseColor("#F59E0B")
            ChipStyle.RED     -> Color.parseColor("#EF4444")
            ChipStyle.OUTLINE -> Color.parseColor("#4D9FFF")
        } else Color.parseColor("#1A2444")

        val textColor = if (selected) Color.WHITE else Color.parseColor("#A0B4D0")

        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        drawable.cornerRadius = 50f
        drawable.setColor(bgColor)
        if (!selected) drawable.setStroke(1, Color.parseColor("#2A3D6A"))

        chip.background = drawable
        chip.setTextColor(textColor)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = 8
        chip.layoutParams = params
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun applyFilters() {
        val filtered = fullList.filter { manga ->
            val matchesSearch = searchQuery.isBlank() || manga.title.contains(searchQuery, ignoreCase = true)
            // many-to-many: cek apakah manga punya genre yang dipilih
            val matchesGenre = selectedGenreId == null || manga.hasGenre(selectedGenreId!!)
            val matchesStatus = selectedStatusId == null || manga.statusId == selectedStatusId
            matchesSearch && matchesGenre && matchesStatus
        }
        recyclerView.adapter = MangaAdapter(filtered) { title ->
            (activity as? MainActivity)?.openChapterList(title)
        }
    }
}
