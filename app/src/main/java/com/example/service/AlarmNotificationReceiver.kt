package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receptor de difusión para silenciar el audio de la alarma directamente desde la acción de la notificación.
 */
class AlarmNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == SecurityNotificationManager.ACTION_SILENCE) {
            SecurityNotificationManager.stopActiveAlarmAudio()
        }
    }
}
