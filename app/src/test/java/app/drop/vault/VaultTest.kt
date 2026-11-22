package app.drop.vault

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The Vault reads what 0.10.0 wrote, refuses wrong passphrases and tampering, and locks reliably. */
@RunWith(RobolectricTestRunner::class)
class VaultTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: VaultDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun repo() = VaultRepository(db, iterationsForNewKey = { 1_000 })

    private fun golden() = javaClass.classLoader!!.getResourceAsStream("vault-golden-0.10.0.jsonl")!!.bufferedReader().readLines().filter { it.isNotBlank() }.map { JSONObject(it) }

    private fun <T> expect(type: Class<out Throwable>, block: suspend () -> T) {
        try {
            runBlocking { block() }
            fail("expected ${type.simpleName}")
        } catch (e: Throwable) {
            assertTrue("got $e", type.isInstance(e))
        }
    }

    private suspend fun loadGolden() {
        val g = golden()
        val h = g.first { it.getString("kind") == "header" }
        db.vaultDao().insertHeader(VaultHeader(formatVersion = 1, iterations = h.getInt("iterations"), salt = hex(h.getString("salt")), keyNonce = hex(h.getString("nonce")), wrappedKey = hex(h.getString("wrapped"))))
        g.filter { it.getString("kind") == "note" }.forEach { db.vaultDao().putRecord(VaultRecord(it.getString("id"), hex(it.getString("nonce")), hex(it.getString("ct")))) }
    }

    private val passphrase get() = "Doğru at pil zımba 42!".toCharArray()

    @Test
    fun aVaultWrittenBy010OpensAndEveryNoteReads() = runBlocking {
        loadGolden()
        val repository = repo()
        val key = repository.unlock(passphrase)
        val listing = repository.list(key)
        assertEquals(0, listing.unreadable)
        val notes = golden().filter { it.getString("kind") == "note" }
        assertEquals(notes.size, listing.notes.size)
        for (n in notes) {
            val read = repository.get(key, n.getString("id"))!!
            assertEquals(n.getString("title"), read.title)
            assertEquals(n.getString("body"), read.body)
            assertEquals(n.getLong("c"), read.createdAt)
            assertEquals(n.getLong("u"), read.updatedAt)
            // The rebuilt code lays notes out byte for byte as 0.10.0 did.
            assertArrayEquals(hex(n.getString("plain")), VaultRepository.encode(read))
        }
        assertEquals(listing.notes.map { it.updatedAt }.sortedDescending(), listing.notes.map { it.updatedAt })
        key.destroy()
    }

    @Test
    fun aWrongPassphraseTamperingAndSwappedRecordsAreRefused() = runBlocking {
        loadGolden()
        val repository = repo()
        expect(WrongPassphraseException::class.java) { repository.unlock("doğru at pil zımba 42!".toCharArray()) }

        val records = db.vaultDao().records()
        val a = records[0]
        val b = records[1]
        // Move b's ciphertext under a's id: the id is bound into the encryption, so it no longer opens.
        db.vaultDao().putRecord(VaultRecord(a.id, b.nonce, b.ciphertext))
        // Flip one bit of another record.
        val c = records[2]
        db.vaultDao().putRecord(VaultRecord(c.id, c.nonce, c.ciphertext.copyOf().also { it[5] = (it[5].toInt() xor 1).toByte() }))
        val key = repository.unlock(passphrase)
        val listing = repository.list(key)
        assertEquals(2, listing.unreadable)
        assertEquals(records.size - 2, listing.notes.size)
        expect(VaultCryptoException::class.java) { repository.get(key, a.id) }
    }

    @Test
    fun notesArePaddedAndTheStoredBytesRevealNothing() = runBlocking {
        val repository = repo()
        val key = repository.create("long enough passphrase".toCharArray())
        val note = repository.newNote().copy(title = "PIN", body = "4921")
        repository.save(key, note)
        val record = db.vaultDao().record(note.id)!!
        assertEquals(0, (record.ciphertext.size - 16) % VaultRepository.PAD_BLOCK)
        val stored = String(record.ciphertext, Charsets.ISO_8859_1)
        assertFalse(stored.contains("PIN") || stored.contains("4921"))
        assertEquals(note, repository.get(key, note.id))
        expect(VaultAlreadyExistsException::class.java) { repository.create("other".toCharArray()) }
    }

    @Test
    fun changingThePassphraseKeepsNotesAndOnlyTheNewOneWorks() = runBlocking {
        val repository = repo()
        val key = repository.create("first passphrase".toCharArray())
        val note = repository.newNote().copy(title = "Keys")
        repository.save(key, note)
        repository.changePassphrase("first passphrase".toCharArray(), "second passphrase".toCharArray())
        expect(WrongPassphraseException::class.java) { repository.unlock("first passphrase".toCharArray()) }
        val again = repository.unlock("second passphrase".toCharArray())
        assertEquals(note, repository.get(again, note.id))
        // The notes themselves were not re-encrypted: the old key still opens them.
        assertEquals(note, repository.get(key, note.id))
    }

    @Test
    fun deletingTheVaultNeedsItsPassphrase() = runBlocking {
        val repository = repo()
        val key = repository.create("delete me".toCharArray())
        repository.save(key, repository.newNote())
        expect(WrongPassphraseException::class.java) { repository.deleteVault("wrong".toCharArray()) }
        assertTrue(repository.exists())
        repository.deleteVault("delete me".toCharArray())
        assertFalse(repository.exists())
        assertTrue(db.vaultDao().records().isEmpty())
        expect(NoVaultException::class.java) { repository.unlock("delete me".toCharArray()) }
    }

    @Test
    fun lockingWipesTheKeyAndIdleTimeLocksToo() {
        runBlocking {
            var now = 0L
            val repository = repo()
            val session = VaultSession({ repository }, scope, clock = { now }, idleTimeoutMs = 200)
            session.refresh()
            assertEquals(VaultState.Absent, session.state.value)
            session.create("session passphrase".toCharArray())
            assertTrue(session.state.value is VaultState.Unlocked)
            val kept = session.withKey { it }
            session.lock()
            assertEquals(VaultState.Locked, session.state.value)
            expect(VaultLockedException::class.java) { session.withKey { } }
            waitUntil { kept.isDestroyed }

            session.unlock("session passphrase".toCharArray())
            assertTrue(session.state.value is VaultState.Unlocked)
            now += 1_000
            waitUntil { session.state.value == VaultState.Locked }

            session.forget()
            assertEquals(VaultState.Absent, session.state.value)
        }
    }

    @Test
    fun aLockThatArrivesDuringUnlockWins() = runBlocking {
        val repository = repo()
        repository.create("race".toCharArray()).destroy()
        lateinit var session: VaultSession
        val slow = object : () -> VaultRepository {
            override fun invoke(): VaultRepository {
                session.lock()
                return repository
            }
        }
        session = VaultSession(slow, scope)
        session.unlock("race".toCharArray())
        assertEquals(VaultState.Locked, session.state.value)
    }

    @Test
    fun calibrationStaysWithinSafeBounds() {
        // A very slow phone still gets at least 310,000 rounds...
        var t = 0L
        assertEquals(VaultCrypto.MIN_ITERATIONS, VaultCrypto.calibrateIterations(750) { t.also { t += 10_000_000_000 } })
        // ...and a very fast one never more than 2,000,000.
        t = 0
        assertEquals(VaultCrypto.MAX_ITERATIONS, VaultCrypto.calibrateIterations(750) { t.also { t += 100_000 } })
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(20)
        }
    }
}
