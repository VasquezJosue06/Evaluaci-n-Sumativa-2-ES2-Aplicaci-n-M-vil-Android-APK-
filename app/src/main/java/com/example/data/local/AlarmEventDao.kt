package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: AlarmEventEntity): Long

    @Query("SELECT * FROM alarm_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE isSimulated = 0 ORDER BY timestamp DESC")
    fun getRealEvents(): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE isSimulated = 1 ORDER BY timestamp DESC")
    fun getSimulatedEvents(): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events WHERE deliveryState = 'PENDIENTE_SYNC' ORDER BY timestamp ASC")
    suspend fun getPendingSyncEvents(): List<AlarmEventEntity>

    @Query("UPDATE alarm_events SET deliveryState = 'ENTREGADO', isAcknowledged = 1 WHERE eventId = :eventId")
    suspend fun markEventAsSynced(eventId: String)

    @Query("SELECT * FROM alarm_events WHERE eventId = :eventId LIMIT 1")
    suspend fun findEventByEventId(eventId: String): AlarmEventEntity?

    @Query("DELETE FROM alarm_events")
    suspend fun clearAllEvents()

    @Query("SELECT COUNT(*) FROM alarm_events WHERE eventType LIKE '%INTRUSION%'")
    fun getIntrusionCount(): Flow<Int>
}
