package app.drop.data

import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class ThemeMode { System, Light, Dark }

/** What the person mainly uses DROP for. Asked once, changeable in Settings. */
enum class UseCase { Notes, Tasks, Study, Projects, Research }

enum class ExperienceStyle { Simple, Organized, Explore }

enum class Familiarity { New, Regular, Experienced }

enum class CaptureWhere { OnTheGo, Desk, Both }

/** The typeface for note titles and text. */
enum class NoteFont { Default, Serif, Mono }

/**
 * The optional answers that shape DROP. Every field is optional: unanswered means "use the
 * default", never a guess.
 */
data class Personalization(
    val useCases: Set<UseCase> = emptySet(),
    val style: ExperienceStyle? = null,
    val familiarity: Familiarity? = null,
    val where: CaptureWhere? = null,
    val savesLinks: Boolean? = null,
    val paper: Boolean? = null,
    val dictation: Boolean? = null,
    val journal: Boolean? = null,
    val longWriting: Boolean? = null,
    val repeatNotes: Boolean? = null,
    val reminders: Boolean? = null,
    val privateNotes: Boolean? = null,
) {
    /** The follow-up answers as stored: "key=value". */
    internal fun extras(): Map<String, String> = buildMap {
        where?.let { put("where", it.name) }
        savesLinks?.let { put("links", it.toString()) }
        paper?.let { put("paper", it.toString()) }
        dictation?.let { put("dictation", it.toString()) }
        journal?.let { put("journal", it.toString()) }
        longWriting?.let { put("long_writing", it.toString()) }
        repeatNotes?.let { put("repeat_notes", it.toString()) }
        reminders?.let { put("reminders", it.toString()) }
        privateNotes?.let { put("private_notes", it.toString()) }
    }

    internal fun withExtras(e: Map<String, String>): Personalization {
        fun flag(k: String): Boolean? = e[k]?.toBooleanStrictOrNull()
        return copy(
            where = e["where"]?.let { s -> CaptureWhere.entries.firstOrNull { it.name == s } },
            savesLinks = flag("links"),
            paper = flag("paper"),
            dictation = flag("dictation"),
            journal = flag("journal"),
            longWriting = flag("long_writing"),
            repeatNotes = flag("repeat_notes"),
            reminders = flag("reminders"),
            privateNotes = flag("private_notes"),
        )
    }
}

/** Small settings kept on the phone. Nothing here leaves it. */
class AppSettings(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(readTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _lastBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0 })
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    private val _speechLanguage = MutableStateFlow(prefs.getString(KEY_SPEECH_LANGUAGE, null) ?: defaultSpeechLanguage())
    val speechLanguage: StateFlow<String> = _speechLanguage.asStateFlow()

    /** Whether Voice Dump may use a speech service that isn't on the phone. Off unless the person allows it. */
    private val _allowSystemSpeech = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_SYSTEM_SPEECH, false))
    val allowSystemSpeech: StateFlow<Boolean> = _allowSystemSpeech.asStateFlow()

    private val _personalization = MutableStateFlow(readPersonalization())
    val personalization: StateFlow<Personalization> = _personalization.asStateFlow()

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_DONE, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    /** The version whose "What's new" was last shown, or null. */
    private val _releaseNotesSeen = MutableStateFlow(prefs.getString(KEY_RELEASE_NOTES_SEEN, null))
    val releaseNotesSeen: StateFlow<String?> = _releaseNotesSeen.asStateFlow()

    private val _echoEnabled = MutableStateFlow(prefs.getBoolean(KEY_ECHO_ENABLED, true))
    val echoEnabled: StateFlow<Boolean> = _echoEnabled.asStateFlow()

    private val _dismissedHints = MutableStateFlow(prefs.getStringSet(KEY_DISMISSED_HINTS, emptySet()).orEmpty().toSet())
    val dismissedHints: StateFlow<Set<String>> = _dismissedHints.asStateFlow()

    private val _noteFont = MutableStateFlow(
        prefs.getString(KEY_NOTE_FONT, null)?.let { s -> NoteFont.entries.firstOrNull { it.name == s } } ?: NoteFont.Default,
    )
    val noteFont: StateFlow<NoteFont> = _noteFont.asStateFlow()

    /**
     * Runs once per installation. Anyone who already used DROP (a library exists, the app was
     * updated in place, or earlier settings are present) is never sent through onboarding again.
     */
    fun initLaunchState(databaseExisted: Boolean, updatedInPlace: Boolean, currentVersion: String) {
        if (prefs.contains(KEY_LAUNCH_STATE)) return
        val earlierSettings = prefs.all.isNotEmpty()
        val existing = databaseExisted || updatedInPlace || earlierSettings
        prefs.edit().apply {
            putInt(KEY_LAUNCH_STATE, 1)
            if (existing) {
                putBoolean(KEY_ONBOARDING_DONE, true)
            } else {
                putBoolean(KEY_ONBOARDING_DONE, false)
                // A new installation has nothing to be told about.
                putString(KEY_RELEASE_NOTES_SEEN, currentVersion)
            }
        }.apply()
        _onboardingDone.value = existing
        _releaseNotesSeen.value = if (existing) null else currentVersion
    }

    fun setPersonalization(value: Personalization) {
        prefs.edit()
            .putStringSet(KEY_USE_CASES, value.useCases.mapTo(HashSet()) { it.name })
            .putString(KEY_STYLE, value.style?.name)
            .putString(KEY_FAMILIARITY, value.familiarity?.name)
            .putStringSet(KEY_EXTRAS, value.extras().mapTo(HashSet()) { (k, v) -> "$k=$v" })
            .apply()
        _personalization.value = value
    }

    fun completeOnboarding(currentVersion: String) {
        val seen = _releaseNotesSeen.value
        val edit = prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true)
        if (seen == null) edit.putString(KEY_RELEASE_NOTES_SEEN, currentVersion)
        edit.apply()
        _onboardingDone.value = true
        if (seen == null) _releaseNotesSeen.value = currentVersion
    }

    fun markReleaseNotesSeen(version: String) {
        prefs.edit().putString(KEY_RELEASE_NOTES_SEEN, version).apply()
        _releaseNotesSeen.value = version
    }

    fun setEchoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ECHO_ENABLED, enabled).apply()
        _echoEnabled.value = enabled
    }

    fun dismissHint(id: String) {
        val hints = _dismissedHints.value + id
        prefs.edit().putStringSet(KEY_DISMISSED_HINTS, hints).apply()
        _dismissedHints.value = hints
    }

    private fun readPersonalization(): Personalization {
        val useCases = prefs.getStringSet(KEY_USE_CASES, emptySet()).orEmpty()
            .mapNotNullTo(HashSet()) { saved -> UseCase.entries.firstOrNull { it.name == saved } }
        val style = prefs.getString(KEY_STYLE, null)?.let { s -> ExperienceStyle.entries.firstOrNull { it.name == s } }
        val familiarity = prefs.getString(KEY_FAMILIARITY, null)?.let { s -> Familiarity.entries.firstOrNull { it.name == s } }
        val extras = prefs.getStringSet(KEY_EXTRAS, emptySet()).orEmpty()
            .mapNotNull { e -> e.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        return Personalization(useCases, style, familiarity).withExtras(extras)
    }

    fun setSpeechLanguage(tag: String) {
        prefs.edit().putString(KEY_SPEECH_LANGUAGE, tag).apply()
        _speechLanguage.value = tag
    }

    fun setAllowSystemSpeech(allow: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_SYSTEM_SPEECH, allow).apply()
        _allowSystemSpeech.value = allow
    }

    fun setNoteFont(font: NoteFont) {
        prefs.edit().putString(KEY_NOTE_FONT, font.name).apply()
        _noteFont.value = font
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _themeMode.value = mode
        applyToSystem(mode)
    }

    fun recordBackup(at: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, at).apply()
        _lastBackupAt.value = at
    }

    /** On Android 12+ the system also draws DROP's splash screen and dialogs in the chosen theme. */
    fun applyToSystem(mode: ThemeMode = themeMode.value) {
        if (Build.VERSION.SDK_INT < 31) return
        val manager = appContext.getSystemService(UiModeManager::class.java) ?: return
        manager.setApplicationNightMode(
            when (mode) {
                ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
            },
        )
    }

    private fun readTheme(): ThemeMode =
        prefs.getString(KEY_THEME, null)?.let { s -> ThemeMode.entries.firstOrNull { it.name == s } } ?: ThemeMode.System

    companion object {
        private const val FILE = "drop_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_LAST_BACKUP = "last_backup_at"
        private const val KEY_SPEECH_LANGUAGE = "speech_language"
        private const val KEY_ALLOW_SYSTEM_SPEECH = "allow_system_speech"
        private const val KEY_USE_CASES = "personal_use_cases"
        private const val KEY_STYLE = "personal_style"
        private const val KEY_FAMILIARITY = "personal_familiarity"
        private const val KEY_EXTRAS = "personal_extras"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        private const val KEY_RELEASE_NOTES_SEEN = "release_notes_seen"
        private const val KEY_LAUNCH_STATE = "launch_state"
        private const val KEY_ECHO_ENABLED = "echo_enabled"
        private const val KEY_DISMISSED_HINTS = "dismissed_hints"
        private const val KEY_NOTE_FONT = "note_font"

        fun defaultSpeechLanguage(locale: Locale = Locale.getDefault()): String = if (locale.language == "tr") "tr-TR" else "en-US"
    }
}
