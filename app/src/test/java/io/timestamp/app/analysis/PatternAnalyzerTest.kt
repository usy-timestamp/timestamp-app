package io.timestamp.app.analysis

import io.timestamp.app.data.*
import org.junit.Assert.*
import org.junit.Test

class PatternAnalyzerTest {
    private val analyzer = PatternAnalyzer()
    private fun record(id: String, parts: List<String> = listOf("앞 왼쪽 바퀴"),
                       samples: List<SampleEntity> = (-25..5).map { SampleEntity(id, it, 38f, false, "D") },
                       time: Long = 100_000L, status: String = "OPEN") = CaptureRecord(
        CaptureEntity(id, time, TriggerKind.DEBUG, "끼익 소리가 나", status = status), samples,
        parts.map { BodyMarkEntity(id, it) })

    @Test fun fewerThanTwoRecordsDoesNotMakeGroup() {
        assertTrue(analyzer.analyze(emptyList()).isEmpty())
        assertTrue(analyzer.analyze(listOf(record("a"))).isEmpty())
        assertTrue(analyzer.analyze(listOf(record("a"), record("a"))).isEmpty())
    }

    @Test fun differentOrUnmarkedPartsDoNotMakeGroup() {
        assertTrue(analyzer.analyze(listOf(record("a"), record("b", listOf("실내")), record("c", emptyList()))).isEmpty())
    }

    @Test fun speedUsesOnlyFiveSecondsBeforeTriggerAndRoundsOutward() {
        val a = record("a", samples = (-25..5).map { SampleEntity("a", it, if (it in -5..-1) 32.4f else 99f) })
        val b = record("b", samples = (-25..5).map { SampleEntity("b", it, if (it in -5..-1) 43.2f else 0f) })
        assertEquals(SpeedRange(32, 44), analyzer.analyze(listOf(a, b)).single().speedRange)
    }

    @Test fun brakeCountsRecordsWithAnyPressedSampleRatherThanSeconds() {
        val a = record("a", samples = (-25..5).map { SampleEntity("a", it, 38f, it == -2, "D") })
        val b = record("b", samples = (-25..5).map { SampleEntity("b", it, 38f, it == 0, "D") })
        val c = record("c", samples = (-25..5).map { SampleEntity("c", it, 38f, true, "D") })
        val group = analyzer.analyze(listOf(a, b, c)).single()
        assertEquals(2, group.brakeRecordCount)
        assertEquals(3, group.brakeKnownRecordCount)
    }

    @Test fun gearIsMostFrequentBeforeTriggerAndTieIsStable() {
        val a = record("a", samples = (-25..5).map { SampleEntity("a", it, gear = if (it in -5..-1) "D" else "P") })
        val b = record("b", samples = (-25..5).map { SampleEntity("b", it, gear = if (it in -5..-1) "N" else "P") })
        assertEquals("D", analyzer.analyze(listOf(a, b)).single().gear)
        assertEquals("D", analyzer.analyze(listOf(b, a)).single().gear)
        assertEquals("N", analyzer.analyze(listOf(a, b, record("c", samples = listOf(SampleEntity("c", -1, gear = "N"))))).single().gear)
    }

    @Test fun majorityBrakingSentence() {
        val values = listOf("a", "b", "c").map { id -> record(id, samples = (-5..-1).map { SampleEntity(id, it, if (it == -5) 30f else 45f, id != "c", "D") }) }
        assertEquals("시속 30~45km에서 브레이크를 밟을 때", analyzer.analyze(values).single().reproductionCondition)
    }

    @Test fun halfBrakingDoesNotCountAsMajority() {
        val values = listOf("a", "b").map { id -> record(id, samples = (-5..-1).map { SampleEntity(id, it, if (it == -5) 30f else 45f, id == "a", "D") }) }
        assertEquals("시속 30~45km로 달릴 때", analyzer.analyze(values).single().reproductionCondition)
    }

    @Test fun missingSpeedIsOmittedAndListed() {
        val values = listOf("a", "b").map { id -> record(id, samples = listOf(SampleEntity(id, -1, brakePressed = true, gear = "D"))) }
        val group = analyzer.analyze(values).single()
        assertNull(group.speedRange)
        assertEquals("브레이크를 밟을 때", group.reproductionCondition)
        assertEquals(listOf("속도", "외기온"), group.unavailableItems)
    }

    @Test fun allMissingAndNonFiniteValuesAreNotInvented() {
        val values = listOf("a", "b").map { id -> record(id, samples = listOf(SampleEntity(id, -1, Float.NaN, outsideTempC = Float.POSITIVE_INFINITY))) }
        val group = analyzer.analyze(values).single()
        assertNull(group.speedRange)
        assertNull(group.gear)
        assertEquals(0, group.brakeKnownRecordCount)
        assertEquals(listOf("속도", "브레이크", "기어", "외기온"), group.unavailableItems)
        assertFalse(group.reproductionCondition.contains("시속"))
        assertFalse(group.reproductionCondition.contains("브레이크"))
    }

    @Test fun partialMissingKeepsAvailableRangeAndDoesNotTreatUnknownBrakeAsPressed() {
        val a = record("a", samples = listOf(SampleEntity("a", -1, 38.5f, true, "D", outsideTempC = 25f)))
        val b = record("b", samples = listOf(SampleEntity("b", -1)))
        val group = analyzer.analyze(listOf(a, b)).single()
        assertEquals(SpeedRange(38, 39), group.speedRange)
        assertEquals(1, group.brakeRecordCount)
        assertEquals(1, group.brakeKnownRecordCount)
        assertTrue(group.unavailableItems.isEmpty())
        assertEquals("시속 38~39km로 달릴 때", group.reproductionCondition)
    }

    @Test fun multiPartRecordsJoinEachGroupOnlyOnce() {
        val values = listOf("a", "b").map { record(it, listOf("실내", "하부", "실내")) }
        val groups = analyzer.analyze(values)
        assertEquals(setOf("실내", "하부"), groups.map { it.part }.toSet())
        assertTrue(groups.all { it.count == 2 && it.name == "${it.part} 소음" })
    }

    @Test fun dashcamWindowsIncludeTriggerAndFiveSecondsBeforeAcrossMidnight() {
        val a = record("a", time = 2_000L)
        val b = record("b", time = 90_000L, status = "REPAIRED")
        val group = analyzer.analyze(listOf(a, b)).single()
        assertEquals(listOf(DashcamWindow("b", 85_000L, 90_000L), DashcamWindow("a", -3_000L, 2_000L)), group.dashcamWindows)
        assertEquals("REPAIRED", group.records.first().capture.status)
    }

    @Test fun legacyWheelNamesConvertForDisplayWithoutChangingStoredMarks() {
        val old = listOf("앞 왼쪽 바퀴", "앞 오른쪽 바퀴", "뒤 왼쪽 바퀴", "뒤 오른쪽 바퀴")
        val current = listOf("왼쪽 앞바퀴", "오른쪽 앞바퀴", "왼쪽 뒷바퀴", "오른쪽 뒷바퀴")
        assertEquals(current, old.map(BodyParts::displayName))
        (current + listOf("엔진룸", "실내", "하부", "잘 모르겠어요", "기타")).forEach {
            assertEquals(it, BodyParts.displayName(it))
        }
        val original = record("old", old)
        val groups = analyzer.analyze(listOf(original, record("new", current)))
        assertEquals(current.toSet(), groups.map { it.part }.toSet())
        assertTrue(groups.all { it.count == 2 })
        assertEquals(old, original.bodyMarks.map { it.part })
    }

    @Test fun oldAndNewNamesInOneRecordDoNotDoubleCount() {
        val group = analyzer.analyze(listOf(record("a", listOf("앞 왼쪽 바퀴", "왼쪽 앞바퀴")),
            record("b", listOf("왼쪽 앞바퀴")))).single()
        assertEquals(2, group.count)
        assertEquals("왼쪽 앞바퀴 소음", group.name)
    }

    @Test fun unknownLocationHasItsOwnSymptomName() {
        val values = listOf("a", "b").map { record(it, listOf("잘 모르겠어요")) }
        assertEquals("부위를 모르는 소음", analyzer.analyze(values).single().name)
    }

    @Test fun exactSpeedUsesOneNumberWithAndWithoutMajorityBraking() {
        val noBrake = listOf(record("a"), record("b"))
        val group = analyzer.analyze(noBrake).single()
        assertEquals("시속 38km", group.speedRange!!.displayText)
        assertEquals("시속 38km에서 달릴 때", group.reproductionCondition)
        assertEquals("2번 중 0번 밟음", group.brakeText)
        val brake = noBrake.map { record -> record.copy(samples = record.samples.map { it.copy(brakePressed = true) }) }
        val braking = analyzer.analyze(brake).single()
        assertEquals("시속 38km에서 브레이크를 밟을 때", braking.reproductionCondition)
        assertEquals("2번 모두 밟음", braking.brakeText)
    }

    @Test fun rangeAndBrakeValuesUseReportWording() {
        val values = listOf("a", "b", "c").map { id -> record(id, samples = (-5..-1).map {
            SampleEntity(id, it, if (it == -5) 31f else 42f, id != "c", "D")
        }) }
        val group = analyzer.analyze(values).single()
        assertEquals("시속 31~42km", group.speedRange!!.displayText)
        assertEquals("시속 31~42km에서 브레이크를 밟을 때", group.reproductionCondition)
        assertEquals("3번 중 2번 밟음", group.brakeText)
        val all = values.map { r -> r.copy(samples = r.samples.map { it.copy(brakePressed = true) }) }
        assertEquals("3번 모두 밟음", analyzer.analyze(all).single().brakeText)
    }

    @Test fun missingBrakeHasNoInventedDisplayValue() {
        val values = listOf("a", "b").map { record(it, samples = listOf(SampleEntity(it, -1))) }
        assertNull(analyzer.analyze(values).single().brakeText)
    }
}
