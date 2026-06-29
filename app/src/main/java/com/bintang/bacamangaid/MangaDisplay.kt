package com.bintang.bacamangaid

data class MangaDisplay(
    val title: String,
    val coverUrl: String?,
    val synopsis: String?,
    val genres: List<GenreItem> = emptyList(),   // many-to-many
    val statusId: Long? = null,
    val statusName: String? = null
) {
    // Helper: cek apakah manga ini punya genre tertentu
    fun hasGenre(genreId: Long) = genres.any { it.id == genreId }

    // Untuk backward compat (rekomendasi pakai genre pertama)
    val primaryGenreId: Long? get() = genres.firstOrNull()?.id
}
