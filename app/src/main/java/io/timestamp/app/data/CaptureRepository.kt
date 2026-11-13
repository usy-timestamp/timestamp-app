package io.timestamp.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CaptureRepository {
    suspend fun save(capture: CaptureEntity, samples: List<SampleEntity>)
    suspend fun updateMemo(captureId: String, memo: String): Boolean
    suspend fun count(): Int
    suspend fun recent(limit: Int = 3): List<CaptureRecord>
    suspend fun all(): List<CaptureRecord>
    suspend fun find(id: String): CaptureRecord?
    fun observeRecords(): Flow<List<CaptureRecord>>
    suspend fun bodyMarks(captureId: String): List<BodyMarkEntity>
    suspend fun saveBodyMarks(captureId: String, parts: Set<String>)
    suspend fun deleteAll()
    suspend fun delete(id: String)
    suspend fun completeRepair(event: RepairEventEntity, captureIds: List<String>)
    suspend fun repairs(): List<RepairEventEntity>
    suspend fun repairCaptureIds(repairEventId: String): List<String>
    suspend fun settings(): AppSettingsEntity
    suspend fun saveSettings(settings: AppSettingsEntity)
    fun observeSettings(): Flow<AppSettingsEntity>
}

class RoomCaptureRepository(private val dao: CaptureDao) : CaptureRepository {
    override suspend fun save(capture: CaptureEntity, samples: List<SampleEntity>) = dao.save(capture, samples)
    override suspend fun updateMemo(captureId: String, memo: String) = dao.updateMemo(captureId, memo) == 1
    override suspend fun count() = dao.count()
    override suspend fun recent(limit: Int) = dao.recent(limit)
    override suspend fun all() = dao.all()
    override suspend fun find(id: String) = dao.find(id)
    override fun observeRecords() = dao.observeRecords()
    override suspend fun bodyMarks(captureId: String) = dao.bodyMarks(captureId)
    override suspend fun saveBodyMarks(captureId: String, parts: Set<String>) = dao.replaceBodyMarks(captureId, parts)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun completeRepair(event: RepairEventEntity, captureIds: List<String>) = dao.completeRepair(event, captureIds)
    override suspend fun repairs() = dao.repairs()
    override suspend fun repairCaptureIds(repairEventId: String) = dao.repairCaptureIds(repairEventId)
    override suspend fun settings() = dao.settings() ?: AppSettingsEntity()
    override suspend fun saveSettings(settings: AppSettingsEntity) = dao.saveSettings(settings)
    override fun observeSettings() = dao.observeSettings().map { it ?: AppSettingsEntity() }
}

class InMemoryCaptureRepository : CaptureRepository {
    private val mutex = Mutex()
    private val records = linkedMapOf<String, CaptureRecord>()
    private val preferences = MutableStateFlow(AppSettingsEntity())
    private val changes = MutableStateFlow<List<CaptureRecord>>(emptyList())
    private val repairEvents = linkedMapOf<String, RepairEventEntity>()
    private val repairLinks = linkedMapOf<String, List<String>>()

    private fun publish() {
        changes.value = records.values.sortedWith(compareByDescending<CaptureRecord> { it.capture.createdAtMs }
            .thenByDescending { it.capture.id })
    }

    override suspend fun save(capture: CaptureEntity, samples: List<SampleEntity>) {
        mutex.withLock {
            require(capture.id !in records)
            require(samples.all { it.captureId == capture.id })
            require(samples.map { it.offsetSec }.distinct().size == samples.size)
            records[capture.id] = CaptureRecord(capture, samples.toList())
            publish()
        }
    }
    override suspend fun updateMemo(captureId: String, memo: String): Boolean = mutex.withLock {
        val record = records[captureId] ?: return@withLock false
        records[captureId] = record.copy(capture = record.capture.copy(memo = memo))
        publish()
        true
    }
    override suspend fun all() = mutex.withLock { changes.value.toList() }
    override suspend fun find(id: String) = mutex.withLock { records[id] }
    override fun observeRecords(): Flow<List<CaptureRecord>> = changes
    override suspend fun bodyMarks(captureId: String) = mutex.withLock { records[captureId]?.bodyMarks.orEmpty() }
    override suspend fun saveBodyMarks(captureId: String, parts: Set<String>) {
        mutex.withLock {
            val record = requireNotNull(records[captureId])
            records[captureId] = record.copy(bodyMarks = parts.map { BodyMarkEntity(captureId, it) })
            publish()
        }
    }
    override suspend fun deleteAll() {
        mutex.withLock {
            records.clear()
            repairEvents.clear()
            repairLinks.clear()
            preferences.value = preferences.value.copy(testDriveReminderParts = "")
            publish()
        }
    }
    override suspend fun delete(id: String) {
        mutex.withLock {
            records.remove(id)
            repairLinks.replaceAll { _, ids -> ids.filterNot { it == id } }
            publish()
        }
    }
    override suspend fun completeRepair(event: RepairEventEntity, captureIds: List<String>) {
        mutex.withLock {
            require(captureIds.isNotEmpty() && captureIds.distinct().size == captureIds.size)
            require(captureIds.all { it in records })
            repairEvents[event.id] = event
            repairLinks[event.id] = captureIds.toList()
            captureIds.forEach { id ->
                val record = records.getValue(id)
                records[id] = record.copy(capture = record.capture.copy(status = "REPAIRED"))
            }
            publish()
        }
    }
    override suspend fun repairs() = mutex.withLock { repairEvents.values.sortedByDescending { it.repairedAtMs } }
    override suspend fun repairCaptureIds(repairEventId: String) = mutex.withLock { repairLinks[repairEventId].orEmpty().toList() }
    override suspend fun count() = mutex.withLock { records.size }
    override suspend fun recent(limit: Int): List<CaptureRecord> = mutex.withLock {
        require(limit >= 0)
        records.values.sortedWith(compareByDescending<CaptureRecord> { it.capture.createdAtMs }
            .thenByDescending { it.capture.id }).take(limit)
    }
    override suspend fun settings() = preferences.value
    override suspend fun saveSettings(settings: AppSettingsEntity) { preferences.value = settings }
    override fun observeSettings(): Flow<AppSettingsEntity> = preferences
}
