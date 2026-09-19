package com.example.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppDatabaseMigrationTest {
    @Test
    fun migration2To3_preservesRowsAndAddsIntelligenceColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-2-3.db"
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        """CREATE TABLE notes (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            type TEXT NOT NULL,
                            title TEXT NOT NULL,
                            content TEXT NOT NULL,
                            audioFilePath TEXT,
                            durationSeconds INTEGER NOT NULL,
                            imageUri TEXT,
                            url TEXT,
                            urlDescription TEXT,
                            tags TEXT NOT NULL,
                            createdAt INTEGER NOT NULL,
                            updatedAt INTEGER NOT NULL,
                            isPinned INTEGER NOT NULL,
                            followUpAt INTEGER,
                            isFollowUpDone INTEGER NOT NULL
                        )""".trimIndent()
                    )
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO notes(type,title,content,durationSeconds,tags,createdAt,updatedAt,isPinned,isFollowUpDone) VALUES('TEXT','keep me','body',0,'',1,1,0,0)")
        AppDatabase.MIGRATION_2_3.migrate(db)
        db.query("SELECT title, summary, category, isImportant FROM notes").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("keep me", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals("", c.getString(2))
            assertEquals(0, c.getInt(3))
        }
        helper.close()
        context.deleteDatabase(name)
    }
}
