package app.drop.ui.vault

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.drop.DropApplication
import app.drop.vault.VaultListing
import app.drop.vault.VaultLockedException
import app.drop.vault.VaultNote
import app.drop.vault.VaultRepository
import app.drop.vault.VaultSession
import app.drop.vault.VaultState
import app.drop.vault.WrongPassphraseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Arrays

object PassphrasePolicy {
    const val MIN_LENGTH = 8
}

enum class VaultError { WrongPassphrase, TooManyAttempts, Mismatch, TooShort, Failed }

enum class VaultDialog { ChangePassphrase, Delete, Forgot }

/** The Vault screen: create, unlock, list, change the passphrase, delete or erase. */
@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(
    private val session: VaultSession,
    private val repository: VaultRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val state: StateFlow<VaultState> = session.state
    private val reload = MutableStateFlow(0)

    /** The decrypted notes while the Vault is open, otherwise null. */
    val listing: StateFlow<VaultListing?> = combine(state, reload) { s, _ -> s }
        .mapLatest { s ->
            if (s !is VaultState.Unlocked) return@mapLatest null
            val result = try {
                session.withKey { repository.list(it) }
            } catch (e: VaultLockedException) {
                null
            }
            // Never show notes that were read before the Vault locked again.
            if (session.state.value == s) result else null
        }
        .flowOn(Dispatchers.IO)
        // Stops and forgets the plaintext as soon as the list is off screen, and reads it afresh on return.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0, 0), null)

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<VaultError?>(null)
        private set
    var failedAttempts by mutableIntStateOf(0)
        private set
    private var cooldownUntil = 0L

    init {
        viewModelScope.launch { session.refresh() }
    }

    fun create(passphrase: String, confirmation: String) {
        when {
            passphrase.length < PassphrasePolicy.MIN_LENGTH -> error = VaultError.TooShort
            passphrase != confirmation -> error = VaultError.Mismatch
            else -> run {
                val chars = passphrase.toCharArray()
                try {
                    session.create(chars)
                } finally {
                    Arrays.fill(chars, '\u0000')
                }
            }
        }
    }

    fun unlock(passphrase: String) {
        if (clock() < cooldownUntil) {
            error = VaultError.TooManyAttempts
            return
        }
        run(onWrong = {
            failedAttempts++
            if (failedAttempts % MAX_ATTEMPTS == 0) cooldownUntil = clock() + COOLDOWN_MS
        }) {
            val chars = passphrase.toCharArray()
            try {
                session.unlock(chars)
            } finally {
                Arrays.fill(chars, '\u0000')
            }
            failedAttempts = 0
        }
    }

    fun lock() = session.lock()

    fun touch() = session.touch()

    fun reloadNotes() {
        reload.value++
    }

    fun clearError() {
        error = null
    }

    fun changePassphrase(current: String, new: String, confirmation: String, onDone: () -> Unit) {
        when {
            new.length < PassphrasePolicy.MIN_LENGTH -> error = VaultError.TooShort
            new != confirmation -> error = VaultError.Mismatch
            else -> run {
                val old = current.toCharArray()
                val next = new.toCharArray()
                try {
                    session.withKey { repository.changePassphrase(old, next) }
                } finally {
                    Arrays.fill(old, '\u0000')
                    Arrays.fill(next, '\u0000')
                }
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    fun deleteVault(passphrase: String, onDone: () -> Unit) {
        run {
            val chars = passphrase.toCharArray()
            try {
                repository.deleteVault(chars)
            } finally {
                Arrays.fill(chars, '\u0000')
            }
            session.forget()
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    /** "Forgot passphrase": erases the Vault and its notes. */
    fun eraseForgotten(onDone: () -> Unit) {
        run {
            repository.erase()
            session.forget()
            failedAttempts = 0
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun run(onWrong: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { block() }
            } catch (e: WrongPassphraseException) {
                onWrong()
                error = VaultError.WrongPassphrase
            } catch (e: Exception) {
                error = VaultError.Failed
            } finally {
                busy = false
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val COOLDOWN_MS = 30_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                VaultViewModel(app.vault, app.vaultRepository)
            }
        }
    }
}

/**
 * Edits one Vault note. The plaintext lives only in this ViewModel and is dropped the moment the
 * Vault locks; changes are encrypted and saved as the person types.
 */
class VaultEditorViewModel(
    handle: SavedStateHandle,
    private val session: VaultSession,
    private val repository: VaultRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val autosaveDelayMs: Long = AUTOSAVE_DELAY_MS,
) : ViewModel() {
    private val noteId: String? = handle[ARG_ID]

    var title by mutableStateOf("")
        private set
    var body by mutableStateOf("")
        private set
    var loaded by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
        private set
    var closed by mutableStateOf(false)
        private set
    var saveFailed by mutableStateOf(false)
        private set
    var saved by mutableStateOf(false)
        private set

    private var note: VaultNote? = null
    private var stored = false
    private var autosave: Job? = null

    init {
        viewModelScope.launch {
            session.state.collect { if (it !is VaultState.Unlocked) clearPlaintext() }
        }
        viewModelScope.launch {
            if (session.state.value !is VaultState.Unlocked) {
                clearPlaintext()
                return@launch
            }
            val existing = if (noteId != null && noteId != NEW) {
                try {
                    withContext(Dispatchers.Default) { session.withKey { repository.get(it, noteId) } }
                } catch (e: Exception) {
                    clearPlaintext()
                    return@launch
                }
            } else {
                null
            }
            if (locked) return@launch
            val current = existing ?: repository.newNote()
            note = current
            stored = existing != null
            title = current.title
            body = current.body
            saved = stored
            loaded = true
        }
    }

    private fun clearPlaintext() {
        autosave?.cancel()
        title = ""
        body = ""
        note = null
        loaded = false
        locked = true
    }

    fun onTitleChange(value: String) {
        if (!loaded) return
        title = value
        scheduleSave()
    }

    fun onBodyChange(value: String) {
        if (!loaded) return
        body = value
        scheduleSave()
    }

    private fun scheduleSave() {
        session.touch()
        saved = false
        autosave?.cancel()
        autosave = viewModelScope.launch {
            delay(autosaveDelayMs)
            save()
        }
    }

    suspend fun save() {
        val current = note ?: return
        val newTitle = title
        val newBody = body
        if (stored && newTitle == current.title && newBody == current.body) return
        if (!stored && newTitle.isBlank() && newBody.isBlank()) return
        val updated = current.copy(title = newTitle, body = newBody, updatedAt = clock())
        try {
            withContext(NonCancellable) {
                withContext(Dispatchers.Default) { session.withKey { repository.save(it, updated) } }
            }
            note = updated
            stored = true
            saveFailed = false
            saved = title == updated.title && body == updated.body
        } catch (e: VaultLockedException) {
            // The Vault locked; the screen closes and the plaintext is already gone.
        } catch (e: Exception) {
            saveFailed = true
        }
    }

    fun saveNow() {
        autosave?.cancel()
        viewModelScope.launch { save() }
    }

    fun close() {
        autosave?.cancel()
        viewModelScope.launch {
            save()
            closed = true
        }
    }

    fun delete() {
        autosave?.cancel()
        val current = note
        viewModelScope.launch {
            try {
                if (current != null && stored) {
                    withContext(Dispatchers.Default) { session.withKey { repository.delete(it, current.id) } }
                }
            } catch (e: Exception) {
                saveFailed = true
                return@launch
            }
            clearPlaintext()
            closed = true
        }
    }

    fun touch() = session.touch()

    companion object {
        const val ARG_ID = "vaultNoteId"
        const val NEW = "new"
        const val AUTOSAVE_DELAY_MS = 800L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DropApplication
                VaultEditorViewModel(createSavedStateHandle(), app.vault, app.vaultRepository)
            }
        }
    }
}
