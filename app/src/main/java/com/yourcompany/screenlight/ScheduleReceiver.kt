package com.yourcompany.screenlight

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            if (action == ACTION_START_FILTER) {
                // 开启滤镜
                val prefs = context.dataStore.data.first()
                val isScheduleEnabled = prefs[AppSettings.SCHEDULE_ENABLED_KEY] ?: false
                
                if (isScheduleEnabled) {
                    // Start service
                    val startIntent = Intent(context, ScreenFilterService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(startIntent)
                    } else {
                        context.startService(startIntent)
                    }
                    
                    // Update state
                    context.dataStore.edit { it[AppSettings.IS_RUNNING_KEY] = true }
                    
                    // Reschedule for next day
                    scheduleNextAlarm(context, true)
                }
            } else if (action == ACTION_STOP_FILTER) {
                // 关闭滤镜
                val prefs = context.dataStore.data.first()
                val isScheduleEnabled = prefs[AppSettings.SCHEDULE_ENABLED_KEY] ?: false
                
                if (isScheduleEnabled) {
                    // Stop service
                    val stopIntent = Intent(context, ScreenFilterService::class.java).apply {
                        this.action = ScreenFilterService.ACTION_STOP
                    }
                    context.startService(stopIntent)
                    
                    // Update state
                    context.dataStore.edit { it[AppSettings.IS_RUNNING_KEY] = false }
                    
                    // Reschedule for next day
                    scheduleNextAlarm(context, false)
                }
            }
        }
    }

    companion object {
        const val ACTION_START_FILTER = "com.yourcompany.screenlight.ACTION_START_FILTER"
        const val ACTION_STOP_FILTER = "com.yourcompany.screenlight.ACTION_STOP_FILTER"

        fun scheduleNextAlarm(context: Context, isStart: Boolean) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                val prefs = context.dataStore.data.first()
                val isEnabled = prefs[AppSettings.SCHEDULE_ENABLED_KEY] ?: false
                if (!isEnabled) return@launch

                val hourKey = if (isStart) AppSettings.START_HOUR_KEY else AppSettings.END_HOUR_KEY
                val minuteKey = if (isStart) AppSettings.START_MINUTE_KEY else AppSettings.END_MINUTE_KEY
                
                val hour = prefs[hourKey] ?: if (isStart) 22 else 7
                val minute = prefs[minuteKey] ?: 0

                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                // If time has passed today, schedule for tomorrow
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                }

                val intent = Intent(context, ScheduleReceiver::class.java).apply {
                    action = if (isStart) ACTION_START_FILTER else ACTION_STOP_FILTER
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    if (isStart) 1001 else 1002,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        } else {
                            // Fallback for no permission (should request it in UI)
                            alarmManager.setAndAllowWhileIdle(
                                AlarmManager.RTC_WAKEUP,
                                calendar.timeInMillis,
                                pendingIntent
                            )
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            calendar.timeInMillis,
                            pendingIntent
                        )
                    }
                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
        
        fun cancelAlarms(context: Context) {
             val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
             
             // Cancel Start Alarm
             val startIntent = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_START_FILTER
             }
             val startPendingIntent = PendingIntent.getBroadcast(
                context, 1001, startIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )
             alarmManager.cancel(startPendingIntent)
             
             // Cancel Stop Alarm
             val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
                action = ACTION_STOP_FILTER
             }
             val stopPendingIntent = PendingIntent.getBroadcast(
                context, 1002, stopIntent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
             )
             alarmManager.cancel(stopPendingIntent)
        }
    }
}