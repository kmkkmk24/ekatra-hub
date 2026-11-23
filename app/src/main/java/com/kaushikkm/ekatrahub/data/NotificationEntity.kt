package com.kaushikkm.ekatrahub.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One captured notification = one row in the `notifications` table.
 *
 * The unique index stops duplicates: messaging apps often re-post the SAME notification
 * (e.g. when you open the chat, or a group summary updates). Same app + title + text +
 * original timestamp means it's the same notification, so the insert is ignored.
 * The other two indexes keep sorting by time and filtering by app fast with large histories.
 */
@Entity(
    tableName = "notifications",
    indices = [
        Index(value = ["packageName", "title", "text", "whenTime"], unique = true),
        Index(value = ["postedAt"]),
        Index(value = ["packageName"]),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String?,
    val category: String?,
    /** When the system posted it - used for ordering and display. */
    val postedAt: Long,
    /** The notification's own timestamp - stable across re-posts, used for de-duplication. */
    val whenTime: Long,
    /** System key for this notification; needed later for in-place reply / dismiss. */
    val sbnKey: String,
    /** Read in Ekatra (swiped right / opened) or in the app itself. */
    @ColumnInfo(defaultValue = "0") val isRead: Boolean = false,
    /**
     * Everything the notification carried, when that's more than [text]: a whole chat
     * conversation, inbox-style lines, summaries. Shown when you expand the notification.
     */
    val fullText: String? = null,
    /** Whether the app offered a Reply button (so we can explain when it's no longer usable). */
    @ColumnInfo(defaultValue = "0") val hadReply: Boolean = false,
)

/** Result row for the app rail: which apps have notifications, and how many are unread. */
data class AppCount(
    val packageName: String,
    val appLabel: String,
    val count: Int,
    val unread: Int,
)
