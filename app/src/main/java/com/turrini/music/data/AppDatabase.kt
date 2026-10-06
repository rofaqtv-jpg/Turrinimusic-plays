package com.turrini.music.data

import androidx.room.*

@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(primaryKeys = ["playlistId", "songId"])
data class PlaylistSong(
    val playlistId: Long,
    val songId: Long,
    val order: Int
)

@Entity
data class FavoriteSong(
    @PrimaryKey val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM Playlist")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<Playlist>>

    @Insert
    fun insert(playlist: Playlist): Long

    @Delete
    fun delete(playlist: Playlist)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM FavoriteSong")
    fun getAll(): kotlinx.coroutines.flow.Flow<List<FavoriteSong>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(song: FavoriteSong)

    @Delete
    fun delete(song: FavoriteSong)
}

@Database(
    entities = [Playlist::class, PlaylistSong::class, FavoriteSong::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteDao(): FavoriteDao
}
