package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "alarm_events",
    indices = [Index(value = ["eventId"], unique = true)]
)
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val eventType: String,
    val sensor: String? = null,
    val details: String,
    val alarmState: String,
    val sirenActive: Boolean,
    val strobeActive: Boolean,
    val isSimulated: Boolean = false,
    val priority: String = "MEDIA", // "CRITICA", "ALTA", "MEDIA", "INFO"
    val deliveryState: String = "ENTREGADO", // "ENTREGADO", "PENDIENTE_SYNC", "FALLO"
    val confidence: Float = 1.0f,
    val isAcknowledged: Boolean = true
)
