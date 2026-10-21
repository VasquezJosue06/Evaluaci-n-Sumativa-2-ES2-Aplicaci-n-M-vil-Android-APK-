package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Gestión de autenticación y almacenamiento seguro de credenciales para la evaluación TI3042.
 * Implementa hashing PBKDF2 con HMAC-SHA256 y sal aleatoria criptográfica según las recomendaciones
 * de seguridad para sistemas IoT y principios de minimización de datos.
 */
class SecurityPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("secure_auth_prefs", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    companion object {
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16

        private const val KEY_USERNAME = "auth_username"
        private const val KEY_PASSWORD_HASH = "auth_password_hash"
        private const val KEY_PASSWORD_SALT = "auth_password_salt"
        private const val KEY_SESSION_ACTIVE = "auth_session_active"
        private const val KEY_LAST_LOGIN = "auth_last_login"

        // Usuario por defecto para evaluación académica
        const val DEFAULT_USER = "operador"
        const val DEFAULT_PASS = "BodegaIoT#2026"
    }

    init {
        // Inicializar usuario por defecto si no existe ninguno
        if (!hasUser()) {
            registerUser(DEFAULT_USER, DEFAULT_PASS)
        }
    }

    fun hasUser(): Boolean {
        return prefs.contains(KEY_USERNAME) && prefs.contains(KEY_PASSWORD_HASH)
    }

    fun getStoredUsername(): String {
        return prefs.getString(KEY_USERNAME, DEFAULT_USER) ?: DEFAULT_USER
    }

    fun isSessionActive(): Boolean {
        return prefs.getBoolean(KEY_SESSION_ACTIVE, false)
    }

    fun setSessionActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, active).apply()
        if (active) {
            prefs.edit().putLong(KEY_LAST_LOGIN, System.currentTimeMillis()).apply()
        }
    }

    /**
     * Registra o actualiza las credenciales aplicando PBKDF2 + HMAC-SHA256 con sal aleatoria.
     * Nunca almacena contraseñas en texto plano.
     */
    fun registerUser(username: String, plainPassword: String):Boolean {
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        val hash = hashPassword(plainPassword.toCharArray(), salt) ?: return false

        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .apply()
        return true
    }

    /**
     * Valida las credenciales comprobando el hash resultante frente al hash almacenado.
     */
    fun authenticate(username: String, plainPassword: String): Boolean {
        val storedUser = prefs.getString(KEY_USERNAME, null) ?: return false
        val storedHashBase64 = prefs.getString(KEY_PASSWORD_HASH, null) ?: return false
        val storedSaltBase64 = prefs.getString(KEY_PASSWORD_SALT, null) ?: return false

        if (storedUser != username.trim()) {
            return false
        }

        val salt = Base64.decode(storedSaltBase64, Base64.NO_WRAP)
        val storedHash = Base64.decode(storedHashBase64, Base64.NO_WRAP)
        val computedHash = hashPassword(plainPassword.toCharArray(), salt) ?: return false

        val matches = slowEquals(storedHash, computedHash)
        if (matches) {
            setSessionActive(true)
        }
        return matches
    }

    fun logout() {
        setSessionActive(false)
    }

    private fun hashPassword(password: CharArray, salt: ByteArray): ByteArray? {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        return try {
            val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            skf.generateSecret(spec).encoded
        } catch (_: NoSuchAlgorithmException) {
            null
        } catch (_: InvalidKeySpecException) {
            null
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Comparación en tiempo constante para mitigar ataques de temporización (timing attacks).
     */
    private fun slowEquals(a: ByteArray, b: ByteArray): Boolean {
        var diff = a.size xor b.size
        var i = 0
        while (i < a.size && i < b.size) {
            diff = diff or (a[i].toInt() xor b[i].toInt())
            i++
        }
        return diff == 0
    }
}
