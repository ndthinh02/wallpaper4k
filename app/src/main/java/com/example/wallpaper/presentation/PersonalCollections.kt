@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.wallpaper.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.wallpaper.R
import com.example.wallpaper.data.CollectionWallpaper
import com.example.wallpaper.data.UserCollection
import com.example.wallpaper.data.Wallpaper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private val collectionColors = listOf(Color(0xFF8978FF), Color(0xFFEA7395), Color(0xFF46A89B), Color(0xFFE5A34C), Color(0xFF598FDC))
private fun collectionColor(index: Int) = collectionColors[index.coerceIn(collectionColors.indices)]

@Composable
fun CollectionsHub(vm: WalperViewModel, explore: (String) -> Unit, personal: (String) -> Unit) {
    val collections by vm.userCollections.collectAsStateWithLifecycle()
    val wallpapers by vm.collectionWallpapers.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var create by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.collections), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            FilledTonalIconButton({ create = true }) { Icon(Icons.Outlined.Add, stringResource(R.string.create_collection)) }
        }
        Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(WalperTokens.Card)
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
            listOf(R.string.my_collections, R.string.explore_collections).forEachIndexed { index, label ->
                val background by animateColorAsState(if (tab == index) MaterialTheme.colorScheme.primary else Color.Transparent, label = "collection-tab")
                val foreground by animateColorAsState(if (tab == index) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "collection-tab-text")
                Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(background)
                    .selectable(tab == index, role = Role.Tab, onClick = { tab = index }).padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(label), color = foreground, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        AnimatedContent(tab, modifier = Modifier.weight(1f), transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { if (targetState == 0) -it / 10 else it / 10 }) togetherWith fadeOut(tween(140))
        }, label = "collection-content") { selected ->
            if (selected == 1) LazyColumn(contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(localCollections, key = { it.query }) { CollectionCard(vm, it, explore) }
            } else if (collections.isEmpty()) CollectionEmpty(R.string.collections_empty_title, R.string.collections_empty_body,
                R.string.create_collection, { create = true }, Modifier.fillMaxSize().padding(bottom = 100.dp))
            else LazyColumn(contentPadding = PaddingValues(24.dp, 16.dp, 24.dp, 112.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text(stringResource(R.string.collections_local_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(collections, key = { it.id }) { collection ->
                    PersonalCollectionCard(collection, wallpapers.filter { it.collectionId == collection.id }, vm,
                        Modifier.animateItem(), { personal(collection.id) })
                }
            }
        }
    }
    if (create) CollectionEditorSheet(vm, dismiss = { create = false }, saved = { create = false; tab = 0; personal(it) })
}

@Composable
private fun PersonalCollectionCard(collection: UserCollection, entries: List<CollectionWallpaper>, vm: WalperViewModel, modifier: Modifier = Modifier, open: () -> Unit) {
    val accent = collectionColor(collection.color)
    Box(modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(accent).clickable(onClick = open)) {
        entries.firstOrNull()?.let { AsyncImage(vm.repo.wallpaper(it.metadata).thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = .2f), Color.Black.copy(alpha = .78f)))))
        Icon(Icons.Outlined.Folder, null, Modifier.align(Alignment.TopStart).padding(20.dp).size(28.dp), tint = Color.White)
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.align(Alignment.BottomEnd).padding(22.dp).size(24.dp), tint = Color.White)
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp).padding(end = 36.dp)) {
            Text(collection.name, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.results_count, entries.size), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun CollectionEmpty(title: Int, body: Int, action: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(28.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
        Text(stringResource(body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp), style = MaterialTheme.typography.bodyMedium)
        Button(onClick, shape = WalperTokens.Card, contentPadding = PaddingValues(24.dp, 14.dp)) {
            Icon(Icons.Outlined.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(stringResource(action))
        }
    }
}

@Composable
fun CollectionEditorSheet(vm: WalperViewModel, existing: UserCollection? = null, initial: Wallpaper? = null, dismiss: () -> Unit, saved: (String) -> Unit) {
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name ?: "") }
    var color by rememberSaveable(existing?.id) { mutableIntStateOf(existing?.color ?: 0) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val accent by animateColorAsState(collectionColor(color), tween(250), label = "collection-accent")
    val submit: () -> Unit = {
        if (!saving && name.trim().isNotEmpty()) {
            saving = true; error = null
            scope.launch {
                try {
                    val id = vm.repo.saveCollection(name, color, existing?.id, initial)
                    sheetState.hide()
                    saved(id)
                }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    error = if (e is IllegalArgumentException) e.message ?: vm.repo.text(R.string.operation_failed)
                        else vm.repo.text(R.string.operation_failed)
                }
                finally { saving = false }
            }
        }
    }
    ModalBottomSheet(onDismissRequest = { if (!saving) dismiss() }, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(112.dp).clip(WalperTokens.Card).background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = .5f)))), contentAlignment = Alignment.Center) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, null, Modifier.size(36.dp), tint = Color.White)
                    Spacer(Modifier.width(16.dp))
                    Text(name.trim().ifBlank { stringResource(R.string.new_collection) }, color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Text(stringResource(if (existing == null) R.string.create_collection else R.string.edit_collection), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.collections_local_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(name, { if (it.length <= 60) { name = it; error = null } }, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.collection_name)) }, singleLine = true, enabled = !saving, shape = WalperTokens.Card,
                isError = error != null, supportingText = { Text(error ?: "${name.length}/60") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { submit() }))
            Text(stringResource(R.string.collection_color), style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                collectionColors.forEachIndexed { index, value ->
                    val scale by animateFloatAsState(if (color == index) 1.08f else 1f, label = "color-selection")
                    Box(Modifier.weight(1f).height(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
                        .background(value).selectable(color == index, enabled = !saving, role = Role.RadioButton, onClick = { color = index }), contentAlignment = Alignment.Center) {
                        if (color == index) Icon(Icons.Outlined.Check, stringResource(R.string.collection_color_selected, index + 1), tint = Color.White)
                        else Text("${index + 1}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Button(submit, Modifier.fillMaxWidth().height(54.dp), enabled = name.trim().isNotEmpty() && !saving, shape = WalperTokens.Card) {
                if (saving) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text(stringResource(if (existing == null) R.string.create_collection else R.string.save_collection))
            }
        }
    }
}

@Composable
fun CollectionPickerSheet(vm: WalperViewModel, wallpaper: Wallpaper, dismiss: () -> Unit) {
    val collections by vm.userCollections.collectAsStateWithLifecycle()
    val entries by vm.collectionWallpapers.collectAsStateWithLifecycle()
    val membership = entries.filter { it.wallpaperId == wallpaper.id }.map { it.collectionId }.toSet()
    var create by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    if (create) {
        CollectionEditorSheet(vm, initial = wallpaper, dismiss = { create = false }, saved = { create = false })
    } else ModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.add_to_collection), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.collection_picker_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton({ create = true }, Modifier.fillMaxWidth().height(50.dp), shape = WalperTokens.Card) {
                Icon(Icons.Outlined.CreateNewFolder, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.create_collection))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(collections, key = { it.id }) { collection ->
                    val selected = collection.id in membership
                    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else MaterialTheme.colorScheme.surfaceVariant, label = "collection-membership")
                    Row(Modifier.fillMaxWidth().clip(WalperTokens.Card).background(background)
                        .selectable(selected, enabled = pending == null, role = Role.Checkbox, onClick = {
                            pending = collection.id; error = null
                            scope.launch {
                                try {
                                    if (selected) vm.repo.dao.removeCollectionWallpaper(collection.id, wallpaper.id)
                                    else vm.repo.dao.addCollectionWallpaper(CollectionWallpaper(collection.id, wallpaper.id, vm.repo.metadata(wallpaper)))
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = vm.repo.text(R.string.operation_failed) }
                                finally { pending = null }
                            }
                        }).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Folder, null, tint = collectionColor(collection.color))
                        Text(collection.name, Modifier.weight(1f).padding(horizontal = 14.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (pending == collection.id) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Checkbox(selected, onCheckedChange = null)
                    }
                }
            }
            Button(dismiss, Modifier.fillMaxWidth().height(50.dp), shape = WalperTokens.Card) { Text(stringResource(R.string.collection_done)) }
        }
    }
}

@Composable
fun PersonalCollectionScreen(vm: WalperViewModel, id: String, back: () -> Unit, open: (Wallpaper) -> Unit, browse: () -> Unit) {
    val collections by vm.userCollections.collectAsStateWithLifecycle()
    val entries by vm.collectionWallpapers.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val collection = collections.firstOrNull { it.id == id }
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val wallpapers = remember(entries, id) { entries.filter { it.collectionId == id }.map { vm.repo.wallpaper(it.metadata) } }
    var localImages by remember(id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    LaunchedEffect(downloads, wallpapers) {
        val ids = wallpapers.map { it.id }.toSet()
        localImages = downloads.filter { it.id in ids && it.status == "complete" && vm.repo.valid(it.uri) }
            .associate { it.id to it.uri }
    }
    var edit by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    var manage by rememberSaveable { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(collection?.name ?: stringResource(R.string.collections), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton({ edit = true }, enabled = collection != null) { Icon(Icons.Outlined.Edit, stringResource(R.string.edit_collection)) }
            IconButton({ deleting = true }, enabled = collection != null) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_collection)) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.results_count, wallpapers.size), Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (wallpapers.isNotEmpty()) TextButton({ manage = !manage }) { Text(stringResource(if (manage) R.string.collection_done else R.string.manage_collection)) }
            FilledTonalIconButton({ adding = true }, enabled = collection != null) { Icon(Icons.Outlined.Add, stringResource(R.string.collection_add_images)) }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 24.dp), color = MaterialTheme.colorScheme.error) }
        AnimatedContent(wallpapers.isEmpty(), Modifier.weight(1f), transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "collection-gallery") { empty ->
            if (empty) CollectionEmpty(R.string.collection_empty_title, R.string.collection_empty_body, R.string.collection_add_images, { adding = true }, Modifier.fillMaxSize())
            else LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(24.dp, 8.dp, 24.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(wallpapers, key = { it.id }) { wallpaper ->
                    Box(Modifier.animateItem()) {
                        WallpaperCard(wallpaper, favorites.any { it.id == wallpaper.id }, true, {
                            vm.prepareDetailSource(wallpapers); open(wallpaper)
                        }, { vm.favorite(wallpaper) }, localImages[wallpaper.id])
                        if (manage) FilledIconButton(onClick = {
                            removing = wallpaper.id; error = null
                            scope.launch {
                                try { vm.repo.dao.removeCollectionWallpaper(id, wallpaper.id) }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = vm.repo.text(R.string.operation_failed) }
                                finally { removing = null }
                            }
                        }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), enabled = removing == null,
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .65f), contentColor = Color.White)) {
                            Icon(Icons.Outlined.Remove, stringResource(R.string.remove_from_collection))
                        }
                    }
                }
            }
        }
    }
    if (edit && collection != null) CollectionEditorSheet(vm, existing = collection, dismiss = { edit = false }, saved = { edit = false })
    if (adding && collection != null) CollectionImagePicker(vm, id, browse = { adding = false; browse() }, dismiss = { adding = false })
    if (deleting && collection != null) AlertDialog(
        onDismissRequest = { if (!deleteBusy) deleting = false },
        icon = { Icon(Icons.Outlined.DeleteOutline, null) },
        title = { Text(stringResource(R.string.delete_collection)) },
        text = { Text(stringResource(R.string.delete_collection_message, collection.name)) },
        confirmButton = { TextButton({
            deleteBusy = true
            scope.launch {
                try { vm.repo.dao.deleteCollection(id); deleting = false; back() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = vm.repo.text(R.string.operation_failed); deleting = false }
                finally { deleteBusy = false }
            }
        }, enabled = !deleteBusy) { Text(stringResource(R.string.delete_collection), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ deleting = false }, enabled = !deleteBusy) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun CollectionImagePicker(vm: WalperViewModel, collectionId: String, browse: () -> Unit, dismiss: () -> Unit) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val entries by vm.collectionWallpapers.collectAsStateWithLifecycle()
    val candidates = remember(favorites, downloads, home.items) {
        (favorites.map { vm.repo.wallpaper(it.metadata) } + downloads.map { vm.repo.wallpaper(it.metadata) } + home.items).distinctBy { it.id }
    }
    val membership = entries.filter { it.collectionId == collectionId }.map { it.wallpaperId }.toSet()
    var pending by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.collection_add_images), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.collection_images_note), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 360.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(candidates, key = { it.id }) { wallpaper ->
                    val selected = wallpaper.id in membership
                    Box(Modifier.aspectRatio(.75f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .selectable(selected, enabled = pending == null, role = Role.Checkbox, onClick = {
                            pending = wallpaper.id; error = null
                            scope.launch {
                                try {
                                    if (selected) vm.repo.dao.removeCollectionWallpaper(collectionId, wallpaper.id)
                                    else vm.repo.dao.addCollectionWallpaper(CollectionWallpaper(collectionId, wallpaper.id, vm.repo.metadata(wallpaper)))
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = vm.repo.text(R.string.operation_failed) }
                                finally { pending = null }
                            }
                        }).border(if (selected) 3.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp))) {
                        AsyncImage(wallpaper.thumbnail, wallpaper.resolution, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (selected) Icon(Icons.Outlined.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color.Black.copy(alpha = .5f), CircleShape), tint = Color.White)
                        if (pending == wallpaper.id) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    }
                }
            }
            OutlinedButton(browse, Modifier.fillMaxWidth(), shape = WalperTokens.Card) { Text(stringResource(R.string.collection_browse_more)) }
            Button(dismiss, Modifier.fillMaxWidth().height(50.dp), shape = WalperTokens.Card) { Text(stringResource(R.string.collection_done)) }
        }
    }
}
