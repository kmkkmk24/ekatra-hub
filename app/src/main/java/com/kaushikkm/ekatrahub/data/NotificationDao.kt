package com.kaushikkm.ekatrahub.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO = "Data Access Object": the list of database operations the app is allowed to do.
 * Room generates the real SQL code for these at build time.
 */
@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    /**
     * The timeline. Returns a PagingSource, so the list loads ~50 rows at a time as you
     * scroll instead of the whole history at once - this is key to smooth scrolling.
     * Room automatically re-runs it when new notifications are inserted.
     */
    @Query(
        """
        SELECT * FROM notifications
        WHERE (:packageName IS NULL OR packageName = :packageName)
          AND (:query = ''
               OR title LIKE '%' || :query || '%'
               OR text LIKE '%' || :query || '%'
               OR appLabel LIKE '%' || :query || '%')
        ORDER BY postedAt DESC
        """,
    )
    fun timeline(packageName: String?, query: String): PagingSource<Int, NotificationEntity>

    @Query(
        """
        SELECT packageName, MAX(appLabel) AS appLabel, COUNT(*) AS count,
               SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) AS unread
        FROM notifications
        GROUP BY packageName
        ORDER BY MAX(postedAt) DESC
        """,
    )
    fun observeApps(): Flow<List<AppCount>>

    /** Drives the LED: it blinks while this is above zero. */
    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Query("UPDATE notifications SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    /** The source app cleared its notification (you read it there): mark it read here too. */
    @Query("UPDATE notifications SET isRead = 1 WHERE sbnKey = :key AND isRead = 0")
    suspend fun markReadByKey(key: String)

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND isRead = 0")
    suspend fun unreadForApp(packageName: String): List<NotificationEntity>

    @Query("UPDATE notifications SET isRead = 1 WHERE packageName = :packageName AND isRead = 0")
    suspend fun markAppRead(packageName: String)

    @Query("SELECT DISTINCT sbnKey FROM notifications WHERE packageName = :packageName")
    suspend fun keysForApp(packageName: String): List<String>

    @Query("SELECT * FROM notifications WHERE isRead = 0")
    suspend fun allUnread(): List<NotificationEntity>

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("SELECT DISTINCT sbnKey FROM notifications")
    suspend fun allKeys(): List<String>

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notifications WHERE packageName = :packageName")
    suspend fun clearApp(packageName: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
