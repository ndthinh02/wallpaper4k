@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.wallpaper.presentation

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.*
import coil.compose.AsyncImage
import coil.imageLoader
import com.example.wallpaper.BuildConfig
import com.example.wallpaper.R
import com.example.wallpaper.data.*
import com.example.wallpaper.repository.Settings
import kotlinx.coroutines.*
import java.text.DateFormat
import java.util.Date

@Composable
fun DownloadsScreen(
    vm: WalperViewModel,
    back: () -> Unit,
    open: (Wallpaper) -> Unit,
    home: () -> Unit,
    snackbar: SnackbarHostState
) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var oldest by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Download?>(null) }
    var systemDeletion by remember { mutableStateOf<Download?>(null) }
    var exists by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    suspend fun validate() {
        exists = downloads.associate { it.id to (it.status == "complete" && vm.repo.valid(it.uri)) }
    }
    LaunchedEffect(downloads) { validate() }
    LifecycleResumeEffect(downloads) { scope.launch { validate() }; onPauseOrDispose { } }
    val deleteLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val item = systemDeletion
            if (result.resultCode == Activity.RESULT_OK && item != null) vm.launch {
                if (Build.VERSION.SDK_INT == 29) vm.repo.deleteFile(item) else vm.repo.dao.removeDownload(item.id)
            }
            systemDeletion = null
        }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) systemDeletion?.let { vm.repo.download(vm.repo.wallpaper(it.metadata)) }
        else scope.launch { snackbar.showSnackbar(context.getString(R.string.storage_permission)) }
        systemDeletion = null
    }
    Column(Modifier
        .fillMaxSize()
        .safeDrawingPadding()) {
        BackHeader(R.string.downloads, back)
        Row(Modifier.padding(horizontal = 24.dp)) {
            FilterChip(
                oldest,
                { oldest = !oldest },
                { Text(stringResource(if (oldest) R.string.oldest else R.string.newest)) })
        }
        if (downloads.isEmpty()) EmptyState(R.string.no_downloads, Modifier.weight(1f), home)
        else LazyVerticalGrid(
            columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(if (oldest) downloads.reversed() else downloads, key = { it.id }) { item ->
                val w = remember(item.metadata) { vm.repo.wallpaper(item.metadata) }
                var menu by remember { mutableStateOf(false) }
                val valid = exists[item.id] == true
                Card(
                    shape = WalperTokens.Card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier
                        .fillMaxWidth()
                        .clickable {
                            val ordered = if(oldest) downloads.reversed() else downloads
                            vm.prepareDetailSource(ordered.map { vm.repo.wallpaper(it.metadata) })
                            open(w)
                        }) {
                        AsyncImage(
                            if (valid) item.uri else w.thumbnail,
                            null,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(.64f)
                                .clip(WalperTokens.Card),
                            contentScale = ContentScale.Crop
                        )
                        Row(Modifier
                            .fillMaxWidth()
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(w.resolution, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                        .format(Date(item.savedAt)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    stringResource(
                                        when {
                                            item.status == "downloading" -> R.string.downloading; item.status != "complete" -> R.string.download_failed; !valid -> R.string.file_missing; else -> R.string.saved_offline
                                        }
                                    ), Modifier.padding(top = 8.dp), fontSize = 12.sp
                                )
                            }
                            Box {
                                IconButton({ menu = true }) {
                                    Icon(
                                        Icons.Outlined.MoreVert,
                                        stringResource(R.string.overflow)
                                    )
                                }
                                DropdownMenu(menu, { menu = false }) {
                                    if (valid) DropdownMenuItem(
                                        { Text(stringResource(R.string.share_file)) },
                                        { menu = false; vm.launch { shareFile(context, item.uri, w.fileType) } })
                                    if (!valid && item.status != "downloading") DropdownMenuItem(
                                        { Text(stringResource(R.string.retry)) },
                                        {
                                            menu = false
                                            if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                ) != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                systemDeletion =
                                                    item; permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                            } else vm.repo.download(w)
                                        })
                                    DropdownMenuItem(
                                        { Text(stringResource(R.string.remove_history)) },
                                        { menu = false; vm.launch { vm.repo.dao.removeDownload(item.id) } },
                                        enabled = item.status != "downloading"
                                    )
                                    if (valid) DropdownMenuItem(
                                        { Text(stringResource(R.string.delete_file)) },
                                        { menu = false; deleting = item })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (deleting != null) AlertDialog(
        { deleting = null },
        title = { Text(stringResource(R.string.delete_file)) },
        text = { Text(stringResource(R.string.delete_confirm)) },
        confirmButton = {
            TextButton({
                val item = deleting!!; deleting = null
                scope.launch {
                    try {
                        vm.repo.deleteFile(item)
                    } catch (e: Exception) {
                        systemDeletion = item
                        when {
                            Build.VERSION.SDK_INT >= 30 && e is SecurityException -> deleteLauncher.launch(
                                IntentSenderRequest.Builder(
                                    MediaStore.createDeleteRequest(
                                        context.contentResolver,
                                        listOf(Uri.parse(item.uri))
                                    ).intentSender
                                ).build()
                            )

                            Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException -> deleteLauncher.launch(
                                IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                            )

                            else -> snackbar.showSnackbar(context.getString(R.string.operation_failed))
                        }
                    }
                }
            }) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun NotificationsScreen(vm: WalperViewModel, back: () -> Unit, open: (Wallpaper) -> Unit) {
    val events by vm.events.collectAsStateWithLifecycle()
    Column(Modifier
        .fillMaxSize()
        .safeDrawingPadding()) {
        BackHeader(R.string.notifications, back)
        if (events.isEmpty()) EmptyState(R.string.no_notifications, Modifier.weight(1f))
        else LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(events, key = { it.eventId }) { event ->
                val w = remember(event.metadata) { vm.repo.wallpaper(event.metadata) }
                val state = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                    if (value != SwipeToDismissBoxValue.Settled) vm.launch { vm.repo.dao.removeEvent(event.eventId) }
                    true
                })
                SwipeToDismissBox(state, backgroundContent = {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(WalperTokens.Card)
                            .background(WalperTokens.Pink)
                            .padding(24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), tint = Color.White)
                    }
                }) {
                    Row(Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            vm.launch { vm.repo.dao.markRead(event.eventId) }; open(w)
                        }
                        .padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier
                            .weight(1f)
                            .padding(end = 16.dp)) {
                            Text(
                                stringResource(
                                    when (event.type) {
                                        "download" -> R.string.download_complete; "auto" -> R.string.auto_complete; else -> R.string.apply_complete
                                    }
                                ), fontWeight = if (event.read) FontWeight.Normal else FontWeight.Bold
                            )
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(event.time)),
                                Modifier.padding(top = 20.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AsyncImage(
                            w.thumbnail,
                            null,
                            Modifier
                                .size(76.dp)
                                .clip(WalperTokens.Card),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: Int,
    subtitle: String? = null,
    click: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (click != null) Modifier.clickable(onClick = click) else Modifier)
            .padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.size(28.dp))
        Column(Modifier
            .weight(1f)
            .padding(horizontal = 20.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        if (trailing != null) trailing() else if (click != null) Icon(Icons.Outlined.ChevronRight, null)
    }
}

@Composable
fun SettingsScreen(vm: WalperViewModel, settings: Settings, snackbar: SnackbarHostState) {
    var theme by rememberSaveable { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(false) }
    var auto by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        vm.launch { vm.repo.settings.flag("notifications", allowed) }
        if (!allowed) scope.launch { snackbar.showSnackbar(context.getString(R.string.notification_denied)) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
        item { PageTitle(R.string.settings) }
        item {
            SettingRow(
                Icons.Outlined.Translate,
                R.string.language,
                stringResource(if (settings.language == "vi") R.string.language_vi else R.string.language_en),
                { language = true })
        }
        item {
            SettingRow(
                Icons.Outlined.DarkMode, R.string.theme, stringResource(
                    when (settings.theme) {
                        "dark" -> R.string.dark; "light" -> R.string.light; else -> R.string.system
                    }
                ), { theme = true })
        }
        item {
            SettingRow(
                Icons.Outlined.Autorenew,
                R.string.auto_wallpaper,
                stringResource(R.string.auto_description),
                { auto = true }) {
                Switch(
                    settings.auto,
                    { enabled ->
                        if (enabled) auto = true else vm.launch {
                            vm.repo.configureAuto(
                                false,
                                settings.hours,
                                settings.target
                            )
                        }
                    })
            }
        }
        item {
            SettingRow(
                Icons.Outlined.NotificationsNone,
                R.string.enable_notifications,
                stringResource(R.string.notification_description)
            ) {
                Switch(settings.notifications, { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    )
                        permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else vm.launch { vm.repo.settings.flag("notifications", enabled) }
                })
            }
        }
        item {
            SettingRow(Icons.Outlined.CleaningServices, R.string.clear_cache, click = {
                vm.launch {
                    withContext(Dispatchers.IO) { context.imageLoader.diskCache?.clear() }; context.imageLoader.memoryCache?.clear(); snackbar.showSnackbar(
                    context.getString(R.string.cache_cleared)
                )
                }
            })
        }
        item {
            Column(Modifier
                .fillMaxWidth()
                .padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.source_info), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    Modifier.padding(top = 32.dp),
                    color = WalperTokens.Lavender,
                    fontSize = 12.sp
                )
            }
        }
    }
    if (theme) ChoiceDialog(
        R.string.theme,
        listOf("light" to R.string.light, "dark" to R.string.dark, "system" to R.string.system),
        settings.theme,
        { theme = false }) {
        vm.launch { vm.repo.settings.text("theme", it) }; theme = false
    }
    if (language) AlertDialog({ language = false }, title = { Text(stringResource(R.string.language)) }, text = {
        Column {
            listOf("vi" to R.string.language_vi, "en" to R.string.language_en).forEach { (code, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.launch { vm.repo.settings.text("language", code) }; language = false },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        settings.language == code,
                        { vm.launch { vm.repo.settings.text("language", code) }; language = false }); Text(
                    stringResource(label)
                )
                }
            }
        }
    }, confirmButton = { TextButton({ language = false }) { Text(stringResource(R.string.close)) } })
    if (auto) AutoDialog(settings, { auto = false }) { enabled, hours, target ->
        vm.launch {
            vm.repo.configureAuto(
                enabled,
                hours,
                target
            ); auto = false
        }
    }
}

@Composable
private fun ChoiceDialog(
    title: Int,
    choices: List<Pair<String, Int>>,
    selected: String,
    dismiss: () -> Unit,
    choose: (String) -> Unit
) {
    AlertDialog(dismiss, title = { Text(stringResource(title)) }, text = {
        Column {
            choices.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { choose(value) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected == value, { choose(value) }); Text(stringResource(label))
                }
            }
        }
    }, confirmButton = { TextButton(dismiss) { Text(stringResource(R.string.close)) } })
}

@Composable
private fun AutoDialog(settings: Settings, dismiss: () -> Unit, save: (Boolean, Int, Int) -> Unit) {
    var hours by rememberSaveable { mutableIntStateOf(settings.hours) }
    var target by rememberSaveable { mutableIntStateOf(settings.target) }
    AlertDialog(
        dismiss,
        title = { Text(stringResource(R.string.auto_wallpaper)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.auto_description)); Text(
                stringResource(R.string.interval),
                Modifier.padding(top = 16.dp),
                fontWeight = FontWeight.Bold
            )
                listOf(1, 6, 24).forEach { value ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { hours = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(hours == value, { hours = value }); Text(stringResource(R.string.hours, value))
                    }
                }
                Text(stringResource(R.string.target), fontWeight = FontWeight.Bold)
                listOf(
                    1 to R.string.home_screen,
                    2 to R.string.lock_screen,
                    3 to R.string.both_screens
                ).forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { target = value },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(target == value, { target = value }); Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = { TextButton({ save(true, hours, target) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun PrivacyScreen(back: () -> Unit) {
    Column(Modifier
        .fillMaxSize()
        .safeDrawingPadding()) {
        BackHeader(R.string.privacy, back)
        Text(
            stringResource(R.string.privacy_body),
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ReportScreen(back: () -> Unit) {
    val context = LocalContext.current
    var email by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var preview by rememberSaveable { mutableStateOf(false) }
    val report =
        "${context.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n${Build.MANUFACTURER} ${Build.MODEL}\n\n${
            context.getString(R.string.email)
        }: $email\n${context.getString(R.string.subject)}: $subject\n\n$message"
    Column(Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .imePadding()
        .verticalScroll(rememberScrollState())) {
        BackHeader(R.string.report, back)
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.report_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                email,
                { email = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.email)) },
                shape = WalperTokens.Card,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            OutlinedTextField(
                subject,
                { subject = it },
                Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.subject)) },
                shape = WalperTokens.Card,
                singleLine = true
            )
            OutlinedTextField(
                message,
                { message = it },
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = { Text(stringResource(R.string.message)) },
                shape = WalperTokens.Card
            )
            PrimaryButton(
                R.string.preview_report,
                { preview = true },
                enabled = subject.isNotBlank() && message.isNotBlank()
            )
        }
    }
    if (preview) AlertDialog(
        { preview = false },
        title = { Text(stringResource(R.string.report_preview)) },
        text = { Text(report, Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = {
            TextButton({
                shareText(context, report, context.getString(R.string.report)); preview = false
            }) { Text(stringResource(R.string.share)) }
        },
        dismissButton = { TextButton({ preview = false }) { Text(stringResource(R.string.cancel)) } })
}
