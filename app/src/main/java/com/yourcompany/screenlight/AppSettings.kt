package com.yourcompany.screenlight

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

// 1. 将 DataStore 实例移到顶层，方便全局访问
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

// 2. 创建一个单例对象来统一管理所有的键
object AppSettings {
    val ALPHA_KEY = floatPreferencesKey("alpha_value")
    val IS_RUNNING_KEY = booleanPreferencesKey("is_running")
    val IS_BLUE_LIGHT_KEY = booleanPreferencesKey("is_blue_light_enabled")
    val WARMTH_KEY = floatPreferencesKey("warmth_value")
    
    // Auto-Schedule keys
    val SCHEDULE_ENABLED_KEY = booleanPreferencesKey("schedule_enabled")
    val START_HOUR_KEY = intPreferencesKey("start_hour")
    val START_MINUTE_KEY = intPreferencesKey("start_minute")
    val END_HOUR_KEY = intPreferencesKey("end_hour")
    val END_MINUTE_KEY = intPreferencesKey("end_minute")

    val LANGUAGE_KEY = stringPreferencesKey("app_language")
}