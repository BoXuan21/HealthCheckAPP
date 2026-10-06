package com.healthcheck.buddy.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.healthcheck.buddy.domain.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Profile, the user's Claude API key (encrypted with the Android Keystore) and cached AI output. */
class SettingsStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val profile = stringPreferencesKey("profile")
        val apiKey = stringPreferencesKey("api_key_enc")
        val plan = stringPreferencesKey("plan_json")
        val checkIn = stringPreferencesKey("check_in")
        val checkInDay = stringPreferencesKey("check_in_day")
    }

    val profile: Flow<Profile?> = context.dataStore.data.map { prefs ->
        prefs[Keys.profile]?.let { runCatching { json.decodeFromString<Profile>(it) }.getOrNull() }
    }

    val apiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[Keys.apiKey]?.let { runCatching { KeyCrypto.decrypt(it) }.getOrNull() }
    }

    val planJson: Flow<String?> = context.dataStore.data.map { it[Keys.plan] }

    suspend fun saveProfile(p: Profile) {
        context.dataStore.edit { it[Keys.profile] = json.encodeToString(p) }
    }

    suspend fun saveApiKey(key: String?) {
        context.dataStore.edit {
            if (key.isNullOrBlank()) it.remove(Keys.apiKey) else it[Keys.apiKey] = KeyCrypto.encrypt(key.trim())
        }
    }

    suspend fun savePlan(planJson: String) {
        context.dataStore.edit { it[Keys.plan] = planJson }
    }

    suspend fun checkInFor(day: String): String? {
        val prefs = context.dataStore.data.first()
        return if (prefs[Keys.checkInDay] == day) prefs[Keys.checkIn] else null
    }

    suspend fun saveCheckIn(day: String, text: String) {
        context.dataStore.edit {
            it[Keys.checkInDay] = day
            it[Keys.checkIn] = text
        }
    }
}

private object KeyCrypto {
    private const val ALIAS = "healthcheck_api_key"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }
}
