package io.timestamp.app.capture

import io.timestamp.app.analysis.PatternAnalyzer
import io.timestamp.app.analysis.SpeedRange
import io.timestamp.app.data.InMemoryCaptureRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DebugSeedTest {
    @Test fun seedCreatesThreeMatchingRecordsAndOneUnmarkedRecordWithoutDuplicates() = runTest {
        val repository = InMemoryCaptureRepository()
        DebugSeed.insert(repository)
        DebugSeed.insert(repository)
        val records = repository.all()
        assertEquals(4, records.size)
        assertTrue(records.all { it.samples.map { sample -> sample.offsetSec }.toSet() == (-25..5).toSet() })
        assertEquals(1, records.count { it.bodyMarks.isEmpty() })
        assertEquals(4, records.map {
            Instant.ofEpochMilli(it.capture.createdAtMs).atZone(ZoneId.systemDefault()).toLocalDate()
        }.distinct().size)
        val group = PatternAnalyzer().analyze(records).single()
        assertEquals(3, group.count)
        assertEquals(SpeedRange(32, 44), group.speedRange)
        assertEquals(3, group.brakeRecordCount)
        assertEquals("D", group.gear)
        assertTrue(group.records.all { it.capture.memo == "끼익 소리가 나" })
    }
}
