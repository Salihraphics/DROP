package app.drop.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/*
 * Every schema change since 0.1. Migrations only add: no table or column is ever dropped,
 * so a library from any earlier version opens with all of its data.
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            UPDATE notes SET uuid =
                lower(hex(randomblob(4))) || '-' ||
                lower(hex(randomblob(2))) || '-4' ||
                substr(lower(hex(randomblob(2))), 2) || '-' ||
                substr('89ab', 1 + (abs(random()) % 4), 1) ||
                substr(lower(hex(randomblob(2))), 2) || '-' ||
                lower(hex(randomblob(6)))
            WHERE uuid = ''
            """.trimIndent(),
        )
        db.execSQL("ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_notes_uuid ON notes (uuid)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_deleted_at ON notes (deleted_at)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE notes ADD COLUMN is_task INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE notes ADD COLUMN completed_at INTEGER")
        db.execSQL("ALTER TABLE notes ADD COLUMN url TEXT")
        // Links found in existing notes, so they show as links right away.
        val links = mutableListOf<Pair<Long, String>>()
        db.query("SELECT id, title, body FROM notes").use { c ->
            while (c.moveToNext()) {
                Links.find(c.getString(1), c.getString(2))?.let { links += c.getLong(0) to it }
            }
        }
        links.forEach { (id, url) -> db.execSQL("UPDATE notes SET url = ? WHERE id = ?", arrayOf<Any>(url, id)) }
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `note_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `file_name` TEXT NOT NULL, `display_name` TEXT NOT NULL, `mime_type` TEXT NOT NULL, `size_bytes` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `duration_ms` INTEGER, `page_count` INTEGER, `ocr_status` TEXT NOT NULL DEFAULT 'none', `ocr_text` TEXT, `ocr_edited` INTEGER NOT NULL DEFAULT 0, `ocr_updated_at` INTEGER, FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_uuid` ON `attachments` (`uuid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_note_id` ON `attachments` (`note_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `reminders` (`note_id` INTEGER NOT NULL, `local_time` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `fired_at` INTEGER, PRIMARY KEY(`note_id`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `projects` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL DEFAULT '', `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_projects_uuid` ON `projects` (`uuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `project_items` (`project_id` INTEGER NOT NULL, `note_id` INTEGER NOT NULL, `added_at` INTEGER NOT NULL, PRIMARY KEY(`project_id`, `note_id`), FOREIGN KEY(`project_id`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_items_note_id` ON `project_items` (`note_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_links` (`source_id` INTEGER NOT NULL, `label` TEXT NOT NULL, `target_id` INTEGER NOT NULL, PRIMARY KEY(`source_id`, `label`), FOREIGN KEY(`source_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`target_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_links_target_id` ON `note_links` (`target_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `daily_entries` (`date` TEXT NOT NULL, `text` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`date`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `canvases` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_canvases_uuid` ON `canvases` (`uuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `canvas_cards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `canvas_id` INTEGER NOT NULL, `note_id` INTEGER NOT NULL, `x` REAL NOT NULL, `y` REAL NOT NULL, FOREIGN KEY(`canvas_id`) REFERENCES `canvases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_canvas_cards_canvas_id_note_id` ON `canvas_cards` (`canvas_id`, `note_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_canvas_cards_note_id` ON `canvas_cards` (`note_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `canvas_edges` (`from_card` INTEGER NOT NULL, `to_card` INTEGER NOT NULL, PRIMARY KEY(`from_card`, `to_card`), FOREIGN KEY(`from_card`) REFERENCES `canvas_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`to_card`) REFERENCES `canvas_cards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_canvas_edges_to_card` ON `canvas_edges` (`to_card`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `format` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_revisions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `note_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `format` INTEGER NOT NULL DEFAULT 0, `edited_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_revisions_note_id` ON `note_revisions` (`note_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `title` TEXT NOT NULL, `body` TEXT NOT NULL, `format` INTEGER NOT NULL DEFAULT 0, `is_task` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_templates_uuid` ON `templates` (`uuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `smart_collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `rules` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_smart_collections_uuid` ON `smart_collections` (`uuid`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_aliases` (`note_id` INTEGER NOT NULL, `alias` TEXT NOT NULL, `key` TEXT NOT NULL, PRIMARY KEY(`note_id`, `key`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_aliases_key` ON `note_aliases` (`key`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `note_properties` (`note_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `key` TEXT NOT NULL, `type` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`note_id`, `key`), FOREIGN KEY(`note_id`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_properties_key` ON `note_properties` (`key`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `canvas_cards` ADD COLUMN `shape` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `canvas_cards` ADD COLUMN `color` INTEGER NOT NULL DEFAULT 0")
    }
}
