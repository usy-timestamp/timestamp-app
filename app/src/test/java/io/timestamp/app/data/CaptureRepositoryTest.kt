package io.timestamp.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CaptureRepositoryTest {
    private suspend fun add(repo: CaptureRepository, id: String, time: Long = 0L) {
        repo.save(CaptureEntity(id, time, TriggerKind.DEBUG), (-25..5).map { SampleEntity(id, it) })
    }

    @Test fun memoUpdatePreservesSignalsMarksAndRepairState() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a"); add(repo, "b")
        repo.saveBodyMarks("a", setOf("실내"))
        repo.completeRepair(RepairEventEntity("repair", 100L), listOf("a"))
        val before = repo.find("a")!!
        assertTrue(repo.updateMemo("a", "오른쪽 뒤에서 드르륵 소리가 나"))
        assertEquals(before.copy(capture = before.capture.copy(memo = "오른쪽 뒤에서 드르륵 소리가 나")), repo.find("a"))
        assertNull(repo.find("b")!!.capture.memo)
        assertEquals(repo.all(), repo.observeRecords().first())
        assertEquals(listOf("a"), repo.repairCaptureIds("repair"))
    }

    @Test fun replyingToADeletedRecordDoesNotRecreateIt() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a")
        repo.deleteAll()
        assertFalse(repo.updateMemo("a", "늦은 답"))
        assertEquals(0, repo.count())
    }

    @Test fun memoModeDefaultsToAskAndSurvivesOtherSettingsAndRecordDeletion() = runTest {
        val repo = InMemoryCaptureRepository()
        assertEquals(MemoInputMode.ASK_AFTER_RECORDING.name, repo.settings().memoInputMode)
        repo.saveSettings(repo.settings().copy(memoInputMode = MemoInputMode.ONE_SHOT.name))
        repo.saveSettings(repo.settings().copy(onboardingCompleted = true, consentAtMs = 123L))
        repo.deleteAll()
        assertEquals(MemoInputMode.ONE_SHOT.name, repo.observeSettings().first().memoInputMode)
        assertEquals(123L, repo.settings().consentAtMs)
    }

    @Test fun queriesAndObservationIncludeReplacedBodyMarks() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a"); add(repo, "b", 1)
        repo.saveBodyMarks("a", setOf("실내", "하부"))
        assertEquals(listOf("b", "a"), repo.all().map { it.capture.id })
        assertEquals(setOf("실내", "하부"), repo.find("a")!!.bodyMarks.map { it.part }.toSet())
        repo.saveBodyMarks("a", setOf("엔진룸"))
        assertEquals(listOf("엔진룸"), repo.bodyMarks("a").map { it.part })
        assertEquals(repo.all(), repo.observeRecords().first())
        repo.saveBodyMarks("a", emptySet())
        assertTrue(repo.find("a")!!.bodyMarks.isEmpty())
    }

    @Test fun newBodyPartsAreSavedAndReplacedWithoutChangingOtherRecords() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "new"); add(repo, "existing")
        val added = setOf("대시보드", "왼쪽 문", "오른쪽 문", "트렁크", "지붕")
        val existing = setOf("앞 왼쪽 바퀴", "엔진룸", "실내", "하부")
        repo.saveBodyMarks("existing", existing)
        repo.saveBodyMarks("new", added)
        assertEquals(added, repo.find("new")!!.bodyMarks.map { it.part }.toSet())
        assertEquals(added, repo.observeRecords().first().single { it.capture.id == "new" }
            .bodyMarks.map { it.part }.toSet())
        repo.saveBodyMarks("new", setOf("트렁크"))
        assertEquals(listOf("트렁크"), repo.bodyMarks("new").map { it.part })
        assertEquals(existing, repo.find("existing")!!.bodyMarks.map { it.part }.toSet())
    }

    @Test fun repairSavesLinksAndOnlyChangesSelectedRecords() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a"); add(repo, "b"); add(repo, "c")
        val repair = RepairEventEntity("repair", 100L, "패드 교체", true)
        repo.completeRepair(repair, listOf("a", "b"))
        assertEquals(listOf(repair), repo.repairs())
        assertEquals(listOf("a", "b"), repo.repairCaptureIds("repair"))
        assertEquals("REPAIRED", repo.find("a")!!.capture.status)
        assertEquals("REPAIRED", repo.find("b")!!.capture.status)
        assertEquals("OPEN", repo.find("c")!!.capture.status)
        assertEquals(repo.all(), repo.observeRecords().first())
    }

    @Test fun invalidRepairDoesNotSaveHalfOfTheOperation() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a")
        try {
            repo.completeRepair(RepairEventEntity("repair", 100L), listOf("a", "missing"))
            fail("없는 기록이 있으면 저장을 막아야 한다")
        } catch (_: IllegalArgumentException) { }
        assertTrue(repo.repairs().isEmpty())
        assertEquals("OPEN", repo.find("a")!!.capture.status)
    }

    @Test fun deletingRecordAlsoRemovesMarksSamplesAndRepairLinks() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a")
        repo.saveBodyMarks("a", setOf("실내"))
        repo.completeRepair(RepairEventEntity("repair", 100L), listOf("a"))
        repo.delete("a")
        assertNull(repo.find("a"))
        assertTrue(repo.bodyMarks("a").isEmpty())
        assertTrue(repo.repairCaptureIds("repair").isEmpty())
        assertTrue(repo.observeRecords().first().isEmpty())
    }

    @Test fun onboardingAndReminderSettingsAreRetained() = runTest {
        val repo = InMemoryCaptureRepository()
        assertFalse(repo.settings().onboardingCompleted)
        repo.saveSettings(repo.settings().copy(onboardingCompleted = true, testDriveReminderParts = "실내|하부"))
        assertTrue(repo.observeSettings().first().onboardingCompleted)
        assertEquals("실내|하부", repo.settings().testDriveReminderParts)
    }

    @Test fun deleteAllClearsRecordsAndRepairHistoryButKeepsConsent() = runTest {
        val repo = InMemoryCaptureRepository()
        add(repo, "a"); add(repo, "b")
        repo.saveBodyMarks("a", setOf("실내"))
        repo.completeRepair(RepairEventEntity("repair", 100L, "패드 교체"), listOf("a"))
        val settings = AppSettingsEntity(onboardingCompleted = true, consentAtMs = 123L,
            microphoneGranted = true, testDriveReminderParts = "실내")
        repo.saveSettings(settings)
        repo.deleteAll()
        repo.deleteAll()
        assertEquals(0, repo.count())
        assertTrue(repo.all().isEmpty())
        assertTrue(repo.observeRecords().first().isEmpty())
        assertNull(repo.find("a"))
        assertTrue(repo.bodyMarks("a").isEmpty())
        assertTrue(repo.repairs().isEmpty())
        assertTrue(repo.repairCaptureIds("repair").isEmpty())
        assertEquals(settings.copy(testDriveReminderParts = ""), repo.settings())
        add(repo, "new")
        assertEquals(1, repo.count())
    }
}
