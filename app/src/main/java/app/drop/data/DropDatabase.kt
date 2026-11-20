package app.drop.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.drop.echo.EchoDao

/** The main library. The Vault has its own encrypted database and is never part of this one. */
@Database(
    entities = [
        Note::class, Attachment::class, Reminder::class, Project::class, ProjectItem::class, NoteLink::class,
        DailyEntry::class, IdeaCanvas::class, CanvasCard::class, CanvasEdge::class, NoteRevision::class,
        NoteTemplate::class, SmartCollection::class, NoteAlias::class, NoteProperty::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class DropDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun reminderDao(): ReminderDao
    abstract fun projectDao(): ProjectDao
    abstract fun linkDao(): LinkDao
    abstract fun dailyDao(): DailyDao
    abstract fun canvasDao(): CanvasDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun revisionDao(): RevisionDao
    abstract fun templateDao(): TemplateDao
    abstract fun collectionDao(): CollectionDao
    abstract fun metaDao(): MetaDao
    abstract fun insightDao(): InsightDao
    abstract fun echoDao(): EchoDao

    companion object {
        const val FILE_NAME = "drop.db"

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)

        fun create(context: Context): DropDatabase =
            Room.databaseBuilder(context.applicationContext, DropDatabase::class.java, FILE_NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
