package com.example.wallpaper

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.wallpaper.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CollectionsDatabaseTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private inline fun <T> LocalDatabase.withDatabase(block: (LocalDatabase) -> T): T =
        try { block(this) } finally { close() }

    @Test
    fun migrationPreservesExistingDataAndCollectionsSurviveReopen() = runBlocking {
        val name = "collections-test-${UUID.randomUUID()}.db"
        try {
            context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
                db.execSQL("CREATE TABLE favorites (id TEXT NOT NULL PRIMARY KEY, metadata TEXT NOT NULL, savedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE downloads (id TEXT NOT NULL PRIMARY KEY, metadata TEXT NOT NULL, uri TEXT NOT NULL, status TEXT NOT NULL, savedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE history (query TEXT NOT NULL PRIMARY KEY, searchedAt INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE activity (eventId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, wallpaperId TEXT NOT NULL, metadata TEXT NOT NULL, time INTEGER NOT NULL, read INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE covers (query TEXT NOT NULL PRIMARY KEY, thumbnail TEXT NOT NULL, total INTEGER NOT NULL, cachedAt INTEGER NOT NULL)")
                db.execSQL("INSERT INTO favorites VALUES ('abc123', '{}', 1)")
                db.execSQL("INSERT INTO history VALUES ('nature', 1)")
                db.execSQL("INSERT INTO downloads VALUES ('abc123', '{}', 'content://saved/image', 'complete', 1)")
                db.version = 1
            }
            fun open() = Room.databaseBuilder(context, LocalDatabase::class.java, name)
                .addMigrations(LocalDatabase.MIGRATION_1_2).build()
            open().withDatabase { db ->
                val dao = db.dao()
                assertNotNull(dao.favorite("abc123"))
                assertEquals("nature", dao.history().first().single().query)
                assertEquals("complete", dao.download("abc123")?.status)
                dao.createCollectionWithWallpaper(UserCollection("one", "Nature", "nature", 2),
                    CollectionWallpaper("one", "abc123", "{}"))
            }
            open().withDatabase { db ->
                assertEquals("Nature", db.dao().userCollections().first().single().name)
                assertEquals("abc123", db.dao().collectionWallpapers().first().single().wallpaperId)
                db.dao().updateCollection("one", "Landscape", "landscape", 3)
                assertNull(db.dao().collectionByName("nature"))
                assertEquals("Landscape", db.dao().collectionByName("landscape")?.name)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun membershipIsUniqueAndDeleteOnlyRemovesItsOwnEntries() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build().withDatabase { db ->
            val dao = db.dao()
            dao.saveFavorite(Favorite("abc123", "{}"))
            dao.createCollection(UserCollection("one", "First", "first"))
            dao.createCollection(UserCollection("two", "Second", "second"))
            assertTrue(runCatching { dao.createCollection(UserCollection("three", "First", "first")) }.isFailure)
            val entry = CollectionWallpaper("one", "abc123", "{}")
            dao.addCollectionWallpaper(entry)
            dao.addCollectionWallpaper(entry)
            dao.addCollectionWallpaper(entry.copy(collectionId = "two"))
            assertEquals(2, dao.collectionWallpapers().first().size)
            dao.removeCollectionWallpaper("one", "abc123")
            assertEquals("two", dao.collectionWallpapers().first().single().collectionId)
            dao.addCollectionWallpaper(entry)
            dao.deleteCollection("one")
            assertEquals("two", dao.userCollections().first().single().id)
            assertEquals("two", dao.collectionWallpapers().first().single().collectionId)
            assertNotNull(dao.favorite("abc123"))
        }
    }

    @Test
    fun creatingCollectionAndFirstWallpaperIsAtomic() = runBlocking {
        Room.inMemoryDatabaseBuilder(context, LocalDatabase::class.java).build().withDatabase { db ->
            val result = runCatching {
                db.dao().createCollectionWithWallpaper(UserCollection("one", "First", "first"),
                    CollectionWallpaper("missing-parent", "abc123", "{}"))
            }
            assertTrue(result.isFailure)
            assertTrue(db.dao().userCollections().first().isEmpty())
            assertTrue(db.dao().collectionWallpapers().first().isEmpty())
        }
    }
}
