package io.timestamp.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CaptureEntity::class, SampleEntity::class, BodyMarkEntity::class,
        RepairEventEntity::class, RepairCaptureEntity::class, RecheckEventEntity::class,
        AppSettingsEntity::class],
    version = 3, exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao

    companion object {
        // 이미 저장한 기록을 보존하면서 첫 실행과 알림 설정만 더한다.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN onboardingCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_settings ADD COLUMN testDriveReminderParts TEXT NOT NULL DEFAULT ''")
            }
        }
        // 새 입력 방식만 더해 예전 기록과 동의 설정을 그대로 보존한다.
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_settings ADD COLUMN memoInputMode TEXT NOT NULL DEFAULT 'ASK_AFTER_RECORDING'")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, "timestamp.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
