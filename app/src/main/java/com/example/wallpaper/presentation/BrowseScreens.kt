@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.wallpaper.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.wallpaper.R
import com.example.wallpaper.data.*
import com.example.wallpaper.repository.Settings

data class LocalCollection(val query: String, val name: Int, val vi: String)

val localCollections = listOf(
    LocalCollection("abstract", R.string.collection_abstract, "trừu tượng"),
    LocalCollection("architecture", R.string.architecture, "kiến trúc"),
    LocalCollection("colorful", R.string.colorful, "sắc màu"),
    LocalCollection("nature", R.string.nature, "thiên nhiên"),
    LocalCollection("cars", R.string.cars, "xe hơi"),
    LocalCollection("anime", R.string.anime, "anime"),
    LocalCollection("gaming", R.string.gaming, "trò chơi"),
    LocalCollection("space", R.string.space, "vũ trụ"),
    LocalCollection("animals", R.string.animals, "động vật"),
    LocalCollection("city", R.string.city, "thành phố"),
    LocalCollection("technology", R.string.technology, "công nghệ"),
    LocalCollection("minimal", R.string.minimal, "tối giản"),
    LocalCollection("flowers", R.string.flowers, "hoa"),
    LocalCollection("ocean", R.string.ocean, "đại dương"),
    LocalCollection("mountains", R.string.mountains, "núi"),
    LocalCollection("amoled", R.string.amoled, "amoled")
)

@Composable
fun HomeScreen(vm: WalperViewModel, settings: Settings, open: (Wallpaper) -> Unit) {
    val feed by vm.home.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            SortDropdown(feed.filters.sorting, { vm.refresh("home", filters = feed.filters.copy(sorting = it)) })
            Spacer(Modifier.weight(1f))
            IconButton({ filter = true }) { Icon(Icons.Outlined.FilterAlt, stringResource(R.string.filter)) }
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(WalperTokens.Ink)
            ) {
                listOf("staggered", "uniform").forEachIndexed { n, mode ->
                    IconButton(
                        { vm.launch { vm.repo.settings.text("grid", mode) } },
                        Modifier
                            .size(36.dp)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (settings.grid == mode) WalperTokens.Lavender else Color.Transparent)
                    ) {
                        Icon(
                            if (n == 0) Icons.Outlined.Dashboard else Icons.Outlined.GridView,
                            stringResource(if (n == 0) R.string.staggered else R.string.uniform),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
        ActiveFilters(feed.filters)
        WallpaperGrid(
            feed.items,
            favorites.map { it.id }.toSet(),
            settings.grid == "uniform",
            open,
            vm::favorite,
            Modifier.weight(1f),
            feed.loading,
            feed.error,
            { vm.refresh("home") },
            { vm.more("home") },
            { vm.retry("home") })
    }
    if (filter) FilterSheet(feed.filters, { filter = false }) { vm.refresh("home", filters = it) }
}

@Composable
fun CollectionCard(vm: WalperViewModel, item: LocalCollection, open: (String) -> Unit) {
    val covers by vm.covers.collectAsStateWithLifecycle()
    val cover = covers.firstOrNull { it.query == item.query }
    // Only visible cards request covers, sequentially; results are persisted in Room.
    LaunchedEffect(item.query) { vm.cover(item.query) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(WalperTokens.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { open(item.query) }) {
        if (cover != null) AsyncImage(cover.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .58f), Color.Transparent)))
        )
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(24.dp)
        ) {
            Text(stringResource(item.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 25.sp)
            if (cover != null) Text(
                stringResource(R.string.results_count, cover.total),
                color = Color.White.copy(alpha = .85f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun CollectionsScreen(vm: WalperViewModel, open: (String) -> Unit, personal: (String) -> Unit) {
    CollectionsHub(vm, open, personal)
}

@Composable
fun CollectionScreen(
    vm: WalperViewModel,
    settings: Settings,
    query: String,
    back: () -> Unit,
    open: (Wallpaper) -> Unit
) {
    val feed by vm.collection.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(query) { if (vm.collection.value.query != query) vm.refresh("collection", query) }
    val category = localCollections.firstOrNull { it.query == query }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        BackHeader(category?.name ?: R.string.collections, back) {
            RoundIcon(
                Icons.Outlined.FilterAlt,
                R.string.filter,
                { filter = true })
        }
        if (feed.page > 0) Text(
            stringResource(R.string.results_count, feed.total),
            Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.padding(horizontal = 24.dp)) {
            SortDropdown(
                feed.filters.sorting,
                { vm.refresh("collection", filters = feed.filters.copy(sorting = it)) })
        }
        ActiveFilters(feed.filters)
        WallpaperGrid(
            feed.items,
            favorites.map { it.id }.toSet(),
            settings.grid == "uniform",
            open,
            vm::favorite,
            Modifier.weight(1f),
            feed.loading,
            feed.error,
            { vm.refresh("collection") },
            { vm.more("collection") },
            { vm.retry("collection") },
            bottom = 24
        )
    }
    if (filter) FilterSheet(feed.filters, { filter = false }) { vm.refresh("collection", filters = it) }
}

@Composable
fun SearchScreen(vm: WalperViewModel, settings: Settings, open: (Wallpaper) -> Unit, collection: (String) -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val feed by vm.search.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableIntStateOf(0) }
    var filter by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        PageTitle(R.string.search, R.string.search_description)
        OutlinedTextField(
            query,
            vm::setQuery,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = WalperTokens.Card,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) IconButton({ vm.setQuery("") }) {
                        Icon(
                            Icons.Outlined.Close,
                            stringResource(R.string.clear)
                        )
                    }
                    IconButton({ filter = true }) { Icon(Icons.Outlined.Tune, stringResource(R.string.filter)) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.submitSearch(); keyboard?.hide() })
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            listOf(R.string.photo, R.string.collections).forEachIndexed { n, label ->
                TextButton({ type = n; keyboard?.hide() }) {
                    Text(
                        stringResource(label),
                        color = if (type == n) WalperTokens.Lavender else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        ActiveFilters(feed.filters)
        when {
            type == 1 -> {
                val matched = localCollections.filter { it.query.contains(query, true) || it.vi.contains(query, true) }
                if (matched.isEmpty()) EmptyState(R.string.no_results, Modifier.weight(1f))
                else LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(matched, key = { it.query }) { CollectionCard(vm, it, collection) }
                }
            }

            query.isBlank() -> LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(24.dp, 0.dp, 24.dp, 112.dp)
            ) {
                item {
                    Text(
                        stringResource(if (history.isEmpty()) R.string.empty_search else R.string.history),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(history, key = { it.query }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.setQuery(item.query); keyboard?.hide() }
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.History, null)
                        Text(
                            item.query, Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                        IconButton({ vm.launch { vm.repo.dao.removeHistory(item.query) } }) {
                            Icon(
                                Icons.Outlined.Close,
                                stringResource(R.string.remove_history)
                            )
                        }
                    }
                }
            }

            else -> WallpaperGrid(
                feed.items,
                favorites.map { it.id }.toSet(),
                settings.grid == "uniform",
                { vm.submitSearch(); keyboard?.hide(); open(it) },
                vm::favorite,
                Modifier.weight(1f),
                feed.loading,
                feed.error,
                { vm.refresh("search") },
                { vm.more("search") },
                { vm.retry("search") })
        }
    }
    if (filter) FilterSheet(feed.filters, { filter = false }) { vm.refresh("search", query, it) }
}

@Composable
fun FavoritesScreen(
    vm: WalperViewModel,
    settings: Settings,
    back: () -> Unit,
    open: (Wallpaper) -> Unit,
    home: () -> Unit
) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    var oldest by rememberSaveable { mutableStateOf(false) }
    val list = remember(
        favorites,
        oldest
    ) { (if (oldest) favorites.reversed() else favorites).map { vm.repo.wallpaper(it.metadata) } }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        BackHeader(R.string.favorites, back)
        Text(
            stringResource(R.string.download_metadata_only),
            Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(Modifier.padding(horizontal = 24.dp)) {
            FilterChip(
                oldest,
                { oldest = !oldest },
                { Text(stringResource(if (oldest) R.string.oldest else R.string.newest)) })
        }
        if (list.isEmpty()) EmptyState(R.string.no_favorites, Modifier.weight(1f), home)
        else WallpaperGrid(
            list,
            favorites.map { it.id }.toSet(),
            settings.grid == "uniform",
            { w -> vm.prepareDetailSource(list); open(w) },
            vm::favorite,
            Modifier.weight(1f),
            bottom = 24
        )
    }
}
