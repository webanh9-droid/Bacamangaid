package com.bintang.bacamangaid

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ChapterListFragment : Fragment(R.layout.fragment_chapter_list) {

    companion object {
        private const val ARG_TITLE = "manga_title"

        fun newInstance(title: String): ChapterListFragment {
            val fragment = ChapterListFragment()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mangaTitle = arguments?.getString(ARG_TITLE) ?: return

        val recyclerView = view.findViewById<RecyclerView>(R.id.chapterRecyclerView)
        val loading = view.findViewById<ProgressBar>(R.id.chapterLoading)
        val errorText = view.findViewById<TextView>(R.id.chapterError)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        loading.visibility = View.VISIBLE

        Thread {
            try {
                val chapters = GitHubApi.listChaptersForTitle(mangaTitle)
                val coverUrl = GitHubApi.getCoverUrlForTitle(mangaTitle)

                val meta = try {
                    SupabaseApi.fetchAllManga().firstOrNull { it.title.equals(mangaTitle, ignoreCase = true) }
                } catch (e: Exception) {
                    null
                }

                val mangaDisplay = MangaDisplay(
                    title = mangaTitle,
                    coverUrl = coverUrl ?: meta?.coverUrlOverride,
                    synopsis = meta?.synopsis,
                    genres = meta?.genres ?: emptyList(),
                    statusId = meta?.statusId,
                    statusName = meta?.statusName
                )

                Handler(Looper.getMainLooper()).post {
                    loading.visibility = View.GONE
                    if (chapters.isEmpty()) {
                        errorText.text = "Belum ada chapter di manga ini."
                        errorText.visibility = View.VISIBLE
                    } else {
                        recyclerView.adapter = ChapterListAdapter(mangaDisplay, chapters) { chapterNum, pdfUrl ->
                            // Catat history baca kalau user login (buat rekomendasi otomatis)
                            val token = SessionManager.getAccessToken(requireContext())
                            val userId = SessionManager.getUserId(requireContext())
                            if (token != null && userId != null) {
                                Thread {
                                    try {
                                        SupabaseApi.recordRead(token, userId, mangaTitle)
                                    } catch (e: Exception) { }
                                }.start()
                            }

                            val intent = Intent(requireContext(), ReaderActivity::class.java)
                            intent.putExtra(ReaderActivity.EXTRA_PDF_URL, pdfUrl)
                            intent.putExtra(ReaderActivity.EXTRA_TITLE, "$mangaTitle - Chapter $chapterNum")
                            startActivity(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    loading.visibility = View.GONE
                    errorText.text = "Gagal memuat chapter. Cek koneksi internet."
                    errorText.visibility = View.VISIBLE
                }
            }
        }.start()
    }
}
