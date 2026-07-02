package com.bintang.bacamangaid

data class MangaDisplay(
    val title: String,
    val coverUrl: String?,
    val synopsis: String?,
    val genres: List<GenreItem> = emptyList(),
    val statusId: Long? = null,
    val statusName: String? = null,
    val avgRating: Float = 0f,
    val totalViews: Int = 0
) {
    fun hasGenre(genreId: Long) = genres.any { it.id == genreId }
    val primaryGenreId: Long? get() = genres.firstOrNull()?.id
}
