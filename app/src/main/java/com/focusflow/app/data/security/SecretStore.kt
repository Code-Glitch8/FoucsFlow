package com.focusflow.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where API keys are kept. An interface so tests (and the AI chain) don't need Android. */
interface SecretStore {
    fun get(name: String): String?
    fun put(name: String, value: String): Boolean
    fun remove(name: String)
}

/**
 * API keys are encrypted with an AES-GCM key held in the Android Keystore (the key material never
 * leaves secure hardware/OS storage) and the ciphertext is kept in app-private preferences. Keys are
 * excluded from backups, are never logged, and if the Keystore is unavailable the key is simply not
 * saved (put returns false) rather than being stored in plain text.
 */
class KeystoreSecretStore(context: Context) : SecretStore {
    private val prefs = context.applicationContext.getSharedPreferences("focusflow_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    } catch (e: Exception) {
        null
    }

    override fun put(name: String, value: String): Boolean {
        val k = key() ?: return false
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, k) }
            val out = c.iv + c.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(name, Base64.encodeToString(out, Base64.NO_WRAP)).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun get(name: String): String? {
        val k = key() ?: return null
        val raw = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(raw, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }
            String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    override fun remove(name: String) { prefs.edit().remove(name).apply() }

    private companion object { const val ALIAS = "focusflow_api_keys" }
}
