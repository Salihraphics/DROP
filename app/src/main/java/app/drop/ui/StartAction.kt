package app.drop.ui

/** What the app was opened for: a shortcut, a widget, a notification or a share. */
sealed interface StartAction {
    data object NewNote : StartAction
    data class OpenNote(val id: Long) : StartAction
    data object ShowTasks : StartAction
    data object VoiceDump : StartAction
    data class OpenResurfaced(val id: Long) : StartAction
}
