package com.hive.agents.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class VaultKeys(
    val nimApiKey: String = "",
    val agentMailApiKey: String = "",
    val agentMailInbox: String = "",
    val stripeSecretKey: String = "",
    val githubToken: String = "",
    val storageUrl: String = "",
    val storageKey: String = ""
) {
    val hasNim: Boolean get() = nimApiKey.isNotBlank()
    val hasMail: Boolean get() = agentMailApiKey.isNotBlank()
    val hasStripe: Boolean get() = stripeSecretKey.isNotBlank()
    val ready: Boolean get() = hasNim
}

/**
 * Stores API keys encrypted with a hardware-backed Android Keystore key.
 *
 * Deliberately its own file (`hive-secrets.json`) with its own atomic write, so a damaged
 * or half-written state file can never blank the setup. Keys live outside the APK, so an
 * in-place update keeps them as long as the signing key is unchanged.
 */
class KeyVault(context: Context) {

    private val file = File(context.filesDir, "hive-secrets.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun load(): VaultKeys = runCatching {
        if (!file.exists()) return VaultKeys()
        json.decodeFromString<VaultKeys>(String(decrypt(file.readBytes()), Charsets.UTF_8))
    }.getOrElse { VaultKeys() }

    @Synchronized
    fun save(keys: VaultKeys) {
        val payload = json.encodeToString(keys).toByteArray(Charsets.UTF_8)
        val encrypted = encrypt(payload)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(encrypted)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
    }

    /** Diagnostics for the settings screen: does the device keystore hold a usable key? */
    fun vaultHealthy(): Boolean = runCatching { secretKey() != null }.getOrDefault(false)

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getEntry(ALIAS, null)
        if (existing is KeyStore.SecretKeyEntry) return existing.secretKey
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return Base64.encodeToString(iv, Base64.NO_WRAP).toByteArray() + NEWLINE + body
    }

    private fun decrypt(stored: ByteArray): ByteArray {
        val split = stored.indexOfFirst { it == NEWLINE.toByte() }
        require(split > 0) { "vault payload is malformed" }
        val iv = Base64.decode(String(stored.copyOfRange(0, split), Charsets.UTF_8), Base64.NO_WRAP)
        val body = stored.copyOfRange(split + 1, stored.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(body)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "hive_vault_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val NEWLINE = '\n'.code.toByte()
    }
}
