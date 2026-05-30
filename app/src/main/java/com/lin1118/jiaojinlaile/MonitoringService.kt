package com.lin1118.jiaojinlaile

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Telephony
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat

class MonitoringService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var smsReceiver: SmsReceiver? = null
    private val CHANNEL_ID = "MonitoringChannel"
    private val NOTIFICATION_ID = 1

    companion object {
        const val ACTION_START_ALARM = "com.lin1118.jiaojinlaile.START_ALARM"
        const val ACTION_STOP_ALARM = "com.lin1118.jiaojinlaile.STOP_ALARM"

        var isRunning = mutableStateOf(false)
            private set
        
        var isAlarming = mutableStateOf(false)
            private set

        fun playAlarm(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_START_ALARM
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlarm(context: Context) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initVibrator()
    }

    private fun initVibrator() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> startAlarmLogic()
            ACTION_STOP_ALARM -> stopAlarmLogic()
            else -> {
                // 默认启动监控
                isRunning.value = true
                showForegroundNotification("正在监控短信和通知...")
                registerSmsReceiver()
            }
        }
        return START_STICKY
    }

    private fun showForegroundNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("交警来了")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startAlarmLogic() {
        if (isAlarming.value) return
        isAlarming.value = true
        showForegroundNotification("！！！检测到交警提醒，正在警报！！！")

        try {
            // 1. 设置最大音量
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            // 2. 使用 MediaPlayer 播放警报并开启循环
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            // 3. 开启持续振动
            val pattern = longArrayOf(0, 1000, 500) // 振动1秒，停0.5秒
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 表示从索引0开始循环
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }

        } catch (e: Exception) {
            Log.e("MonitoringService", "无法启动警报", e)
        }
    }

    private fun stopAlarmLogic() {
        isAlarming.value = false
        showForegroundNotification("正在监控短信和通知...")
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        vibrator?.cancel()
    }

    private fun registerSmsReceiver() {
        if (smsReceiver == null) {
            smsReceiver = SmsReceiver()
            val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
            registerReceiver(smsReceiver, filter)
        }
    }

    override fun onDestroy() {
        isRunning.value = false
        stopAlarmLogic()
        smsReceiver?.let {
            unregisterReceiver(it)
            smsReceiver = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "监控服务频道",
                NotificationManager.IMPORTANCE_HIGH // 提高重要度以确保通知可见
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    inner class SmsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                for (message in messages) {
                    val body = message.displayMessageBody
                    if (body.contains("交警")) {
                        playAlarm(context)
                    }
                }
            }
        }
    }
}
