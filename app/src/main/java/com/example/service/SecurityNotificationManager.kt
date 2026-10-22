package com.example.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gestor robusto de Notificaciones Persistentes de Seguridad y Audio de Alarma.
 * - Notificaciones persistentes: no se borran solas al abrirlas ni al desarmar la alarma;
 *   permanecen en la bandeja hasta que el usuario decida eliminarlas manualmente.
 * - Audio controlado y exclusivo: evita solapamientos y se detiene de forma instantánea al
 *   desactivar o silenciar la alarma.
 */
class SecurityNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "security_alarm_alerts_v3"
        const val CHANNEL_NAME = "Alertas de Seguridad Perimetral"
        const val ACTION_SILENCE = "com.example.action.SILENCE_ALARM"
        val VIBRATION_PATTERN = longArrayOf(0, 500, 200, 500, 200, 800)

        @Volatile
        private var instance: SecurityNotificationManager? = null

        fun getInstance(context: Context): SecurityNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: SecurityNotificationManager(context.applicationContext).also { instance = it }
            }
        }

        fun stopActiveAlarmAudio() {
            instance?.stopAlarmAudio()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var activeRingtone: Ringtone? = null
    private var audioTimeoutJob: Job? = null

    private val notificationManager: NotificationManager? = try {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    } catch (_: Exception) {
        null
    }

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    init {
        instance = this
        createNotificationChannel()
    }

    private fun getAlarmSoundUri(): Uri? {
        return try {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        } catch (_: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas urgentes de seguridad perimetral con detalle del evento"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    // Configuramos el canal sin sonido directo del sistema para evitar que el sistema
                    // y nuestro reproductor controlado suenen a la vez generando eco o solapamiento.
                    setSound(null, null)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                notificationManager?.createNotificationChannel(channel)
            } catch (_: Exception) {}
        }
    }

    /**
     * Emite la alerta de seguridad completa:
     * 1. Detiene cualquier audio previo para evitar solapamientos.
     * 2. Inicia vibración de emergencia.
     * 3. Inicia sonido de alarma controlado con auto-corte de seguridad.
     * 4. Publica la notificación PERSISTENTE en el sistema Android (permanece hasta que el usuario la descarte).
     */
    fun dispatchSecurityAlert(title: String, details: String, isTest: Boolean = false) {
        triggerVibration()
        playAlarmAudio()
        postPersistentNotification(title, details, isTest)
    }

    /**
     * Detiene de inmediato el audio de la alarma y la vibración, sin eliminar la notificación.
     */
    fun stopAlarmAudio() {
        try {
            audioTimeoutJob?.cancel()
            audioTimeoutJob = null

            activeRingtone?.let { r ->
                if (r.isPlaying) {
                    r.stop()
                }
            }
            activeRingtone = null
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    /**
     * Elimina una notificación específica por ID o todas si se requiere.
     */
    fun cancelNotification(notificationId: Int) {
        try {
            notificationManager?.cancel(notificationId)
        } catch (_: Exception) {}
    }

    fun cancelAllNotifications() {
        try {
            notificationManager?.cancelAll()
        } catch (_: Exception) {}
    }

    private fun triggerVibration() {
        try {
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, -1))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(VIBRATION_PATTERN, -1)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun playAlarmAudio() {
        try {
            // Detener cualquier sonido previo de forma estricta para evitar solapamiento
            stopAlarmAudio()

            val soundUri = getAlarmSoundUri() ?: return
            val ringtone = RingtoneManager.getRingtone(context, soundUri) ?: return
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            ringtone.audioAttributes = audioAttributes
            activeRingtone = ringtone
            ringtone.play()

            // Temporizador de seguridad: detener audio tras 20 segundos para no bloquear el altavoz
            // indefinidamente si el usuario no tiene el teléfono a mano, mientras la notificación permanece.
            audioTimeoutJob = scope.launch {
                delay(20000)
                stopAlarmAudio()
            }
        } catch (_: Exception) {}
    }

    private fun postPersistentNotification(title: String, details: String, isTest: Boolean) {
        try {
            // Verificar permiso en Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Acción para silenciar el sonido de la alarma directamente desde la notificación
            val silenceIntent = Intent(context, AlarmNotificationReceiver::class.java).apply {
                action = ACTION_SILENCE
            }
            val silencePendingIntent = PendingIntent.getBroadcast(
                context,
                101,
                silenceIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Identificador único para que múltiples eventos no se sobreescriban entre sí
            val notificationId = ((System.currentTimeMillis() / 1000) % 100000).toInt() + 1000

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(details)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(title)
                        .bigText(details)
                        .setSummaryText(if (isTest) "Prueba de Alarma" else "¡INTRUSIÓN PERIMETRAL!")
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                // Persistente: NO se auto-cancela al pulsarla; solo se descarta si el usuario la desliza
                .setAutoCancel(false)
                .setOngoing(false)
                .setContentIntent(pendingIntent)
                .addAction(
                    android.R.drawable.ic_lock_silent_mode_off,
                    "Silenciar Sonido",
                    silencePendingIntent
                )
                .setVibrate(VIBRATION_PATTERN)
                .setLights(Color.RED, 1000, 500)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

            notificationManager?.notify(notificationId, builder.build())
        } catch (_: Exception) {}
    }
}
