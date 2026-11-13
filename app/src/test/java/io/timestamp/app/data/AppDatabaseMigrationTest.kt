package io.timestamp.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class AppDatabaseMigrationTest {
    private fun database(statements: MutableList<String>) = Proxy.newProxyInstance(
        SupportSQLiteDatabase::class.java.classLoader, arrayOf(SupportSQLiteDatabase::class.java),
    ) { _, method, arguments ->
        check(method.name == "execSQL")
        statements.add(arguments!![0] as String)
        null
    } as SupportSQLiteDatabase

    @Test fun versionTwoKeepsEveryTableAndAddsAskModeAsDefault() {
        val statements = mutableListOf<String>()
        val migration = AppDatabase.MIGRATION_2_3
        assertEquals(2, migration.startVersion)
        assertEquals(3, migration.endVersion)
        migration.migrate(database(statements))
        assertEquals(listOf("ALTER TABLE app_settings ADD COLUMN memoInputMode TEXT NOT NULL DEFAULT 'ASK_AFTER_RECORDING'"), statements)
    }

    @Test fun versionOneCanUpgradeThroughBothPreservingMigrations() {
        val statements = mutableListOf<String>()
        val db = database(statements)
        AppDatabase.MIGRATION_1_2.migrate(db)
        AppDatabase.MIGRATION_2_3.migrate(db)
        assertEquals(3, statements.size)
        assertTrue(statements.all { it.startsWith("ALTER TABLE app_settings ADD COLUMN ") })
        assertTrue(statements.none { it.contains("DROP") || it.contains("DELETE") })
    }
}
