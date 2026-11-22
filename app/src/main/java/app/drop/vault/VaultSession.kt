package app.drop.vault

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

sealed interface VaultState {
    data object Unknown : VaultState
    data object Absent : VaultState
    data object Locked : VaultState

    /** [epoch] changes each time the Vault is unlocked, so screens can tell sessions apart. */
    data class Unlocked(val epoch: Long) : VaultState

    /** The Vault file could not be read. */
    data object Unavailable : VaultState
}

/**
 * Holds the vault key while the Vault is open. It locks (and wipes the key) when asked, when the
 * app leaves the screen, when the screen turns off and after [idleTimeoutMs] without activity.
 */
class VaultSession(
    private val repository: () -> VaultRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) {
    private val _state = MutableStateFlow<VaultState>(VaultState.Unknown)
    val state: StateFlow<VaultState> = _state.asStateFlow()
    private val mutex = Mutex()
    private var key: VaultKey? = null

    @Volatile
    private var epoch = 0L

    @Volatile
    private var lastActivity = 0L

    /** Counts lock requests, so an unlock that finishes after a lock does not reopen the Vault. */
    private val locks = AtomicLong()
    private var idleWatch: Job? = null

    suspend fun refresh() {
        if (_state.value is VaultState.Unlocked) return
        _state.value = try {
            if (repository().exists()) VaultState.Locked else VaultState.Absent
        } catch (e: Exception) {
            VaultState.Unavailable
        }
    }

    suspend fun create(passphrase: CharArray) {
        val requested = locks.get()
        mutex.withLock { open(repository().create(passphrase), requested) }
    }

    suspend fun unlock(passphrase: CharArray) {
        val requested = locks.get()
        mutex.withLock { open(repository().unlock(passphrase), requested) }
    }

    private fun open(newKey: VaultKey, requested: Long) {
        if (locks.get() != requested) {
            newKey.destroy()
            _state.value = VaultState.Locked
            return
        }
        key?.destroy()
        key = newKey
        epoch++
        touch()
        _state.value = VaultState.Unlocked(epoch)
        startIdleWatch()
    }

    /** Locks at once; the key is wiped as soon as no Vault work is running. */
    fun lock() {
        locks.incrementAndGet()
        val lockedEpoch = epoch
        if (_state.value is VaultState.Unlocked) _state.value = VaultState.Locked
        idleWatch?.cancel()
        scope.launch {
            mutex.withLock {
                if (epoch == lockedEpoch) {
                    key?.destroy()
                    key = null
                }
            }
        }
    }

    /** After the Vault was deleted. */
    suspend fun forget() {
        mutex.withLock {
            key?.destroy()
            key = null
            _state.value = VaultState.Absent
        }
    }

    fun touch() {
        lastActivity = clock()
    }

    /** Runs [block] with the key; throws [VaultLockedException] if the Vault is not open. */
    suspend fun <T> withKey(block: suspend (VaultKey) -> T): T {
        if (_state.value !is VaultState.Unlocked) throw VaultLockedException()
        return mutex.withLock {
            val current = key
            if (current == null || current.isDestroyed) throw VaultLockedException()
            touch()
            block(current)
        }
    }

    private fun startIdleWatch() {
        idleWatch?.cancel()
        idleWatch = scope.launch {
            while (isActive) {
                delay(minOf(idleTimeoutMs, 15_000))
                if (clock() - lastActivity >= idleTimeoutMs) {
                    lock()
                    return@launch
                }
            }
        }
    }

    companion object {
        const val DEFAULT_IDLE_TIMEOUT_MS = 300_000L
    }
}
