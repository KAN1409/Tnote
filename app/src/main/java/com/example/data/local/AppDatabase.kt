package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.NoteEntity

@Database(entities = [NoteEntity::class], version = 4, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "voice_notes_db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN followUpAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN isFollowUpDone INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isImportant INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN summary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val textColumns = listOf("rawTranscript", "cleanedTranscript", "transcriptionProvider", "transcriptionModel", "detectedLanguage", "transcriptionStatus", "transcriptionFailureReason", "rawOcrText", "normalizedOcrText", "ocrProvider", "ocrModel", "detectedLanguages", "ocrGeometryJson", "ocrStatus", "ocrFailureReason")
                textColumns.forEach { db.execSQL("ALTER TABLE notes ADD COLUMN $it TEXT NOT NULL DEFAULT ''") }
                listOf("transcriptionTimestamp", "ocrTimestamp").forEach { db.execSQL("ALTER TABLE notes ADD COLUMN $it INTEGER") }
                listOf("transcriptionRetryCount", "ocrRetryCount").forEach { db.execSQL("ALTER TABLE notes ADD COLUMN $it INTEGER NOT NULL DEFAULT 0") }
                db.execSQL("ALTER TABLE notes ADD COLUMN providerConfidence REAL")
            }
        }
    }
}
