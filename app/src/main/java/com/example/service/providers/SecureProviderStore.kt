package com.example.service.providers

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Provider credentials are encrypted at rest by a non-exportable Android Keystore key. */
class SecureProviderStore(context: Context) {
    private val prefs = context.getSharedPreferences("secure_ai_providers", Context.MODE_PRIVATE)
    private val keyAlias = "tnote_provider_credentials_v1"

    fun put(name: String, value: String) {
        if (value.isBlank()) return remove(name)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun get(name: String): String? = runCatching {
        val payload = Base64.decode(prefs.getString(name, null), Base64.NO_WRAP)
        if (payload.size <= 12) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8)
    }.getOrNull()

    fun remove(name: String) { prefs.edit().remove(name).apply() }
    fun has(name: String) = !get(name).isNullOrBlank()

    fun mode(kind: String): ProcessingMode = runCatching {
        ProcessingMode.valueOf(prefs.getString("mode_$kind", ProcessingMode.AUTOMATIC.name)!!)
    }.getOrDefault(ProcessingMode.AUTOMATIC)

    fun setMode(kind: String, mode: ProcessingMode) { prefs.edit().putString("mode_$kind", mode.name).apply() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build())
            generateKey()
        }
    }

    companion object {
        const val GEMINI_KEY = "gemini_key"
        const val GROQ_KEY = "groq_key"
        const val GOOGLE_VISION_KEY = "google_vision_key"
        const val AZURE_VISION_KEY = "azure_vision_key"
        const val AZURE_VISION_ENDPOINT = "azure_vision_endpoint"
        const val OCR_SPACE_KEY = "ocr_space_key"
    }
}
