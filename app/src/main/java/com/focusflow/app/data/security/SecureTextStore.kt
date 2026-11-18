package com.focusflow.app.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-GCM encrypted preferences protected by a non-exportable Android Keystore key. */
class SecureTextStore(context: Context, preferencesName: String, private val keyAlias: String) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    fun get(name: String): String? = runCatching {
        val stored = preferences.getString(name, null) ?: return null
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun put(name: String, value: String?) {
        if (value == null) {
            preferences.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        preferences.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun keystoreKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

/** Shared-code AES-GCM encryption for peer-to-peer LAN sync packets. */
object PairingCrypto {
    fun encrypt(plainText: String, pairingCode: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(pairingCode))
        return cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(frame: ByteArray, pairingCode: String): String {
        require(frame.size > 12) { "Invalid encrypted frame" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(pairingCode),
            GCMParameterSpec(128, frame.copyOfRange(0, 12)),
        )
        return String(cipher.doFinal(frame.copyOfRange(12, frame.size)), Charsets.UTF_8)
    }

    private fun deriveKey(pairingCode: String): SecretKey {
        val secret = pairingCode.trim().toByteArray(Charsets.UTF_8)
        require(secret.size >= 10) { "The sync code must contain at least 10 characters" }
        return SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret), "AES")
    }
}
