@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.wallpaper.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.example.wallpaper.R
import com.example.wallpaper.data.*

@Composable
fun RoundIcon(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick,
        modifier
            .size(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f))
    ) {
        Icon(icon, stringResource(label), tint = tint)
    }
}

@Composable
fun Header(menu: () -> Unit, notifications: () -> Unit, unread: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(menu) { Icon(Icons.Outlined.Menu, stringResource(R.string.menu)) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.walper_mark), stringResource(R.string.app_name), Modifier.size(32.dp))
        }
        IconButton(notifications) {
            BadgedBox(badge = { if (unread) Badge(containerColor = WalperTokens.Lavender) }) {
                Icon(
                    Icons.Outlined.NotificationsNone,
                    stringResource(R.string.notifications)
                )
            }
        }
    }
}

@Composable
fun PageTitle(title: Int, subtitle: Int? = null) {
    Column(Modifier
        .fillMaxWidth()
        .padding(horizontal = WalperTokens.Page, vertical = 16.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        subtitle?.let {
            Text(
                stringResource(it),
                Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun BackHeader(title: Int, back: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = WalperTokens.Page, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIcon(Icons.AutoMirrored.Outlined.ArrowBack, R.string.back, back)
        Spacer(Modifier.weight(1f)); action?.invoke()
    }
    PageTitle(title)
}

@Composable
fun PrimaryButton(title: Int, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick, modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp), enabled = enabled, shape = WalperTokens.Card,
        colors = ButtonDefaults.buttonColors(containerColor = WalperTokens.Lavender, contentColor = Color.White)
    ) {
        Text(stringResource(title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(message: Int, modifier: Modifier = Modifier, action: (() -> Unit)? = null, error: Boolean = false) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val illustration = when {
            error -> R.drawable.error_network
            message == R.string.no_favorites -> R.drawable.empty_favorites
            message == R.string.no_downloads -> R.drawable.empty_downloads
            message == R.string.no_notifications -> R.drawable.empty_notifications
            else -> R.drawable.empty_search
        }
        Image(
            painterResource(illustration),
            null,
            Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .aspectRatio(1.2f),
            contentScale = ContentScale.Fit
        )
        Text(
            stringResource(message),
            Modifier.padding(top = 24.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge
        )
        if (action != null) PrimaryButton(
            if (error) R.string.retry else R.string.explore,
            action,
            Modifier.padding(top = 24.dp)
        )
    }
}

@Composable
fun WallpaperCard(
    w: Wallpaper,
    favorite: Boolean,
    uniform: Boolean,
    open: () -> Unit,
    toggle: () -> Unit,
    local: String? = null
) {
    val context = LocalContext.current
    var previewFailed by remember(w.id, w.thumbnail, local) { mutableStateOf(false) }
    val preview = if (previewFailed) {
        w.thumbs?.large?.takeIf { it.isNotBlank() } ?: w.thumbnail
    } else w.thumbnail
    val request = remember(context, local, preview) {
        ImageRequest.Builder(context)
            .data(local ?: preview)
            // AsyncImage supplies the measured card size; do not decode at original resolution.
            .precision(Precision.EXACT)
            .scale(Scale.FILL)
            .build()
    }
    val tint by animateColorAsState(if (favorite) WalperTokens.Pink else Color.White, label = "favorite")
    val aspect = if (uniform) .64f else if (w.width > 0 && w.height > 0) (w.width.toFloat() / w.height).coerceIn(
        .6f,
        1.05f
    ) else .75f
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(WalperTokens.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = open)
    ) {
        AsyncImage(
            request,
            w.resolution,
            Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            onError = { if (local == null) previewFailed = true }
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .18f))
        ) {
            IconButton(toggle) {
                Icon(
                    if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    stringResource(R.string.favorite_toggle),
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun WallpaperGrid(
    items: List<Wallpaper>, favorites: Set<String>, uniform: Boolean, open: (Wallpaper) -> Unit,
    toggle: (Wallpaper) -> Unit, modifier: Modifier = Modifier, loading: Boolean = false, error: Int? = null,
    refresh: (() -> Unit)? = null, more: (() -> Unit)? = null, retry: (() -> Unit)? = null,
    empty: Int = R.string.no_results, local: Map<String, String> = emptyMap(), bottom: Int = 112
) {
    val state = rememberLazyStaggeredGridState()
    val nearingEnd by remember(
        state,
        items.size
    ) {
        derivedStateOf {
            state.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let { it >= items.size - 5 } ?: false
        }
    }
    LaunchedEffect(
        nearingEnd,
        items.size,
        loading
    ) { if (nearingEnd && items.isNotEmpty() && !loading && error == null) more?.invoke() }
    @Composable
    fun Content() {
        when {
            items.isEmpty() && !loading && error != null -> EmptyState(error, Modifier.fillMaxSize(), retry, true)
            items.isEmpty() && !loading -> EmptyState(empty, Modifier.fillMaxSize())
            else -> LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2), state = state, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = WalperTokens.Page,
                    end = WalperTokens.Page,
                    top = 8.dp,
                    bottom = bottom.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(WalperTokens.Gap), verticalItemSpacing = WalperTokens.Gap
            ) {
                if (items.isEmpty()) items(8, key = { "placeholder_$it" }) { n ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(if (uniform) 230.dp else if (n % 3 == 0) 150.dp else 240.dp)
                            .clip(WalperTokens.Card)
                            .background(WalperTokens.pastel[n % 5])
                    )
                }
                items(items, key = { it.id }) { w ->
                    WallpaperCard(
                        w,
                        w.id in favorites,
                        uniform,
                        { open(w) },
                        { toggle(w) },
                        local[w.id]
                    )
                }
                item(span = StaggeredGridItemSpan.FullLine) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (loading) CircularProgressIndicator(Modifier
                            .padding(20.dp)
                            .size(28.dp))
                        if (error != null) {
                            Text(
                                stringResource(error),
                                textAlign = TextAlign.Center
                            ); TextButton({ retry?.invoke() }) { Text(stringResource(R.string.retry)) }
                        }
                    }
                }
            }
        }
    }
    Box(modifier) {
        if (refresh != null) PullToRefreshBox(
            isRefreshing = loading && items.isNotEmpty(),
            onRefresh = refresh
        ) { Content() }
        else Content()
    }
}

fun sortLabel(sort: String): Int = when (sort) {
    "date_added" -> R.string.recent; "random" -> R.string.random; "relevance" -> R.string.relevance; else -> R.string.trending
}

@Composable
fun SortDropdown(sorting: String, change: (String) -> Unit, search: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }, contentPadding = PaddingValues(0.dp)) {
            Text(
                stringResource(sortLabel(sorting)),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                null,
                Modifier.padding(start = 4.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(expanded, { expanded = false }) {
            (if (search) listOf("relevance", "toplist", "date_added", "random") else listOf(
                "toplist",
                "date_added",
                "random"
            )).forEach { value ->
                DropdownMenuItem(
                    text = { Text(stringResource(sortLabel(value))) },
                    onClick = { expanded = false; change(value) },
                    trailingIcon = {
                        if (value == sorting) Icon(
                            Icons.Outlined.Check,
                            null,
                            tint = WalperTokens.Lavender
                        )
                    })
            }
        }
    }
}

@Composable
fun FilterSheet(current: Filters, dismiss: () -> Unit, apply: (Filters) -> Unit) {
    var sorting by rememberSaveable { mutableStateOf(current.sorting) }
    var color by rememberSaveable { mutableStateOf(current.color) }
    var ratio by rememberSaveable { mutableStateOf(current.ratio) }
    var resolution by rememberSaveable { mutableStateOf(current.resolution) }
    ModalBottomSheet(dismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.filter), style = MaterialTheme.typography.headlineSmall)
            SortDropdown(sorting, { sorting = it }, true)
            Text(stringResource(R.string.color), fontWeight = FontWeight.SemiBold)
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(color == "", { color = "" }, { Text(stringResource(R.string.any)) })
                listOf("663399", "0066cc", "66cccc", "77cc33", "ff9900", "cc0000", "000000", "ffffff").forEach { hex ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor("#$hex")))
                            .border(
                                if (color == hex) 3.dp else 1.dp,
                                if (color == hex) WalperTokens.Lavender else Color.Gray,
                                CircleShape
                            )
                            .clickable { color = hex })
                }
            }
            Text(stringResource(R.string.ratio), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("", "9x16", "16x9", "1x1", "21x9").forEach { value ->
                    FilterChip(
                        ratio == value,
                        { ratio = value },
                        { Text(if (value == "") stringResource(R.string.any) else value.replace('x', ':')) })
                }
            }
            Text(stringResource(R.string.resolution), fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("", "1920x1080", "2560x1440", "3840x2160").forEach { value ->
                    FilterChip(
                        resolution == value,
                        { resolution = value },
                        { Text(if (value == "") stringResource(R.string.any) else value) })
                }
            }
            PrimaryButton(R.string.apply_filters, { apply(Filters(sorting, color, ratio, resolution)); dismiss() })
            TextButton(
                { sorting = current.sorting; color = ""; ratio = ""; resolution = "" },
                Modifier.align(Alignment.CenterHorizontally)
            ) { Text(stringResource(R.string.reset)) }
        }
    }
}

@Composable
fun ActiveFilters(filters: Filters) {
    val values = listOf(
        filters.color.takeIf { it.isNotEmpty() }?.let { "#$it" },
        filters.ratio.takeIf { it.isNotEmpty() },
        filters.resolution.takeIf { it.isNotEmpty() }).filterNotNull()
    if (values.isNotEmpty()) Text(
        values.joinToString(" · "),
        Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        color = WalperTokens.Lavender
    )
}
