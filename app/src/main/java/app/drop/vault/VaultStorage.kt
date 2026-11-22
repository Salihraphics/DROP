package app.drop.vault

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/** The single row describing the vault: how the vault key is wrapped by the passphrase. */
@Entity(tableName = "vault_header")
class VaultHeader(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    val iterations: Int,
    val salt: ByteArray,
    @ColumnInfo(name = "key_nonce") val keyNonce: ByteArray,
    @ColumnInfo(name = "wrapped_key") val wrappedKey: ByteArray,
)

/** One encrypted note. Only its random id is stored in the clear. */
@Entity(tableName = "vault_items")
class VaultRecord(
    @PrimaryKey val id: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_header WHERE id = 1")
    suspend fun header(): VaultHeader?

    @Insert
    suspend fun insertHeader(header: VaultHeader)

    @Update
    suspend fun updateHeader(header: VaultHeader)

    @Query("DELETE FROM vault_header")
    suspend fun deleteHeader()

    @Query("SELECT * FROM vault_items")
    suspend fun records(): List<VaultRecord>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun record(id: String): VaultRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putRecord(record: VaultRecord)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteRecord(id: String)

    @Query("DELETE FROM vault_items")
    suspend fun deleteAllRecords()
}

/** A separate database file, so the Vault never mixes with ordinary notes, backups or exports. */
@Database(entities = [VaultHeader::class, VaultRecord::class], version = 1)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao

    companion object {
        const val FILE_NAME = "vault.db"

        fun create(context: Context, name: String = FILE_NAME): VaultDatabase =
            Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, name)
                .openHelperFactory(KeepOnCorruption)
                .build()
    }
}

/**
 * Android's default reaction to a corrupt database file is to delete it. For the Vault that
 * would destroy notes that exist nowhere else, so the file is kept and the error surfaces instead.
 */
internal object KeepOnCorruption : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val room = configuration.callback
        val callback = object : SupportSQLiteOpenHelper.Callback(room.version) {
            override fun onConfigure(db: SupportSQLiteDatabase) = room.onConfigure(db)
            override fun onCreate(db: SupportSQLiteDatabase) = room.onCreate(db)
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onUpgrade(db, oldVersion, newVersion)
            override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onDowngrade(db, oldVersion, newVersion)
            override fun onOpen(db: SupportSQLiteDatabase) = room.onOpen(db)
            override fun onCorruption(db: SupportSQLiteDatabase) = Unit
        }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build(),
        )
    }
}
