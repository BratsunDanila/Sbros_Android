package ru.sbros.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore("appearance")
data class Appearance(val palette: String = "lavender", val dark: Boolean = false, val welcomed: Boolean = false)
class SettingsRepository(val context: Context) {
    val aiTextConsent=context.settingsStore.data.map {it[booleanPreferencesKey("aiTextConsent")]?:false}
    suspend fun allowAiText(){context.settingsStore.edit {it[booleanPreferencesKey("aiTextConsent")]=true}}
    val mode = context.settingsStore.data.map {it[stringPreferencesKey("viewMode")]?:"local"}
    suspend fun mode(value: String){require(value in listOf("local","account","demo","legacy"));context.settingsStore.edit {p->val old=p[stringPreferencesKey("viewMode")]?:"local";if(value in listOf("demo","legacy")&&old !in listOf("demo","legacy"))p[stringPreferencesKey("returnMode")]=old;p[stringPreferencesKey("viewMode")]=value}}
    suspend fun leaveExamples(){context.settingsStore.edit {p->p[stringPreferencesKey("viewMode")]=p[stringPreferencesKey("returnMode")]?:"local"}}
    val pendingImport = context.settingsStore.data.map {it[stringPreferencesKey("pendingImport")]?:""}
    suspend fun pendingImport(value: String){context.settingsStore.edit {it[stringPreferencesKey("pendingImport")]=value}}
    val appearance = context.settingsStore.data.map { p -> Appearance(p[stringPreferencesKey("palette")]?:"lavender",p[booleanPreferencesKey("dark")]?:false,p[booleanPreferencesKey("welcomed")]?:false) }
    suspend fun update(value: Appearance) { context.settingsStore.edit { p ->
        p[stringPreferencesKey("palette")] = value.palette; p[booleanPreferencesKey("dark")] = value.dark; p[booleanPreferencesKey("welcomed")] = value.welcomed
    } }
}
