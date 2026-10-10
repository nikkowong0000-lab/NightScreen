package com.yourcompany.screenlight

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class ScreenFilterTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var dataStoreJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        // 开始监听 DataStore 状态
        dataStoreJob = serviceScope.launch {
            dataStore.data
                .map { it[AppSettings.IS_RUNNING_KEY] ?: false }
                .collectLatest { isRunning ->
                    updateTileState(isRunning)
                }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        // 停止监听以节省资源
        dataStoreJob?.cancel()
    }

    override fun onClick() {
        super.onClick()
        val isCurrentlyActive = qsTile.state == Tile.STATE_ACTIVE

        if (isCurrentlyActive) {
            // 如果当前是开启状态，点击则关闭
            // 1. 停止服务
            val intent = Intent(this, ScreenFilterService::class.java).apply {
                action = ScreenFilterService.ACTION_STOP
            }
            startService(intent)
            
            // 2. 更新 DataStore 状态
            serviceScope.launch {
                dataStore.edit { prefs ->
                    prefs[AppSettings.IS_RUNNING_KEY] = false
                }
            }
            // Tile 状态会通过 onStartListening 中的监听器自动更新
        } else {
            // 如果当前是关闭状态，点击则开启
            if (Settings.canDrawOverlays(this)) {
                // 1. 启动服务
                val intent = Intent(this, ScreenFilterService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                
                // 2. 更新 DataStore 状态
                serviceScope.launch {
                    dataStore.edit { prefs ->
                        prefs[AppSettings.IS_RUNNING_KEY] = true
                    }
                }
            } else {
                // 无权限，跳转引导
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTileState(isRunning: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}