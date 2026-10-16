package com.example.wallpaper.repository

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.*
import com.example.wallpaper.MainActivity
import com.example.wallpaper.R
import com.example.wallpaper.data.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.*

class AppRepository(val context: Context) {
    @Volatile
    var language: String = "vi"
    fun text(id: Int): String =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(language))
        }).getString(id)

    val gson = Gson()
    val dao = Room.databaseBuilder(context, LocalDatabase::class.java, "walper.db")
        .addMigrations(LocalDatabase.MIGRATION_1_2).build().dao()
    val settings = SettingsRepository(context)
    val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(5, TimeUnit.MINUTES).retryOnConnectionFailure(false).build()
    val api = Retrofit.Builder().baseUrl("https://wallhaven.cc/api/v1/").client(client)
        .addConverterFactory(GsonBodyConverter(gson)).build().create(WallhavenApi::class.java)
    private val favoriteMutex = Mutex()
    private val coverMutex = Mutex()
    private val collectionMutex = Mutex()
    suspend fun saveCollection(name: String, color: Int, existingId: String? = null, initial: Wallpaper? = null): String =
        collectionMutex.withLock {
            val clean = name.trim()
            require(clean.isNotBlank() && clean.length <= 60) { text(R.string.collection_name_invalid) }
            val key = clean.lowercase(Locale.ROOT)
            val duplicate = dao.collectionByName(key)
            require(duplicate == null || duplicate.id == existingId) { text(R.string.collection_name_exists) }
            val id = existingId ?: UUID.randomUUID().toString()
            if (existingId == null) {
                dao.createCollectionWithWallpaper(UserCollection(id, clean, key, color.coerceIn(0, 4)),
                    initial?.let { CollectionWallpaper(id, it.id, metadata(it)) })
            } else dao.updateCollection(id, clean, key, color.coerceIn(0, 4))
            id
        }
    fun metadata(w: Wallpaper): String = gson.toJson(w)
    fun wallpaper(json: String): Wallpaper = gson.fromJson(json, Wallpaper::class.java)
    suspend fun toggleFavorite(w: Wallpaper) = favoriteMutex.withLock {
        if (dao.favorite(w.id) == null) dao.saveFavorite(Favorite(w.id, metadata(w))) else dao.removeFavorite(w.id)
    }

    suspend fun cover(query: String) = coverMutex.withLock {
        if (dao.cover(query) == null) {
            val result = api.search(Filters().parameters(query, 1, null))
            result.data?.mapNotNull { it?.cleaned() }?.firstOrNull { it.thumbnail.isNotBlank() }?.let {
                dao.saveCover(CollectionCover(query, it.thumbnail, result.meta?.total ?: 0))
            }
        }
    }

    fun download(w: Wallpaper) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf("metadata" to metadata(w)))
            .addTag("download").addTag("download_${w.id}").build()
        WorkManager.getInstance(context).enqueueUniqueWork("download_${w.id}", ExistingWorkPolicy.KEEP, request)
    }

    suspend fun valid(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.read() >= 0 } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveImage(w: Wallpaper, progress: suspend (Int) -> Unit): Uri = withContext(Dispatchers.IO) {
        dao.download(w.id)?.takeIf { it.status == "complete" && valid(it.uri) }
            ?.let { return@withContext Uri.parse(it.uri) }
        require(w.path.startsWith("https://"))
        if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        )
            throw IOException(text(R.string.storage_permission))
        var uri: Uri? = null
        var legacyFile: File? = null
        try {
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "walper-${w.id}.${if (w.fileType == "image/png") "png" else "jpg"}"
                )
                put(MediaStore.Images.Media.MIME_TYPE, w.fileType)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Walper")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                } else {
                    @Suppress("DEPRECATION") val folder =
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Walper")
                    if (!folder.exists() && !folder.mkdirs()) throw IOException("Cannot create Pictures/Walper")
                    legacyFile = File(folder, "walper-${w.id}.${if (w.fileType == "image/png") "png" else "jpg"}")
                    @Suppress("DEPRECATION") put(MediaStore.Images.Media.DATA, legacyFile!!.absolutePath)
                }
            }
            uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert failed")
            dao.saveDownload(Download(w.id, metadata(w), uri.toString(), "downloading"))
            client.newCall(Request.Builder().url(w.path).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("Empty response")
                if (body.contentType()?.type != "image") throw IOException("Response is not an image")
                val size = body.contentLength();
                var count = 0L;
                var previous = -1
                body.byteStream().use { input ->
                    context.contentResolver.openOutputStream(uri!!)?.use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            ensureActive()
                            val n = input.read(buffer); if (n == -1) break
                            output.write(buffer, 0, n); count += n
                            val percent = if (size > 0) ((count * 100 / size).toInt().coerceAtMost(99)) else 0
                            if (percent != previous) {
                                progress(percent); previous = percent
                            }
                        }
                        output.flush()
                    } ?: throw IOException("Cannot open MediaStore file")
                }
                if (count == 0L || (size > 0 && count != size)) throw IOException("Incomplete download")
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri!!)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException(text(R.string.operation_failed))
            if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(
                uri!!,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
            dao.saveDownload(Download(w.id, metadata(w), uri.toString()))
            progress(100)
            event("download", w)
            uri!!
        } catch (e: Exception) {
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            legacyFile?.delete()
            throw e
        }
    }

    suspend fun deleteFile(item: Download) = withContext(Dispatchers.IO) {
        context.contentResolver.delete(Uri.parse(item.uri), null, null)
        dao.removeDownload(item.id)
    }

    suspend fun apply(uri: Uri, target: Int, zoom: Float = 1f, horizontal: Float = .5f, vertical: Float = .5f) =
        withContext(Dispatchers.IO) {
            val manager = WallpaperManager.getInstance(context)
            check(manager.isWallpaperSupported && manager.isSetWallpaperAllowed) { text(R.string.apply_unavailable) }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            require(options.outWidth > 0 && options.outHeight > 0) { text(R.string.file_missing) }
            options.inJustDecodeBounds = false; options.inPreferredConfig = Bitmap.Config.ARGB_8888
            // Bound decode to four million pixels before cropping; never decode an unbounded original.
            while (options.outWidth.toLong() * options.outHeight / (options.inSampleSize.coerceAtLeast(1).toLong()
                    .let { it * it }) > 4_000_000
            ) {
                options.inSampleSize = options.inSampleSize.coerceAtLeast(1) * 2
            }
            val bitmap =
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?: throw IOException(text(R.string.file_missing))
            val display = context.resources.displayMetrics
            val ratio = display.widthPixels.toFloat() / display.heightPixels
            val cropWidth = min(bitmap.width.toFloat(), bitmap.height * ratio) / zoom.coerceIn(1f, 3f)
            val cropHeight = cropWidth / ratio
            val left = ((bitmap.width - cropWidth) * horizontal.coerceIn(0f, 1f)).toInt()
            val top = ((bitmap.height - cropHeight) * vertical.coerceIn(0f, 1f)).toInt()
            try {
                val result = manager.setBitmap(
                    bitmap,
                    Rect(
                        left,
                        top,
                        left + cropWidth.toInt().coerceAtLeast(1),
                        top + cropHeight.toInt().coerceAtLeast(1)
                    ),
                    true,
                    target
                )
                check(result > 0) { text(R.string.apply_unconfirmed) }
            } finally {
                bitmap.recycle()
            }
        }

    suspend fun event(type: String, w: Wallpaper) {
        val id = dao.event(ActivityEvent(type = type, wallpaperId = w.id, metadata = metadata(w)))
        val prefs = settings.settings.first()
        language = prefs.language
        if (!prefs.notifications || (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED)
        ) return
        val title = text(
            when (type) {
                "download" -> R.string.download_complete; "auto" -> R.string.auto_complete; else -> R.string.apply_complete
            }
        )
        val manager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(
                "activity",
                text(R.string.notifications),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        val pending = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java).putExtra("wallpaperId", w.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            manager.notify(
                id.toInt(), NotificationCompat.Builder(context, "activity").setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title).setContentText(w.resolution).setContentIntent(pending).setAutoCancel(true)
                    .build()
            )
        } catch (_: SecurityException) { /* Local history is retained without notification permission. */
        }
    }

    suspend fun configureAuto(enabled: Boolean, hours: Int, target: Int) {
        if (enabled) {
            check(
                WallpaperManager.getInstance(context)
                    .let { it.isWallpaperSupported && it.isSetWallpaperAllowed }) { text(R.string.apply_unavailable) }
            check(
                dao.downloadList()
                    .any { it.status == "complete" && valid(it.uri) }) { text(R.string.auto_needs_download) }
        }
        settings.auto(enabled, hours, target)
        val work = WorkManager.getInstance(context)
        if (enabled) work.enqueueUniquePeriodicWork(
            "auto_wallpaper", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoWallpaperWorker>(hours.toLong(), TimeUnit.HOURS).build()
        )
        else work.cancelUniqueWork("auto_wallpaper")
    }
}

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as com.example.wallpaper.WalperApplication).repository
        val json = inputData.getString("metadata") ?: return Result.failure()
        val w = repo.wallpaper(json)
        return try {
            val existing = repo.dao.download(w.id)
            if (existing != null && existing.status == "complete" && repo.valid(existing.uri)) return Result.success()
            if (existing != null && existing.status != "complete" && existing.uri.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        applicationContext.contentResolver.delete(
                            Uri.parse(existing.uri),
                            null,
                            null
                        )
                    }
                }
            }
            repo.dao.saveDownload(Download(w.id, json, "", "downloading"))
            repo.saveImage(w) { setProgress(workDataOf("percent" to it)) }
            Result.success()
        } catch (e: CancellationException) {
            withContext(NonCancellable) { repo.dao.saveDownload(Download(w.id, json, "", "interrupted")) }; throw e
        } catch (e: Exception) {
            repo.dao.saveDownload(Download(w.id, json, "", "failed"))
            Result.failure(workDataOf("error" to (e.message ?: "Download failed")))
        }
    }
}

class AutoWallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as com.example.wallpaper.WalperApplication).repository
        val prefs = repo.settings.settings.first()
        repo.language = prefs.language
        if (!prefs.auto) return Result.success()
        val images = repo.dao.downloadList().filter { it.status == "complete" && repo.valid(it.uri) }
        if (images.isEmpty()) {
            repo.configureAuto(false, prefs.hours, prefs.target); return Result.success()
        }
        val choice = images.filter { it.id != prefs.lastWallpaper }.ifEmpty { images }.random()
        return try {
            repo.apply(Uri.parse(choice.uri), prefs.target)
            repo.settings.text("lastWallpaper", choice.id)
            repo.event("auto", repo.wallpaper(choice.metadata)); Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            repo.configureAuto(false, prefs.hours, prefs.target); Result.failure()
        }
    }
}
