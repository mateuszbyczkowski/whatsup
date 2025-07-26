package com.example.whadgest.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.whadgest.data.dao.QueuedEventDao
import com.example.whadgest.data.entity.QueuedEvent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * Main Room database for the WhatsApp Digest app
 * Uses SQLCipher for encryption
 */
@Database(
    entities = [QueuedEvent::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun queuedEventDao(): QueuedEventDao

    companion object {
        private const val DATABASE_NAME = "whadgest_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Get database instance with encryption
         */
        fun getDatabase(context: Context, passphrase: CharArray): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context, passphrase)
                INSTANCE = instance
                instance
            }
        }

        /**
         * Build encrypted database instance
         */
        private fun buildDatabase(context: Context, passphrase: CharArray): AppDatabase {
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase))

            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(factory)
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Database created callback
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Enable foreign key constraints
                        db.execSQL("PRAGMA foreign_keys=ON")
                        // Set WAL mode via query (execSQL not supported for PRAGMA that return results)
                        db.query("PRAGMA journal_mode=WAL").close()
                        // Set synchronous mode for better performance
                        db.execSQL("PRAGMA synchronous=NORMAL")
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * Close database and clear instance
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }

        /**
         * Check if database exists
         */
        fun databaseExists(context: Context): Boolean {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            return dbFile.exists()
        }

        /**
         * Delete database file (for reset functionality)
         */
        fun deleteDatabase(context: Context): Boolean {
            closeDatabase()
            return context.deleteDatabase(DATABASE_NAME)
        }

        /**
         * Get database file size in bytes
         */
        fun getDatabaseSize(context: Context): Long {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            return if (dbFile.exists()) dbFile.length() else 0L
        }
    }
}
