package app.drop.vault

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Ciphertext with the nonce it was sealed with. */
class Sealed(val nonce: ByteArray, val ciphertext: ByteArray)

class VaultCryptoException(message: String) : Exception(message)

/**
 * The Vault's cryptography: AES-256-GCM for every note, and a key derived from the passphrase
 * with PBKDF2-HMAC-SHA256 that only ever wraps the random vault key.
 */
object VaultCrypto {
    const val KDF_NAME = "PBKDF2WithHmacSHA256"
    const val KEY_BYTES = 32
    const val SALT_BYTES = 16
    const val NONCE_BYTES = 12
    const val MIN_ITERATIONS = 310_000
    const val MAX_ITERATIONS = 2_000_000
    private const val TAG_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun randomBytes(count: Int): ByteArray = ByteArray(count).also(random::nextBytes)

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        require(salt.size >= SALT_BYTES) { "salt too short" }
        require(iterations >= 1) { "invalid iteration count" }
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_BYTES * 8)
        try {
            return SecretKeyFactory.getInstance(KDF_NAME).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun seal(key: ByteArray, plaintext: ByteArray, aad: ByteArray): Sealed {
        checkKey(key)
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return Sealed(nonce, cipher.doFinal(plaintext))
    }

    /** Decrypts and checks [sealed]; any tampering or a wrong key throws [VaultCryptoException]. */
    fun open(key: ByteArray, sealed: Sealed, aad: ByteArray): ByteArray {
        checkKey(key)
        if (sealed.nonce.size != NONCE_BYTES) throw VaultCryptoException("invalid nonce")
        if (sealed.ciphertext.size < TAG_BITS / 8) throw VaultCryptoException("ciphertext too short")
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, sealed.nonce))
            cipher.updateAAD(aad)
            return cipher.doFinal(sealed.ciphertext)
        } catch (e: GeneralSecurityException) {
            throw VaultCryptoException("authentication failed")
        }
    }

    /**
     * How many PBKDF2 rounds take about [targetMillis] on this phone, never fewer than
     * [MIN_ITERATIONS] (OWASP's recommendation) nor more than [MAX_ITERATIONS].
     */
    fun calibrateIterations(targetMillis: Long = 750, clock: () -> Long = System::nanoTime): Int {
        val salt = randomBytes(SALT_BYTES)
        val start = clock()
        deriveKey("calibration".toCharArray(), salt, SAMPLE).fill(0)
        val elapsedMs = ((clock() - start) / 1_000_000.0).coerceAtLeast(0.5)
        val scaled = (SAMPLE * (targetMillis / elapsedMs)).toLong()
        return scaled.coerceIn(MIN_ITERATIONS.toLong(), MAX_ITERATIONS.toLong()).toInt()
    }

    private const val SAMPLE = 20_000

    private fun checkKey(key: ByteArray) {
        if (key.size != KEY_BYTES) throw VaultCryptoException("invalid key")
    }
}

/** The unlocked vault key, wiped from memory as soon as the Vault locks. */
class VaultKey(bytes: ByteArray) {
    private val bytes: ByteArray = bytes.copyOf()

    @Volatile
    var isDestroyed: Boolean = false
        private set

    internal fun <T> use(block: (ByteArray) -> T): T {
        check(!isDestroyed) { "vault key destroyed" }
        return block(bytes)
    }

    fun destroy() {
        bytes.fill(0)
        isDestroyed = true
    }
}
