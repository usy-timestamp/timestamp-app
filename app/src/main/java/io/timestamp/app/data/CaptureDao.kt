package io.timestamp.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CaptureDao {
    @Insert abstract suspend fun insertCapture(capture: CaptureEntity)
    @Insert abstract suspend fun insertSamples(samples: List<SampleEntity>)

    @Transaction
    open suspend fun save(capture: CaptureEntity, samples: List<SampleEntity>) {
        require(samples.all { it.captureId == capture.id })
        require(samples.map { it.offsetSec }.distinct().size == samples.size)
        insertCapture(capture)
        insertSamples(samples)
    }

    @Query("SELECT COUNT(*) FROM captures") abstract suspend fun count(): Int
    @Transaction
    @Query("SELECT * FROM captures ORDER BY createdAtMs DESC, id DESC LIMIT :limit")
    abstract suspend fun recent(limit: Int): List<CaptureRecord>
    @Transaction
    @Query("SELECT * FROM captures WHERE id = :id")
    abstract suspend fun find(id: String): CaptureRecord?
    @Transaction
    @Query("SELECT * FROM captures ORDER BY createdAtMs DESC, id DESC")
    abstract suspend fun all(): List<CaptureRecord>
    @Transaction
    @Query("SELECT * FROM captures ORDER BY createdAtMs DESC, id DESC")
    abstract fun observeRecords(): Flow<List<CaptureRecord>>
    @Query("UPDATE captures SET memo = :memo WHERE id = :captureId")
    abstract suspend fun updateMemo(captureId: String, memo: String): Int

    @Query("DELETE FROM captures WHERE id = :id") abstract suspend fun delete(id: String)

    @Query("DELETE FROM captures") abstract suspend fun deleteCaptures()
    @Query("DELETE FROM repair_events") abstract suspend fun deleteRepairs()

    // 수리 이력과 확인 이력도 함께 지워 기록의 흔적이 남지 않게 한다.
    @Transaction
    open suspend fun deleteAll() {
        deleteRepairs()
        deleteCaptures()
        settings()?.let { saveSettings(it.copy(testDriveReminderParts = "")) }
    }

    @Upsert abstract suspend fun saveBodyMarks(marks: List<BodyMarkEntity>)
    @Query("SELECT * FROM body_marks WHERE captureId = :captureId")
    abstract suspend fun bodyMarks(captureId: String): List<BodyMarkEntity>
    @Query("DELETE FROM body_marks WHERE captureId = :captureId")
    abstract suspend fun deleteBodyMarks(captureId: String)

    @Transaction
    open suspend fun replaceBodyMarks(captureId: String, parts: Set<String>) {
        require(find(captureId) != null)
        deleteBodyMarks(captureId)
        saveBodyMarks(parts.map { BodyMarkEntity(captureId, it) })
    }

    @Query("UPDATE captures SET status = 'REPAIRED' WHERE id IN (:ids)")
    abstract suspend fun markRepaired(ids: List<String>)

    @Transaction
    open suspend fun completeRepair(event: RepairEventEntity, ids: List<String>) {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size)
        require(ids.all { find(it) != null })
        saveRepair(event)
        saveRepairCaptures(ids.map { RepairCaptureEntity(event.id, it) })
        markRepaired(ids)
    }

    @Upsert abstract suspend fun saveRepair(event: RepairEventEntity)
    @Upsert abstract suspend fun saveRepairCaptures(captures: List<RepairCaptureEntity>)
    @Query("SELECT * FROM repair_events ORDER BY repairedAtMs DESC")
    abstract suspend fun repairs(): List<RepairEventEntity>
    @Query("SELECT captureId FROM repair_captures WHERE repairEventId = :repairEventId")
    abstract suspend fun repairCaptureIds(repairEventId: String): List<String>
    @Upsert abstract suspend fun saveRecheck(event: RecheckEventEntity)
    @Query("SELECT * FROM recheck_events WHERE repairEventId = :repairEventId ORDER BY createdAtMs")
    abstract suspend fun rechecks(repairEventId: String): List<RecheckEventEntity>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    abstract fun observeSettings(): Flow<AppSettingsEntity?>
    @Query("SELECT * FROM app_settings WHERE id = 1")
    abstract suspend fun settings(): AppSettingsEntity?
    @Upsert abstract suspend fun saveSettings(settings: AppSettingsEntity)
}
