@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.wallpaper.presentation

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.wallpaper.R
import com.example.wallpaper.data.Wallpaper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

fun shareText(context: Context, text: String, title: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
    }, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun shareFile(context: Context, uri: String, mime: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = mime; putExtra(Intent.EXTRA_STREAM, Uri.parse(uri)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("Walper", Uri.parse(uri))
    }, context.getString(R.string.share)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme in listOf("https", "http")) context.startActivity(
        Intent(
            Intent.ACTION_VIEW,
            uri
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun DetailScreen(vm: WalperViewModel, id: String, back: () -> Unit, home: () -> Unit, snackbar: SnackbarHostState, open: (Wallpaper) -> Unit) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val w = selected?.takeIf { it.id == id }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val error by vm.detailError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val applied by vm.applied.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val darkBefore =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        window?.let { WindowInsetsControllerCompat(it, view).isAppearanceLightStatusBars = false }
        onDispose { window?.let { WindowInsetsControllerCompat(it, view).isAppearanceLightStatusBars = !darkBefore } }
    }
    LaunchedEffect(id) { if (vm.selected.value?.id != id) vm.openId(id) }
    var controls by rememberSaveable(id) { mutableStateOf(true) }
    var info by rememberSaveable(id) { mutableStateOf(false) }
    var addToCollection by rememberSaveable(id) { mutableStateOf(false) }
    var apply by rememberSaveable(id) { mutableStateOf(false) }
    var preview by rememberSaveable(id) { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var zoom by rememberSaveable(id) { mutableFloatStateOf(1f) }
    var pan by remember(id) { mutableStateOf(Offset.Zero) }
    var imageFailed by remember(id) { mutableStateOf(false) }
    var imageAttempt by remember(id) { mutableIntStateOf(0) }
    val currentImageAspect by rememberUpdatedState(
        w?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height }
    )
    val currentOpen by rememberUpdatedState(open)
    val currentBusy by rememberUpdatedState(busy)
    var localUri by remember(id) { mutableStateOf<String?>(null) }
    var imageLoading by remember(id, localUri, w?.path, imageAttempt) { mutableStateOf(true) }
    var imageLoadSlow by remember(id, localUri, w?.path, imageAttempt) { mutableStateOf(false) }
    LaunchedEffect(imageLoading, id, localUri, w?.path, imageAttempt) {
        imageLoadSlow = false
        if (imageLoading) {
            delay(5_000)
            imageLoadSlow = true
        }
    }
    var missing by remember(id) { mutableStateOf(false) }
    val download = downloads.firstOrNull { it.id == id }
    val work by WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow("download_$id")
        .collectAsStateWithLifecycle(emptyList())
    val running = work.firstOrNull { !it.state.isFinished }
    val percent = running?.progress?.getInt("percent", 0) ?: 0
    LaunchedEffect(id, download) {
        localUri = if (download?.status == "complete" && vm.repo.valid(download.uri)) download.uri else null
        missing = download?.status == "complete" && localUri == null
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && w != null) vm.repo.download(w)
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.storage_permission)) }
    }
    val requestDownload: () -> Unit = {
        if (w != null) {
            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            )
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else vm.repo.download(w)
        }
    }
    Box(Modifier
        .fillMaxSize()
        .background(Color.Black)) {
        if (w != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RectangleShape)
                    .pointerInput(id) { detectTapGestures { controls = !controls } }
                    .pointerInput(id) {
                        val swipeThreshold = 64.dp.toPx()
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var displacement = Offset.Zero
                            var transformed = zoom > 1f
                            var cancelled = false
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.isConsumed }) {
                                    cancelled = true
                                    break
                                }
                                val drag = event.calculatePan()
                                displacement += drag
                                if (event.changes.count { it.pressed } > 1) transformed = true
                                if (transformed) {
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                    val aspect = currentImageAspect ?: (size.width.toFloat() / size.height.coerceAtLeast(1))
                                    val fittedWidth = min(size.width.toFloat(), size.height * aspect)
                                    val fittedHeight = fittedWidth / aspect
                                    val boundsX = ((fittedWidth * zoom - size.width) / 2f).coerceAtLeast(0f)
                                    val boundsY = ((fittedHeight * zoom - size.height) / 2f).coerceAtLeast(0f)
                                    pan = if (zoom == 1f) Offset.Zero else Offset(
                                        (pan.x + drag.x).coerceIn(-boundsX, boundsX),
                                        (pan.y + drag.y).coerceIn(-boundsY, boundsY)
                                    )
                                }
                                if (transformed || displacement.getDistance() > viewConfiguration.touchSlop) {
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                            if (!cancelled && !transformed && !currentBusy &&
                                abs(displacement.x) > swipeThreshold && abs(displacement.x) > abs(displacement.y)) {
                                val next = if (displacement.x < 0f) vm.getNext() else vm.getPrev()
                                next?.let(currentOpen)
                            }
                        }
                    }) {
                AsyncImage(
                    w.thumbnail,
                    null,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
                    contentScale = ContentScale.Fit
                )
                key(id, imageAttempt) {
                    AsyncImage(
                        remember(context, localUri, w.path) {
                            ImageRequest.Builder(context)
                                .data(localUri ?: w.path)
                                .crossfade(true)
                                .build()
                        },
                        w.resolution,
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
                        contentScale = ContentScale.Fit,
                        onLoading = { imageLoading = true; imageFailed = false },
                        onSuccess = { imageLoading = false; imageFailed = false },
                        onError = {
                            imageLoading = false
                            if (localUri != null) {
                                localUri = null
                                missing = true
                            } else imageFailed = true
                        }
                    )
                }
            }
            if (preview) Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()),
                    color = Color.White,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Light
                )
                Text(DateFormat.getDateInstance(DateFormat.FULL).format(Date()), color = Color.White)
                Text(
                    stringResource(R.string.preview_note),
                    Modifier.padding(top = 8.dp),
                    color = Color.White.copy(alpha = .65f),
                    fontSize = 12.sp
                )
            }
            if (imageLoading || imageFailed) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 24.dp)
                        .padding(top = if (preview) 260.dp else 88.dp),
                    shape = WalperTokens.Card,
                    color = Color.Black.copy(alpha = .75f),
                    contentColor = Color.White
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (imageLoading) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                            Text(
                                stringResource(R.string.detail_image_loading),
                                style = MaterialTheme.typography.labelLarge,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                stringResource(if (imageLoadSlow) R.string.detail_image_loading_slow else R.string.detail_image_preview_note),
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(stringResource(R.string.detail_image_failed), textAlign = TextAlign.Center)
                            TextButton({ imageFailed = false; imageAttempt++ }) {
                                Text(stringResource(R.string.retry), color = Color.White)
                            }
                        }
                    }
                }
            }
            if (controls) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RoundIcon(Icons.AutoMirrored.Outlined.ArrowBack, R.string.back, back, tint = Color.White)

                }
                Column(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .safeDrawingPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    if (error != null) TextButton({ vm.open(w) }) {
                        Text(
                            stringResource(R.string.retry),
                            color = Color.White
                        )
                    }
                    if (missing) Text(
                        stringResource(R.string.file_missing),
                        color = Color.White,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = .5f))
                            .padding(8.dp)
                    )
                    IconButton({ vm.favorite(w) }, Modifier.size(56.dp)) {
                        Icon(
                            if (favorites.any { it.id == id }) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                            stringResource(R.string.favorite_toggle),
                            Modifier.size(34.dp),
                            tint = if (favorites.any { it.id == id }) WalperTokens.Pink else Color.White
                        )
                    }
                    IconButton({ addToCollection = true }, Modifier.size(56.dp)) {
                        Icon(Icons.Outlined.CreateNewFolder, stringResource(R.string.add_to_collection),
                            Modifier.size(30.dp), tint = Color.White)
                    }
                    IconButton(requestDownload, Modifier.size(56.dp), enabled = running == null) {
                        if (running != null) CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(30.dp),
                            color = Color.White
                        )
                        else Icon(
                            if (localUri != null) Icons.Outlined.DownloadDone else Icons.Outlined.FileDownload,
                            stringResource(R.string.download),
                            Modifier.size(34.dp),
                            tint = Color.White
                        )
                    }
                    if (running != null) Text(stringResource(R.string.download_percent, percent), color = Color.White)
                    if (download?.status in listOf("failed", "interrupted")) TextButton(requestDownload) {
                        Text(
                            stringResource(R.string.download_failed),
                            color = Color.White
                        )
                    }
                    Box {
                        IconButton({ overflow = true }, Modifier.size(56.dp)) {
                            Icon(
                                Icons.Outlined.MoreHoriz,
                                stringResource(R.string.overflow),
                                Modifier.size(34.dp),
                                tint = Color.White
                            )
                        }
                        DropdownMenu(overflow, { overflow = false }) {
                            DropdownMenuItem(
                                { Text(stringResource(R.string.share)) },
                                {
                                    overflow = false; vm.launch {
                                    shareText(
                                        context,
                                        w.url,
                                        context.getString(R.string.share)
                                    )
                                }
                                })
                            DropdownMenuItem(
                                { Text(stringResource(R.string.lock_preview)) },
                                { overflow = false; preview = !preview })
                            if (localUri != null) DropdownMenuItem(
                                { Text(stringResource(R.string.share_file)) },
                                { overflow = false; vm.launch { shareFile(context, localUri!!, w.fileType) } })
                            if (localUri != null) DropdownMenuItem({ Text(stringResource(R.string.system_picker)) }, {
                                overflow = false
                                vm.launch {
                                    val picker = WallpaperManager.getInstance(context)
                                        .getCropAndSetWallpaperIntent(Uri.parse(localUri!!))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    context.startActivity(picker)
                                    snackbar.showSnackbar(context.getString(R.string.system_picker_note))
                                }
                            })
                        }
                    }
                    PrimaryButton(R.string.apply, {
                        if (localUri == null) scope.launch { snackbar.showSnackbar(context.getString(R.string.download_first)) }
                        else apply = true
                    }, Modifier.padding(top = 16.dp), !busy)
                }
            }
        } else {
            Column(Modifier
                .fillMaxSize()
                .safeDrawingPadding()) {
                RoundIcon(Icons.AutoMirrored.Outlined.ArrowBack, R.string.back, back, Modifier.padding(24.dp))
                if (error != null) EmptyState(error!!, Modifier.weight(1f), { vm.openId(id) }, true)
                else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
    if (addToCollection && w != null) CollectionPickerSheet(vm, w) { addToCollection = false }
    if (info && w != null) ModalBottomSheet({ info = false }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.info), style = MaterialTheme.typography.headlineSmall)
            Text("${stringResource(R.string.original_resolution)}: ${w.resolution}")
            Text("${stringResource(R.string.file_type)}: ${w.fileType}")
            Text(
                "${stringResource(R.string.file_size)}: ${
                    android.text.format.Formatter.formatFileSize(
                        context,
                        w.fileSize
                    )
                }"
            )
            w.createdAt?.let { Text("${stringResource(R.string.uploaded)}: $it") }
            if (!w.tags.isNullOrEmpty()) Text(
                "${stringResource(R.string.tags)}: ${
                    w.tags.mapNotNull { it.name }.joinToString(", ")
                }"
            )
            TextButton({ vm.launch { openLink(context, w.url) } }) { Text(stringResource(R.string.wallhaven_page)) }
            if (!w.source.isNullOrBlank()) TextButton({ vm.launch { openLink(context, w.source) } }) {
                Text(
                    stringResource(R.string.source)
                )
            }
            if (error != null) {
                Text(stringResource(error!!)); TextButton({ vm.open(w) }) { Text(stringResource(R.string.retry)) }
            }
        }
    }
    if (apply && w != null && localUri != null) CropSheet(
        w,
        localUri!!,
        busy,
        { apply = false }) { target, z, x, y -> vm.apply(w, target, z, x, y) }
    if (applied) AlertDialog(
        { vm.applied.value = false; apply = false },
        icon = { Icon(Icons.Outlined.CheckCircle, null, Modifier.size(64.dp), tint = WalperTokens.Lavender) },
        title = { Text(stringResource(R.string.apply_complete), textAlign = TextAlign.Center) },
        confirmButton = {
            TextButton({
                vm.applied.value = false; apply = false; home()
            }) { Text(stringResource(R.string.back_home)) }
        },
        dismissButton = {
            TextButton({
                vm.applied.value = false; apply = false
            }) { Text(stringResource(R.string.close)) }
        })
}

@Composable
private fun CropSheet(
    w: Wallpaper,
    uri: String,
    busy: Boolean,
    dismiss: () -> Unit,
    apply: (Int, Float, Float, Float) -> Unit
) {
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var x by rememberSaveable { mutableFloatStateOf(.5f) }
    var y by rememberSaveable { mutableFloatStateOf(.5f) }
    var target by rememberSaveable { mutableIntStateOf(WallpaperManager.FLAG_SYSTEM) }
    val screen = LocalContext.current.resources.displayMetrics
    val aspect = screen.widthPixels.toFloat() / screen.heightPixels
    ModalBottomSheet({ if (!busy) dismiss() }) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.crop), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.crop_help), color = MaterialTheme.colorScheme.onSurfaceVariant)
            CropPreview(
                w,
                uri,
                zoom,
                x,
                y,
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(280.dp)
                    .aspectRatio(aspect)
                    .clip(WalperTokens.Card)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, scale, _ ->
                            zoom = (zoom * scale).coerceIn(1f, 3f)
                            x = (x - pan.x / size.width).coerceIn(0f, 1f); y =
                            (y - pan.y / size.height).coerceIn(0f, 1f)
                        }
                    })
            Text(stringResource(R.string.zoom)); Slider(zoom, { zoom = it }, valueRange = 1f..3f, enabled = !busy)
            Text(stringResource(R.string.horizontal)); Slider(x, { x = it }, enabled = !busy)
            Text(stringResource(R.string.vertical)); Slider(y, { y = it }, enabled = !busy)
            listOf(
                WallpaperManager.FLAG_LOCK to R.string.lock_screen,
                WallpaperManager.FLAG_SYSTEM to R.string.home_screen,
                (WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM) to R.string.both_screens
            ).forEach { (value, name) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { target = value },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(target == value, { target = value }, enabled = !busy); Text(stringResource(name))
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryButton(R.string.apply, { apply(target, zoom, x, y) }, enabled = !busy)
        }
    }
}

@Composable
private fun CropPreview(w: Wallpaper, uri: String, zoom: Float, x: Float, y: Float, modifier: Modifier) {
    val context = LocalContext.current
    Layout(content = {
        AsyncImage(
            ImageRequest.Builder(context).data(uri).size(2048, 2048).build(),
            null,
            Modifier,
            contentScale = ContentScale.FillBounds
        )
    }, modifier = modifier) { children, constraints ->
        val frameW = constraints.maxWidth;
        val frameH = constraints.maxHeight
        val originalW = w.width.coerceAtLeast(1);
        val originalH = w.height.coerceAtLeast(1)
        val scale = max(frameW.toFloat() / originalW, frameH.toFloat() / originalH) * zoom
        val imageW = (originalW * scale).roundToInt().coerceAtLeast(frameW)
        val imageH = (originalH * scale).roundToInt().coerceAtLeast(frameH)
        val child = children.first().measure(Constraints.fixed(imageW, imageH))
        layout(frameW, frameH) {
            child.place(
                -((imageW - frameW) * x).roundToInt(),
                -((imageH - frameH) * y).roundToInt()
            )
        }
    }
}
