package com.grabix.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "grabix_settings")

data class AppSettings(
    /** "internal" = Download/Grabix on phone storage, "sd" = app folder on SD card */
    val storage: String = "internal",
    /** Absolute folder chosen by the user for videos / music; null = default location. */
    val videoDir: String? = null,
    val audioDir: String? = null,
    /** Preferred video height; 0 = best available */
    val videoQuality: Int = 720,
    val audioFormat: String = "mp3",
    val audioBitrate: String = "320",
    val maxConcurrent: Int = 2,
    val wifiOnly: Boolean = false,
    val useAria2: Boolean = false,
    val embedSubs: Boolean = true,
    val subLangs: String = "en.*,en",
    val embedThumbnail: Boolean = true,
    /** Title / artist / description tags. The source link is always written. */
    val embedMetadata: Boolean = true,
    /** "system", "light" or "dark" */
    val theme: String = "system",
    /** Material You colours taken from the wallpaper (Android 12+). */
    val dynamicColor: Boolean = true,
    /** Start the next track automatically when one ends. */
    val autoPlayNext: Boolean = true,
    /** Google sign-in in the browser through microG (GmsCore), like ReVanced. */
    val microG: Boolean = false,
    /** Keep the download queue running after the app is swiped away from recents. */
    val downloadAfterClose: Boolean = false,
    val autoUpdateEngine: Boolean = true,
    val lastEngineUpdate: Long = 0,
    val backgroundPlay: Boolean = true,
    val autoPip: Boolean = true,
    val blockAds: Boolean = true,
    val vaultPinHash: String? = null,
    val vaultBiometric: Boolean = false,
    val searchHistory: List<String> = emptyList(),
)

class SettingsRepository(private val context: Context) {

    private object K {
        val storage = stringPreferencesKey("storage")
        val videoDir = stringPreferencesKey("video_dir")
        val audioDir = stringPreferencesKey("audio_dir")
        val videoQuality = intPreferencesKey("video_quality")
        val audioFormat = stringPreferencesKey("audio_format")
        val audioBitrate = stringPreferencesKey("audio_bitrate")
        val maxConcurrent = intPreferencesKey("max_concurrent")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val useAria2 = booleanPreferencesKey("use_aria2")
        val embedSubs = booleanPreferencesKey("embed_subs")
        val subLangs = stringPreferencesKey("sub_langs")
        val embedThumbnail = booleanPreferencesKey("embed_thumbnail")
        val embedMetadata = booleanPreferencesKey("embed_metadata")
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val autoPlayNext = booleanPreferencesKey("auto_play_next")
        val microG = booleanPreferencesKey("microg")
        val downloadAfterClose = booleanPreferencesKey("download_after_close")
        val autoUpdate = booleanPreferencesKey("auto_update_engine")
        val lastUpdate = longPreferencesKey("last_engine_update")
        val backgroundPlay = booleanPreferencesKey("background_play")
        val autoPip = booleanPreferencesKey("auto_pip")
        val blockAds = booleanPreferencesKey("block_ads")
        val vaultPin = stringPreferencesKey("vault_pin")
        val vaultBio = booleanPreferencesKey("vault_bio")
        val history = stringSetPreferencesKey("search_history")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun current(): AppSettings = flow.first()

    private fun Preferences.toSettings() = AppSettings(
        storage = this[K.storage] ?: "internal",
        videoDir = this[K.videoDir],
        audioDir = this[K.audioDir],
        videoQuality = this[K.videoQuality] ?: 720,
        audioFormat = this[K.audioFormat] ?: "mp3",
        audioBitrate = this[K.audioBitrate] ?: "320",
        maxConcurrent = this[K.maxConcurrent] ?: 2,
        wifiOnly = this[K.wifiOnly] ?: false,
        useAria2 = this[K.useAria2] ?: false,
        embedSubs = this[K.embedSubs] ?: true,
        subLangs = this[K.subLangs] ?: "en.*,en",
        embedThumbnail = this[K.embedThumbnail] ?: true,
        embedMetadata = this[K.embedMetadata] ?: true,
        theme = this[K.theme] ?: "system",
        dynamicColor = this[K.dynamicColor] ?: true,
        autoPlayNext = this[K.autoPlayNext] ?: true,
        microG = this[K.microG] ?: false,
        downloadAfterClose = this[K.downloadAfterClose] ?: false,
        autoUpdateEngine = this[K.autoUpdate] ?: true,
        lastEngineUpdate = this[K.lastUpdate] ?: 0L,
        backgroundPlay = this[K.backgroundPlay] ?: true,
        autoPip = this[K.autoPip] ?: true,
        blockAds = this[K.blockAds] ?: true,
        vaultPinHash = this[K.vaultPin],
        vaultBiometric = this[K.vaultBio] ?: false,
        // Stored as "timestamp|query" so the newest entries sort first.
        searchHistory = (this[K.history] ?: emptySet())
            .sortedDescending()
            .map { it.substringAfter('|') },
    )

    suspend fun setStorage(v: String) = context.dataStore.edit { it[K.storage] = v }
    suspend fun setVideoDir(v: String?) = context.dataStore.edit { if (v == null) it.remove(K.videoDir) else it[K.videoDir] = v }
    suspend fun setAudioDir(v: String?) = context.dataStore.edit { if (v == null) it.remove(K.audioDir) else it[K.audioDir] = v }
    suspend fun setVideoQuality(v: Int) = context.dataStore.edit { it[K.videoQuality] = v }
    suspend fun setAudioFormat(v: String) = context.dataStore.edit { it[K.audioFormat] = v }
    suspend fun setAudioBitrate(v: String) = context.dataStore.edit { it[K.audioBitrate] = v }
    suspend fun setMaxConcurrent(v: Int) = context.dataStore.edit { it[K.maxConcurrent] = v.coerceIn(1, 5) }
    suspend fun setWifiOnly(v: Boolean) = context.dataStore.edit { it[K.wifiOnly] = v }
    suspend fun setUseAria2(v: Boolean) = context.dataStore.edit { it[K.useAria2] = v }
    suspend fun setEmbedSubs(v: Boolean) = context.dataStore.edit { it[K.embedSubs] = v }
    suspend fun setSubLangs(v: String) = context.dataStore.edit { it[K.subLangs] = v }
    suspend fun setEmbedThumbnail(v: Boolean) = context.dataStore.edit { it[K.embedThumbnail] = v }
    suspend fun setEmbedMetadata(v: Boolean) = context.dataStore.edit { it[K.embedMetadata] = v }
    suspend fun setTheme(v: String) = context.dataStore.edit { it[K.theme] = v }
    suspend fun setDynamicColor(v: Boolean) = context.dataStore.edit { it[K.dynamicColor] = v }
    suspend fun setAutoPlayNext(v: Boolean) = context.dataStore.edit { it[K.autoPlayNext] = v }
    suspend fun setMicroG(v: Boolean) = context.dataStore.edit { it[K.microG] = v }
    suspend fun setDownloadAfterClose(v: Boolean) = context.dataStore.edit { it[K.downloadAfterClose] = v }
    suspend fun setAutoUpdate(v: Boolean) = context.dataStore.edit { it[K.autoUpdate] = v }
    suspend fun setLastUpdate(v: Long) = context.dataStore.edit { it[K.lastUpdate] = v }
    suspend fun setBackgroundPlay(v: Boolean) = context.dataStore.edit { it[K.backgroundPlay] = v }
    suspend fun setAutoPip(v: Boolean) = context.dataStore.edit { it[K.autoPip] = v }
    suspend fun setBlockAds(v: Boolean) = context.dataStore.edit { it[K.blockAds] = v }
    suspend fun setVaultPin(hash: String?) = context.dataStore.edit {
        if (hash == null) it.remove(K.vaultPin) else it[K.vaultPin] = hash
    }
    suspend fun setVaultBiometric(v: Boolean) = context.dataStore.edit { it[K.vaultBio] = v }

    suspend fun addSearch(query: String) = context.dataStore.edit { p ->
        val q = query.trim()
        if (q.isEmpty()) return@edit
        val existing = (p[K.history] ?: emptySet()).filterNot { it.substringAfter('|') == q }
        val updated = (existing + "${System.currentTimeMillis()}|$q").sortedDescending().take(15)
        p[K.history] = updated.toSet()
    }

    suspend fun clearSearchHistory() = context.dataStore.edit { it.remove(K.history) }

    // ---------- backup / restore (plain key=value text) ----------

    private val boolKeys = listOf(
        K.wifiOnly, K.useAria2, K.embedSubs, K.embedThumbnail, K.embedMetadata, K.autoUpdate,
        K.backgroundPlay, K.autoPip, K.blockAds, K.dynamicColor, K.autoPlayNext, K.microG, K.downloadAfterClose,
    )
    private val stringKeys = listOf(
        K.storage, K.videoDir, K.audioDir, K.audioFormat, K.audioBitrate, K.subLangs, K.theme,
    )
    private val intKeys = listOf(K.videoQuality, K.maxConcurrent)

    /** Every user-facing setting as key=value pairs. The private-folder PIN is never exported. */
    suspend fun exportPairs(): List<Pair<String, String>> {
        val p = context.dataStore.data.first()
        val out = ArrayList<Pair<String, String>>()
        stringKeys.forEach { k -> p[k]?.let { out += k.name to it } }
        intKeys.forEach { k -> p[k]?.let { out += k.name to it.toString() } }
        boolKeys.forEach { k -> p[k]?.let { out += k.name to it.toString() } }
        (p[K.history] ?: emptySet()).sortedDescending().forEach { out += "search" to it.substringAfter('|') }
        return out
    }

    /** Applies pairs written by [exportPairs]. Unknown keys are ignored. Returns how many were applied. */
    suspend fun importPairs(pairs: List<Pair<String, String>>): Int {
        var applied = 0
        context.dataStore.edit { p ->
            val searches = ArrayList<String>()
            for ((key, value) in pairs) {
                val sk = stringKeys.firstOrNull { it.name == key }
                val ik = intKeys.firstOrNull { it.name == key }
                val bk = boolKeys.firstOrNull { it.name == key }
                when {
                    key == "search" -> searches += value
                    sk != null -> { p[sk] = value.trim(); applied++ }
                    ik != null -> value.toIntOrNull()?.let {
                        p[ik] = if (ik == K.maxConcurrent) it.coerceIn(1, 5) else it.coerceAtLeast(0)
                        applied++
                    }
                    bk != null -> value.toBooleanStrictOrNull()?.let { p[bk] = it; applied++ }
                }
            }
            if (searches.isNotEmpty()) {
                val now = System.currentTimeMillis()
                // Keep the original order: first line = newest.
                p[K.history] = searches.take(15).mapIndexed { i, q -> "${now - i}|$q" }.toSet()
                applied++
            }
        }
        return applied
    }
}
