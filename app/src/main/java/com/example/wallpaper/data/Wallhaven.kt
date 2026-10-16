package com.example.wallpaper.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap

data class Thumbs(val large: String? = null, val original: String? = null, val small: String? = null)
data class Tag(val name: String? = null)
data class Wallpaper(
    val id: String = "", val url: String = "", val path: String = "",
    val thumbs: Thumbs? = null, val resolution: String = "",
    @SerializedName("dimension_x") val width: Int = 0,
    @SerializedName("dimension_y") val height: Int = 0,
    @SerializedName("file_size") val fileSize: Long = 0,
    @SerializedName("file_type") val fileType: String = "image/jpeg",
    @SerializedName("created_at") val createdAt: String? = null,
    val source: String? = null, val purity: String? = null,
    val tags: List<Tag>? = null
) {
    val thumbnail: String
        // Keep the uncropped preview when available, especially for tall grid cards.
        get() = thumbs?.original?.takeIf { it.isNotBlank() }
            ?: thumbs?.large?.takeIf { it.isNotBlank() }
            ?: thumbs?.small?.takeIf { it.isNotBlank() }
            ?: path

    fun cleaned(): Wallpaper? {
        if (id.isNullOrBlank() || !id.matches(Regex("[a-z0-9]{6}")) || path.isNullOrBlank() || !path.startsWith("https://") || purity != "sfw") return null
        return copy(url = url?.takeIf { it.startsWith("https://") } ?: "https://wallhaven.cc/w/$id",
            resolution = resolution ?: "", fileType = fileType ?: "image/jpeg", width = width.coerceAtLeast(0),
            height = height.coerceAtLeast(0), fileSize = fileSize.coerceAtLeast(0), tags = tags?.filterNotNull())
    }
}

data class Meta(
    @SerializedName("current_page") val page: Int = 1,
    @SerializedName("last_page") val lastPage: Int = 1,
    val total: Int = 0, val seed: String? = null
)

data class SearchResponse(val data: List<Wallpaper>? = null, val meta: Meta? = null)
data class DetailResponse(val data: Wallpaper? = null)
interface WallhavenApi {
    @GET("search")
    suspend fun search(@QueryMap parameters: Map<String, String>): SearchResponse

    @GET("w/{id}")
    suspend fun detail(@Path("id") id: String): DetailResponse
}

data class Filters(
    val sorting: String = "toplist",
    val color: String = "",
    val ratio: String = "",
    val resolution: String = ""
) {
    fun parameters(query: String, page: Int, seed: String?): Map<String, String> = buildMap {
        put("purity", "100"); put("categories", "111"); put("sorting", sorting)
        put("order", "desc"); put("page", page.toString())
        if (sorting == "toplist") put("topRange", "1M")
        if (query.isNotBlank()) put("q", query)
        if (color.isNotBlank()) put("colors", color)
        if (ratio.isNotBlank()) put("ratios", ratio)
        if (resolution.isNotBlank()) put("atleast", resolution)
        if (sorting == "random" && !seed.isNullOrBlank()) put("seed", seed)
    }
}
