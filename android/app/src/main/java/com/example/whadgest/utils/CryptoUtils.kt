package com.example.whadgest.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import android.util.Log

/**
 * Utility class for cryptographic operations
 * Handles secure passphrase generation and storage for database encryption
 */
object CryptoUtils {

    private const val TAG = "CryptoUtils"
    private const val ENCRYPTED_PREFS_NAME = "whadgest_secure_prefs"
    private const val PASSPHRASE_KEY = "database_passphrase"
    private const val KEYSTORE_ALIAS = "whadgest_master_key"
    private const val PASSPHRASE_LENGTH = 32 // 256-bit passphrase
    private const val GCM_TAG_LENGTH = 16

    /**
     * Get or create database passphrase
     * Uses Android Keystore for secure key management
     */
    fun getDatabasePassphrase(context: Context): CharArray {
        return try {
            val encryptedPrefs = getEncryptedSharedPreferences(context)

            // Try to get existing passphrase
            val existingPassphrase = encryptedPrefs.getString(PASSPHRASE_KEY, null)

            if (existingPassphrase != null) {
                Log.d(TAG, "Retrieved existing database passphrase")
                existingPassphrase.toCharArray()
            } else {
                // Generate new passphrase
                Log.i(TAG, "Generating new database passphrase")
                val newPassphrase = generateSecurePassphrase()

                // Store it securely
                encryptedPrefs.edit()
                    .putString(PASSPHRASE_KEY, String(newPassphrase))
                    .apply()

                newPassphrase
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting database passphrase", e)
            // Fallback to a basic passphrase (less secure)
            generateFallbackPassphrase(context)
        }
    }

    /**
     * Generate a cryptographically secure passphrase
     */
    private fun generateSecurePassphrase(): CharArray {
        val secureRandom = SecureRandom()
        val passphraseBytes = ByteArray(PASSPHRASE_LENGTH)
        secureRandom.nextBytes(passphraseBytes)

        // Convert to base64 for safe character representation
        val base64Passphrase = Base64.encodeToString(passphraseBytes, Base64.NO_WRAP)
        return base64Passphrase.toCharArray()
    }

    /**
     * Get encrypted shared preferences instance
     */
    private fun getEncryptedSharedPreferences(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Fallback passphrase generation when Android Keystore is not available
     */
    private fun generateFallbackPassphrase(context: Context): CharArray {
        Log.w(TAG, "Using fallback passphrase generation")

        // Use app-specific data for reproducible but unique passphrase
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val seed = "${context.packageName}_${packageInfo.firstInstallTime}_${android.os.Build.FINGERPRINT}"

        // Hash the seed to create a passphrase
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(seed.toByteArray())
        val base64Hash = Base64.encodeToString(hash, Base64.NO_WRAP)

        return base64Hash.toCharArray()
    }

    /**
     * Clear stored passphrase (for reset functionality)
     */
    fun clearStoredPassphrase(context: Context): Boolean {
        return try {
            val encryptedPrefs = getEncryptedSharedPreferences(context)
            encryptedPrefs.edit()
                .remove(PASSPHRASE_KEY)
                .apply()
            Log.i(TAG, "Cleared stored passphrase")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing passphrase", e)
            false
        }
    }

    /**
     * Encrypt sensitive data using Android Keystore
     */
    fun encryptData(context: Context, data: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val encryptedData = cipher.doFinal(data.toByteArray())
            val iv = cipher.iv

            // Combine IV and encrypted data
            val combined = iv + encryptedData
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting data", e)
            null
        }
    }

    /**
     * Decrypt sensitive data using Android Keystore
     */
    fun decryptData(context: Context, encryptedData: String): String? {
        return try {
            val secretKey = getOrCreateSecretKey()
            val combined = Base64.decode(encryptedData, Base64.NO_WRAP)

            // Extract IV and encrypted data
            val iv = combined.sliceArray(0..11) // GCM IV is typically 12 bytes
            val encrypted = combined.sliceArray(12 until combined.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)

            val decryptedData = cipher.doFinal(encrypted)
            String(decryptedData)
        } catch (e: Exception) {
            Log.e(TAG, "Error decrypting data", e)
            null
        }
    }

    /**
     * Get or create secret key in Android Keystore
     */
    private fun getOrCreateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Generate secure random token for device authentication
     */
    fun generateDeviceToken(): String {
        val secureRandom = SecureRandom()
        val tokenBytes = ByteArray(32) // 256-bit token
        secureRandom.nextBytes(tokenBytes)
        return Base64.encodeToString(tokenBytes, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Hash password securely using PBKDF2
     */
    fun hashPassword(password: String, salt: ByteArray): String {
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 10000, 256)
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    /**
     * Generate random salt for password hashing
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    /**
     * Verify password against hash
     */
    fun verifyPassword(password: String, salt: ByteArray, hash: String): Boolean {
        val computedHash = hashPassword(password, salt)
        return computedHash == hash
    }

    /**
     * Securely clear character array
     */
    fun clearArray(array: CharArray) {
        array.fill('\u0000')
    }

    /**
     * Securely clear byte array
     */
    fun clearArray(array: ByteArray) {
        array.fill(0)
    }
}
