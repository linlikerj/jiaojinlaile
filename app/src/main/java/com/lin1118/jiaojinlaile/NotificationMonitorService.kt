package com.lin1118.jiaojinlaile

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotificationMonitorService : NotificationListenerService() {

    private val TARGET_PACKAGE = "com.tmri.app.main" // 交管12123的包名

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!MonitoringService.isRunning.value) return

        val packageName = sbn.packageName
        Log.d("NotificationMonitor", "收到通知来自: $packageName")

        if (packageName == TARGET_PACKAGE) {
            Log.d("NotificationMonitor", "检测到交管12123通知，触发警报")
            MonitoringService.playAlarm(this)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // 通知被移除时的逻辑（如果需要）
    }
}
