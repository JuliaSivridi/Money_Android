package com.stler.money.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stler.money.data.local.entity.ProcessedNotificationEntity

@Dao
interface ProcessedNotificationDao {

    /** Returns -1 when this notification was already recorded — atomic, so a live post and a
     * rescan racing on the same notification can't both get through. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ProcessedNotificationEntity): Long

    @Query("DELETE FROM processed_notifications WHERE processedAt < :olderThanMillis")
    suspend fun pruneOlderThan(olderThanMillis: Long)
}
