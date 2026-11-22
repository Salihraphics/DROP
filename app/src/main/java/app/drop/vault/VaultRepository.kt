package app.drop.vault

import androidx.room.withTransaction
import org.json.JSONObject
import java.nio.ByteBuffer
import java.util.UUID

class NoVaultException : Exception("no vault")
class VaultAlreadyExistsException : Exception("vault already exists")
class VaultLockedException : Exception("vault is locked")
class WrongPassphraseException : Exception("wrong passphrase")

data class VaultNote(val id: String, val title: String, val body: String, val createdAt: Long, val updatedAt: Long)

/** The readable notes, newest first, and how many could not be decrypted. */
data class VaultListing(val notes: List<VaultNote>, val unreadable: Int)

/**
 * Stores Vault notes encrypted. Each note is sealed with the vault key and its id as associated
 * data (so records cannot be swapped), and padded to 256-byte blocks so its size gives little away.
 */
class VaultRepository(
    private val database: VaultDatabase,
    private val iterationsForNewKey: () -> Int = { VaultCrypto.calibrateIterations() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val dao: VaultDao = database.vaultDao(),
) {
    suspend fun exists(): Boolean = dao.header() != null

    suspend fun create(passphrase: CharArray): VaultKey {
        if (dao.header() != null) throw VaultAlreadyExistsException()
        val vaultKey = VaultCrypto.randomBytes(VaultCrypto.KEY_BYTES)
        try {
            dao.insertHeader(wrap(vaultKey, passphrase))
            return VaultKey(vaultKey)
        } finally {
            vaultKey.fill(0)
        }
    }

    suspend fun unlock(passphrase: CharArray): VaultKey {
        val header = dao.header() ?: throw NoVaultException()
        val bytes = unwrap(header, passphrase)
        try {
            return VaultKey(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    /** Re-wraps the same vault key: notes are not re-encrypted, only the header changes. */
    suspend fun changePassphrase(current: CharArray, new: CharArray) {
        val header = dao.header() ?: throw NoVaultException()
        val vaultKey = unwrap(header, current)
        try {
            dao.updateHeader(wrap(vaultKey, new))
        } finally {
            vaultKey.fill(0)
        }
    }

    suspend fun list(key: VaultKey): VaultListing {
        var unreadable = 0
        val notes = dao.records().mapNotNull { record ->
            try {
                decode(record.id, key.use { VaultCrypto.open(it, Sealed(record.nonce, record.ciphertext), itemAad(record.id)) })
            } catch (e: VaultCryptoException) {
                unreadable++
                null
            } catch (e: Exception) {
                unreadable++
                null
            }
        }
        return VaultListing(notes.sortedByDescending { it.updatedAt }, unreadable)
    }

    suspend fun get(key: VaultKey, id: String): VaultNote? {
        val record = dao.record(id) ?: return null
        return decode(id, key.use { VaultCrypto.open(it, Sealed(record.nonce, record.ciphertext), itemAad(id)) })
    }

    suspend fun save(key: VaultKey, note: VaultNote) {
        val plaintext = encode(note)
        try {
            val sealed = key.use { VaultCrypto.seal(it, plaintext, itemAad(note.id)) }
            dao.putRecord(VaultRecord(note.id, sealed.nonce, sealed.ciphertext))
        } finally {
            plaintext.fill(0)
        }
    }

    fun newNote(): VaultNote {
        val now = clock()
        return VaultNote(UUID.randomUUID().toString(), "", "", now, now)
    }

    /** Deleting needs the Vault unlocked, like reading. */
    suspend fun delete(key: VaultKey, id: String) {
        key.use { }
        dao.deleteRecord(id)
    }

    /** Deletes the whole Vault, but only with the right passphrase. */
    suspend fun deleteVault(passphrase: CharArray) {
        val header = dao.header() ?: throw NoVaultException()
        unwrap(header, passphrase).fill(0)
        erase()
    }

    suspend fun erase() {
        database.withTransaction {
            dao.deleteAllRecords()
            dao.deleteHeader()
        }
    }

    private fun wrap(vaultKey: ByteArray, passphrase: CharArray): VaultHeader {
        val salt = VaultCrypto.randomBytes(VaultCrypto.SALT_BYTES)
        val iterations = iterationsForNewKey()
        val kek = VaultCrypto.deriveKey(passphrase, salt, iterations)
        try {
            val sealed = VaultCrypto.seal(kek, vaultKey, keyAad(iterations, salt))
            return VaultHeader(formatVersion = FORMAT_VERSION, iterations = iterations, salt = salt, keyNonce = sealed.nonce, wrappedKey = sealed.ciphertext)
        } finally {
            kek.fill(0)
        }
    }

    private fun unwrap(header: VaultHeader, passphrase: CharArray): ByteArray {
        if (header.formatVersion != FORMAT_VERSION) throw VaultCryptoException("unsupported vault format")
        val kek = VaultCrypto.deriveKey(passphrase, header.salt, header.iterations)
        try {
            return VaultCrypto.open(kek, Sealed(header.keyNonce, header.wrappedKey), keyAad(header.iterations, header.salt))
        } catch (e: VaultCryptoException) {
            throw WrongPassphraseException()
        } finally {
            kek.fill(0)
        }
    }

    companion object {
        const val FORMAT_VERSION = 1
        const val PAD_BLOCK = 256

        fun keyAad(iterations: Int, salt: ByteArray): ByteArray = "drop-vault-key:v1:$iterations:".toByteArray(Charsets.UTF_8) + salt

        fun itemAad(id: String): ByteArray = "drop-vault-item:v1:$id".toByteArray(Charsets.UTF_8)

        /** A length prefix, the note as JSON, then zero padding up to a multiple of [PAD_BLOCK]. */
        internal fun encode(note: VaultNote): ByteArray {
            val json = JSONObject().put("t", note.title).put("b", note.body).put("c", note.createdAt).put("u", note.updatedAt)
                .toString().toByteArray(Charsets.UTF_8)
            val padded = (json.size + 4 + PAD_BLOCK - 1) / PAD_BLOCK * PAD_BLOCK
            val out = ByteBuffer.allocate(padded).putInt(json.size).put(json).array()
            json.fill(0)
            return out
        }

        internal fun decode(id: String, plaintext: ByteArray): VaultNote {
            try {
                val buffer = ByteBuffer.wrap(plaintext)
                val length = buffer.int
                require(length >= 0 && length <= plaintext.size - 4) { "invalid length" }
                val json = JSONObject(String(plaintext, 4, length, Charsets.UTF_8))
                return VaultNote(id, json.getString("t"), json.getString("b"), json.getLong("c"), json.getLong("u"))
            } finally {
                plaintext.fill(0)
            }
        }
    }
}
