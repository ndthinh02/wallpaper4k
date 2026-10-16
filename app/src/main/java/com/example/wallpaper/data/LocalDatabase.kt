package com.example.wallpaper.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "user_collections", indices = [Index(value = ["nameKey"], unique = true)])
data class UserCollection(
    @PrimaryKey val id: String,
    val name: String,
    val nameKey: String,
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_wallpapers",
    primaryKeys = ["collectionId", "wallpaperId"],
    foreignKeys = [ForeignKey(entity = UserCollection::class, parentColumns = ["id"],
        childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("collectionId")]
)
data class CollectionWallpaper(
    val collectionId: String, val wallpaperId: String, val metadata: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class Favorite(@PrimaryKey val id: String, val metadata: String, val savedAt: Long = System.currentTimeMillis())

@Entity(tableName = "downloads")
data class Download(
    @PrimaryKey val id: String, val metadata: String, val uri: String,
    val status: String = "complete", val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class SearchHistory(@PrimaryKey val query: String, val searchedAt: Long = System.currentTimeMillis())

@Entity(tableName = "activity")
data class ActivityEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val type: String, val wallpaperId: String, val metadata: String,
    val time: Long = System.currentTimeMillis(), val read: Boolean = false
)

@Entity(tableName = "covers")
data class CollectionCover(
    @PrimaryKey val query: String, val thumbnail: String, val total: Int,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface LocalDao {
    @Query("SELECT * FROM user_collections ORDER BY createdAt DESC")
    fun userCollections(): Flow<List<UserCollection>>
    @Query("SELECT * FROM collection_wallpapers ORDER BY addedAt DESC")
    fun collectionWallpapers(): Flow<List<CollectionWallpaper>>
    @Query("SELECT * FROM user_collections WHERE nameKey = :key LIMIT 1")
    suspend fun collectionByName(key: String): UserCollection?
    @Insert
    suspend fun createCollection(item: UserCollection)
    @Query("UPDATE user_collections SET name = :name, nameKey = :key, color = :color WHERE id = :id")
    suspend fun updateCollection(id: String, name: String, key: String, color: Int)
    @Query("DELETE FROM user_collections WHERE id = :id")
    suspend fun deleteCollection(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCollectionWallpaper(item: CollectionWallpaper)
    @Query("DELETE FROM collection_wallpapers WHERE collectionId = :collectionId AND wallpaperId = :wallpaperId")
    suspend fun removeCollectionWallpaper(collectionId: String, wallpaperId: String)
    @Transaction
    suspend fun createCollectionWithWallpaper(item: UserCollection, wallpaper: CollectionWallpaper?) {
        createCollection(item)
        if (wallpaper != null) addCollectionWallpaper(wallpaper)
    }
    @Query("SELECT * FROM favorites ORDER BY savedAt DESC")
    fun favorites(): Flow<List<Favorite>>
    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun favorite(id: String): Favorite?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFavorite(item: Favorite)
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun removeFavorite(id: String)
    @Query("SELECT * FROM downloads ORDER BY savedAt DESC")
    fun downloads(): Flow<List<Download>>
    @Query("SELECT * FROM downloads ORDER BY savedAt DESC")
    suspend fun downloadList(): List<Download>
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun download(id: String): Download?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownload(item: Download)
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun removeDownload(id: String)
    @Query("SELECT * FROM history ORDER BY searchedAt DESC LIMIT 20")
    fun history(): Flow<List<SearchHistory>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHistory(item: SearchHistory)
    @Query("DELETE FROM history WHERE query = :query")
    suspend fun removeHistory(query: String)
    @Query("SELECT * FROM activity ORDER BY time DESC")
    fun events(): Flow<List<ActivityEvent>>
    @Insert
    suspend fun event(item: ActivityEvent): Long
    @Query("DELETE FROM activity WHERE eventId = :id")
    suspend fun removeEvent(id: Long)
    @Query("UPDATE activity SET read = 1 WHERE eventId = :id")
    suspend fun markRead(id: Long)
    @Query("SELECT * FROM covers")
    fun covers(): Flow<List<CollectionCover>>
    @Query("SELECT * FROM covers WHERE query = :query")
    suspend fun cover(query: String): CollectionCover?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCover(item: CollectionCover)
}

@Database(
    entities = [Favorite::class, Download::class, SearchHistory::class, ActivityEvent::class, CollectionCover::class,
        UserCollection::class, CollectionWallpaper::class],
    version = 2,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun dao(): LocalDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS user_collections (id TEXT NOT NULL, name TEXT NOT NULL, nameKey TEXT NOT NULL, color INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_collections_nameKey ON user_collections(nameKey)")
                db.execSQL("CREATE TABLE IF NOT EXISTS collection_wallpapers (collectionId TEXT NOT NULL, wallpaperId TEXT NOT NULL, metadata TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(collectionId, wallpaperId), FOREIGN KEY(collectionId) REFERENCES user_collections(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_wallpapers_collectionId ON collection_wallpapers(collectionId)")
            }
        }
    }
}
