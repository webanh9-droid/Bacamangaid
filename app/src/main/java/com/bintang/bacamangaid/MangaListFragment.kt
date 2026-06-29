package com.bintang.bacamangaid

import android.content.Intent
import android.graphics.Color
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
    private var searchQuery: String = ""

    private lateinit var recyclerView: RecyclerView
    private lateinit var genreChipContainer: LinearLayout

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
        genreChipContainer = view.findViewById(R.id.genreChipContainer)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recommendedRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        accountButton.setOnClickListener {
            startActivity(Intent(requireContext(), AccountActivity::class.java))
        }

        view.findViewById<TextView>(R.id.adminButton).setOnClickListener {
            if (!SessionManager.isLoggedIn(requireContext())) {
                android.widget.Toast.makeText(requireContext(), "Login dulu", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                startActivity(Intent(requireContext(), AdminActivity::class.java))
            }
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

                val combined = githubTitles.map { title ->
                    val meta = metaMap[title.lowercase()]
                    val coverFromGithub = covers[title.lowercase()]?.downloadUrl
                    MangaDisplay(
                        title = title,
                        coverUrl = coverFromGithub ?: meta?.coverUrlOverride,
                        synopsis = meta?.synopsis,
                        genreId = meta?.genreId,
                        genreName = meta?.genreName
                    )
                }

                // Rekomendasi otomatis berdasarkan history baca user (kalau login)
                var recommended: List<MangaDisplay> = emptyList()
                val token = SessionManager.getAccessToken(requireContext())
                val userId = SessionManager.getUserId(requireContext())
                if (token != null && userId != null) {
                    try {
                        val genreCounts = SupabaseApi.fetchUserGenreCounts(token, userId)
                        val topGenreId = genreCounts.maxByOrNull { it.value }?.key
                        if (topGenreId != null) {
                            recommended = combined.filter { it.genreId == topGenreId }
                        }
                    } catch (e: Exception) { }
                }

                Handler(Looper.getMainLooper()).post {
                    loading.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                    fullList = combined

                    setupGenreChips(genres)

                    if (combined.isEmpty()) {
                        errorText.text = "Belum ada manga. Upload PDF dengan format 'Judul Manga Chapter N.pdf'."
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
                    errorText.text = "Gagal memuat daftar manga. Cek koneksi internet, tarik ke bawah untuk coba lagi."
                    errorText.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun setupGenreChips(genres: List<GenreItem>) {
        genreChipContainer.removeAllViews()

        val chipAll = makeChip("Semua", selectedGenreId == null) {
            selectedGenreId = null
            setupGenreChips(genres)
            applyFilters()
        }
        genreChipContainer.addView(chipAll)

        for (genre in genres) {
            val chip = makeChip(genre.name, selectedGenreId == genre.id) {
                selectedGenreId = genre.id
                setupGenreChips(genres)
                applyFilters()
            }
            genreChipContainer.addView(chip)
        }
    }

    private fun makeChip(label: String, selected: Boolean, onClick: () -> Unit): TextView {
        val chip = TextView(requireContext())
        chip.text = label
        chip.setPadding(24, 12, 24, 12)
        chip.textSize = 13f
        chip.setTextColor(if (selected) Color.BLACK else Color.WHITE)
        chip.setBackgroundColor(if (selected) Color.parseColor("#4DB8FF") else Color.parseColor("#333333"))
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.marginEnd = 8
        chip.layoutParams = params
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun applyFilters() {
        val filtered = fullList.filter { manga ->
            val matchesSearch = searchQuery.isBlank() || manga.title.contains(searchQuery, ignoreCase = true)
            val matchesGenre = selectedGenreId == null || manga.genreId == selectedGenreId
            matchesSearch && matchesGenre
        }
        recyclerView.adapter = MangaAdapter(filtered) { title ->
            (activity as? MainActivity)?.openChapterList(title)
        }
    }
}
