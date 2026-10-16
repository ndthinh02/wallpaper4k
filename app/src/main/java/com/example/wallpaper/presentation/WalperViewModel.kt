package com.example.wallpaper.presentation

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.wallpaper.WalperApplication
import com.example.wallpaper.R
import com.example.wallpaper.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

data class FeedState(
    val items: List<Wallpaper> = emptyList(), val loading: Boolean = false,
    val error: Int? = null, val page: Int = 0, val lastPage: Int = 1, val total: Int = 0,
    val seed: String? = null, val filters: Filters = Filters(), val query: String = ""
)

class WalperViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    val repo = (application as WalperApplication).repository
    val settings = repo.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val favorites = repo.dao.favorites().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val downloads = repo.dao.downloads().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = repo.dao.history().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val events = repo.dao.events().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val covers = repo.dao.covers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val userCollections = repo.dao.userCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collectionWallpapers = repo.dao.collectionWallpapers().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val home = MutableStateFlow(FeedState(filters = Filters(sorting = saved["sorting"] ?: "toplist")))
    val search = MutableStateFlow(FeedState(filters = Filters(sorting = "relevance")))
    val collection = MutableStateFlow(FeedState())
    val query = saved.getStateFlow("query", "")
    val selected = MutableStateFlow<Wallpaper?>(null)
    val detailError = MutableStateFlow<Int?>(null)
    val busy = MutableStateFlow(false)
    val applied = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    private val jobs = mutableMapOf<String, Job>()
    private var detailJob: Job? = null

    init {
        refresh("home")
        viewModelScope.launch {
            query.collectLatest { text ->
                jobs.remove("search")?.cancel()
                search.value = FeedState(filters = search.value.filters, query = text, loading = text.isNotBlank())
                if (text.isNotBlank()) {
                    delay(400); refresh("search", text)
                }
            }
        }
    }

    private fun feed(key: String) = when (key) {
        "search" -> search; "collection" -> collection; else -> home
    }

    fun refresh(key: String, text: String = feed(key).value.query, filters: Filters = feed(key).value.filters) {
        jobs.remove(key)?.cancel()
        val state = feed(key)
        state.value = FeedState(loading = true, filters = filters, query = text)
        if (key == "home") saved["sorting"] = filters.sorting
        load(key, 1)
    }

    fun more(key: String) {
        val current = feed(key).value
        if (current.loading || current.page >= current.lastPage || current.error != null) return
        load(key, current.page + 1)
    }

    fun retry(key: String) {
        val current = feed(key).value
        if (current.loading) return
        if (current.page == 0) refresh(key) else load(key, current.page + 1)
    }

    private fun load(key: String, page: Int) {
        val flow = feed(key);
        val snapshot = flow.value
        flow.value = snapshot.copy(loading = true, error = null)
        jobs[key] = viewModelScope.launch {
            try {
                val result = repo.api.search(snapshot.filters.parameters(snapshot.query, page, snapshot.seed))
                ensureActive()
                val metadata = result.meta ?: throw IOException("Missing metadata")
                if (metadata.page != page || metadata.lastPage < page || metadata.total < 0) throw IOException("Invalid pagination")
                val list = result.data ?: throw IOException("Missing data")
                val safe = list.mapNotNull { it?.cleaned() }
                flow.value = snapshot.copy(
                    items = (if (page == 1) safe else snapshot.items + safe).distinctBy { it.id },
                    loading = false, page = metadata.page, lastPage = metadata.lastPage, total = metadata.total,
                    seed = metadata.seed ?: snapshot.seed, error = null
                )
                if (key == "search" && page == 1 && snapshot.query.isNotBlank()) repo.dao.saveHistory(
                    SearchHistory(
                        snapshot.query.trim()
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                flow.value = snapshot.copy(loading = false, error = error(e))
            }
        }
    }

    fun setQuery(text: String) {
        saved["query"] = text
    }

    fun submitSearch() {
        val text = query.value.trim(); if (text.isNotBlank()) launch { repo.dao.saveHistory(SearchHistory(text)) }
    }

    val currentList = MutableStateFlow<List<Wallpaper>>(emptyList())
    var currentIndex = 0

    fun prepareDetailSource(items: List<Wallpaper>) {
        currentList.value = items
    }

    fun open(w: Wallpaper, source: List<Wallpaper>? = null) {
        detailJob?.cancel(); selected.value = w; detailError.value = null; applied.value = false
        val lists = listOf<List<Wallpaper>>(
            home.value.items,
            search.value.items,
            collection.value.items,
            favorites.value.map { repo.wallpaper(it.metadata) },
            downloads.value.map { repo.wallpaper(it.metadata) }
        )
        val list = source ?: currentList.value.takeIf { items -> items.any { it.id == w.id } }
            ?: lists.firstOrNull { it.any { item -> item.id == w.id } } ?: listOf(w)
        currentList.value = list
        currentIndex = list.indexOfFirst { it.id == w.id }.coerceAtLeast(0)

        detailJob = viewModelScope.launch {
            try {
                val detail = repo.api.detail(w.id).data?.cleaned() ?: throw IOException("Missing data")
                ensureActive()
                selected.value = detail
                val updated = currentList.value.map { if (it.id == detail.id) detail else it }
                currentList.value = updated
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detailError.value = error(e)
            }
        }
    }

    fun getNext(): Wallpaper? {
        val list = currentList.value
        if (list.isEmpty()) return null
        return list.getOrNull(currentIndex + 1)
    }

    fun getPrev(): Wallpaper? {
        val list = currentList.value
        if (list.isEmpty()) return null
        return list.getOrNull(currentIndex - 1)
    }

    fun openId(id: String) = launch {
        detailError.value = null
        val local = repo.dao.download(id)?.metadata ?: repo.dao.favorite(id)?.metadata
        if (local != null) open(repo.wallpaper(local))
        else {
            val lists = listOf<List<Wallpaper>>(
                home.value.items,
                search.value.items,
                collection.value.items,
                favorites.value.map { repo.wallpaper(it.metadata) },
                downloads.value.map { repo.wallpaper(it.metadata) }
            )
            val existing = lists.flatten().firstOrNull { it.id == id }
            if (existing != null) {
                open(existing)
            } else {
                try {
                    val w = repo.api.detail(id).data?.cleaned()
                    if (w != null && w.purity == "sfw") open(w) else detailError.value = R.string.not_found
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    detailError.value = error(e)
                }
            }
        }
    }

    fun favorite(w: Wallpaper) = launch { repo.toggleFavorite(w) }
    fun cover(text: String) = launch(silent = true) { repo.cover(text) }
    fun apply(w: Wallpaper, target: Int, zoom: Float, x: Float, y: Float) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                val local = repo.dao.download(w.id)
                val uri = local?.takeIf { it.status == "complete" && repo.valid(it.uri) }?.let { Uri.parse(it.uri) }
                    ?: throw IOException(repo.text(R.string.download_first))
                repo.apply(uri, target, zoom, x, y)
                repo.event("apply", w); applied.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.emit(e.message ?: repo.text(R.string.operation_failed))
            } finally {
                busy.value = false
            }
        }
    }

    fun launch(silent: Boolean = false, block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!silent) messages.emit(e.message ?: repo.text(R.string.operation_failed))
        }
    }

    private fun error(e: Exception): Int = when {
        e is HttpException && e.code() == 429 -> R.string.rate_limit
        e is HttpException && e.code() >= 500 -> R.string.server_error
        e is HttpException && e.code() == 404 -> R.string.not_found
        e is SocketTimeoutException -> R.string.timeout
        e is IOException -> R.string.network_error
        else -> R.string.operation_failed
    }
}
