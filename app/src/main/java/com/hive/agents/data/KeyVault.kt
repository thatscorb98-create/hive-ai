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
    val storageKey: String = "",
    /** Optional Google Programmable Search, so research can use real Google results. */
    val googleSearchKey: String = "",
    val googleSearchCx: String = ""
) {
    val hasNim: Boolean get() = nimApiKey.isNotBlank()
    val hasGoogle: Boolean get() = googleSearchKey.isNotBlank() && googleSearchCx.isNotBlank()
    val hasMail: Boolean get() = agentMailApiKey.isNotBlank()
    val hasStripe: Boolean get() = stripeSecretKey.isNotBlank()
    val ready: Boolean get() = hasNim
}

/**
 * Stores API keys encrypted with a hardware-backed Android Keystore key.
 *
 * Deliberately its own file (`hive-secrets.json`) with its own atomic write and a backup copy,
 * so a damaged or half-written state file can never blank the setup. Keys live outside the APK,
 * so an in-place update keeps them as long as the signing key is unchanged.
 *
 * A decryption failure is never reported as "no keys configured": [lastError] carries the real
 * problem to Settings so the owner can re-enter keys instead of unknowingly running keyless.
 */
class KeyVault(context: Context) {

    private val file = File(context.filesDir, "hive-secrets.json")
    private val backup = File(context.filesDir, "hive-secrets.bak")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Set when the vault exists but cannot be read, or when a save fails. */
    @Volatile
    var lastError: String? = null
        private set

    @Synchronized
    fun load(): VaultKeys {
        decode(file)?.let {
            lastError = null
            return it
        }
        decode(backup)?.let { restored ->
            runCatching { file.writeBytes(backup.readBytes()) }
            lastError = null
            return restored
        }
        if (file.exists() || backup.exists()) {
            lastError =
                "The encrypted vault could not be read on this device. Re-enter your keys to replace it."
        }
        return VaultKeys()
    }

    /** Missing fields fall back to their defaults, so an update never blanks older keys. */
    private fun decode(source: File): VaultKeys? = runCatching {
        if (!source.exists()) return null
        json.decodeFromString<VaultKeys>(String(decrypt(source.readBytes()), Charsets.UTF_8))
    }.getOrNull()

    @Synchronized
    fun save(keys: VaultKeys) {
        try {
            val payload = json.encodeToString(keys).toByteArray(Charsets.UTF_8)
            val encrypted = encrypt(payload)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeBytes(encrypted)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
            // A second copy survives a corrupted main file after an interrupted write.
            runCatching { backup.writeBytes(encrypted) }
            lastError = null
        } catch (error: Exception) {
            lastError = "Could not save keys: ${error.message ?: "unknown error"}"
            throw error
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        backup.delete()
        lastError = null
    }

    /** Diagnostics for the settings screen: can this device actually encrypt and decrypt? */
    fun vaultHealthy(): Boolean = runCatching {
        val probe = "hive-vault-probe".toByteArray(Charsets.UTF_8)
        decrypt(encrypt(probe)).contentEquals(probe)
    }.getOrDefault(false)

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
