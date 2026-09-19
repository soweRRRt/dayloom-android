package com.sowerrrt.dayloom.core.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreVaultCipher : VaultCipher {
    override suspend fun ensureKey(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val keyStore = loadKeyStore()
                if (!keyStore.containsAlias(KEY_ALIAS)) generateKey()
            }
        }

    override suspend fun encrypt(plaintext: ByteArray): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(plaintext.isNotEmpty()) { "Vault payload cannot be empty" }
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getKey())
                cipher.updateAAD(ASSOCIATED_DATA)
                val encrypted = cipher.doFinal(plaintext)
                val initializationVector = cipher.iv
                require(initializationVector.size in 1..MAX_IV_SIZE) { "Unexpected initialization vector size" }
                ByteBuffer
                    .allocate(HEADER_SIZE + initializationVector.size + encrypted.size)
                    .put(FORMAT_VERSION)
                    .put(initializationVector.size.toByte())
                    .put(initializationVector)
                    .put(encrypted)
                    .array()
            }
        }

    override suspend fun decrypt(ciphertext: ByteArray): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(ciphertext.size > HEADER_SIZE + GCM_TAG_SIZE_BYTES) { "Vault ciphertext is truncated" }
                val buffer = ByteBuffer.wrap(ciphertext)
                require(buffer.get() == FORMAT_VERSION) { "Unsupported vault cipher format" }
                val initializationVectorSize = buffer.get().toInt() and 0xFF
                require(initializationVectorSize in 1..MAX_IV_SIZE) { "Invalid initialization vector size" }
                require(buffer.remaining() > initializationVectorSize + GCM_TAG_SIZE_BYTES) {
                    "Vault ciphertext is truncated"
                }
                val initializationVector = ByteArray(initializationVectorSize)
                buffer.get(initializationVector)
                val encrypted = ByteArray(buffer.remaining())
                buffer.get(encrypted)

                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_SIZE_BITS, initializationVector))
                cipher.updateAAD(ASSOCIATED_DATA)
                cipher.doFinal(encrypted)
            }
        }

    override suspend fun destroyKey(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val keyStore = loadKeyStore()
                if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            }
        }

    private fun getKey(): SecretKey {
        val keyStore = loadKeyStore()
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: error("Vault key is unavailable")
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun generateKey() {
        val builder =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setKeySize(KEY_SIZE_BITS)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                AUTHENTICATION_WINDOW_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_WINDOW_SECONDS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) builder.setUnlockedDeviceRequired(true)

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(builder.build())
        generator.generateKey()
    }

    companion object {
        const val AUTHENTICATION_WINDOW_SECONDS = 300
        private const val KEY_ALIAS = "dayloom_vault_aes_v1"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_TAG_SIZE_BITS = 128
        private const val GCM_TAG_SIZE_BYTES = GCM_TAG_SIZE_BITS / 8
        private const val MAX_IV_SIZE = 32
        private const val HEADER_SIZE = 2
        private const val FORMAT_VERSION: Byte = 1
        private val ASSOCIATED_DATA = "com.sowerrrt.dayloom:vault:v1".toByteArray(Charsets.UTF_8)
    }
}
