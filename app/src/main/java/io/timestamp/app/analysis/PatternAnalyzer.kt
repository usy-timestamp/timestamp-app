package io.timestamp.app.analysis

import io.timestamp.app.data.CaptureRecord
import kotlin.math.ceil
import kotlin.math.floor

object BodyParts {
    val touchParts = listOf("왼쪽 앞바퀴", "오른쪽 앞바퀴", "왼쪽 뒷바퀴", "오른쪽 뒷바퀴", "엔진룸", "실내")
    val extraParts = listOf("하부", "잘 모르겠어요")
    val all = touchParts + extraParts

    // 예전 기록을 그대로 두면서 같은 부위끼리 묶고 같은 이름으로 보여 준다.
    fun displayName(part: String) = when (part) {
        "앞 왼쪽 바퀴" -> "왼쪽 앞바퀴"
        "앞 오른쪽 바퀴" -> "오른쪽 앞바퀴"
        "뒤 왼쪽 바퀴" -> "왼쪽 뒷바퀴"
        "뒤 오른쪽 바퀴" -> "오른쪽 뒷바퀴"
        else -> part
    }
}

data class SpeedRange(val minimum: Int, val maximum: Int) {
    val displayText get() = "시속 " + (if (minimum == maximum) "$minimum" else "$minimum~$maximum") + "km"
}
data class DashcamWindow(val captureId: String, val fromMs: Long, val toMs: Long)

data class SymptomGroup(
    val part: String,
    val records: List<CaptureRecord>,
    val speedRange: SpeedRange?,
    val brakeRecordCount: Int,
    val brakeKnownRecordCount: Int,
    val gear: String?,
    val dashcamWindows: List<DashcamWindow>,
    val unavailableItems: List<String>,
    val reproductionCondition: String,
) {
    val name: String get() = if (part == "잘 모르겠어요") "부위를 모르는 소음" else "${BodyParts.displayName(part)} 소음"
    val count: Int get() = records.size
    val brakeText: String? get() = when {
        brakeKnownRecordCount == 0 -> null
        brakeRecordCount == count -> "${count}번 모두 밟음"
        else -> "${count}번 중 ${brakeRecordCount}번 밟음"
    }
}

class PatternAnalyzer {
    fun analyze(records: List<CaptureRecord>): List<SymptomGroup> = records.distinctBy { it.capture.id }
        .flatMap { record -> record.bodyMarks.map { BodyParts.displayName(it.part) }.distinct().map { it to record } }
        .groupBy({ it.first }, { it.second })
        .filterValues { it.size >= 2 }
        .map { (part, values) -> summarize(part, values.sortedByDescending { it.capture.createdAtMs }) }
        .sortedWith(compareByDescending<SymptomGroup> { it.count }.thenBy { it.part })

    private fun summarize(part: String, records: List<CaptureRecord>): SymptomGroup {
        val before = records.map { record -> record.samples.filter { it.offsetSec in -5..-1 } }
        val speeds = before.flatten().mapNotNull { it.speedKmh?.takeIf(Float::isFinite) }
        val range = if (speeds.isEmpty()) null else SpeedRange(
            floor(speeds.min().toDouble()).toInt(), ceil(speeds.max().toDouble()).toInt(),
        )
        val brakes = before.count { samples -> samples.any { it.brakePressed == true } }
        val brakeKnown = before.count { samples -> samples.any { it.brakePressed != null } }
        val gears = before.flatten().mapNotNull { it.gear?.takeIf(String::isNotBlank) }
        // 동률이면 이름순으로 골라 같은 입력에는 항상 같은 결과를 낸다.
        val gear = gears.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key
        val missing = buildList {
            if (range == null) add("속도")
            if (brakeKnown == 0) add("브레이크")
            if (gear == null) add("기어")
            if (before.flatten().none { it.outsideTempC?.isFinite() == true }) add("외기온")
        }
        val braking = brakes > records.size / 2
        val condition = when {
            range != null && braking -> "${range.displayText}에서 브레이크를 밟을 때"
            range != null -> if (range.minimum == range.maximum) "${range.displayText}에서 달릴 때" else "${range.displayText}로 달릴 때"
            braking -> "브레이크를 밟을 때"
            else -> "재현 조건을 찾기 위한 차량 정보가 부족해요"
        }
        return SymptomGroup(part, records, range, brakes, brakeKnown, gear,
            records.map { DashcamWindow(it.capture.id, it.capture.createdAtMs - 5000, it.capture.createdAtMs) },
            missing, condition)
    }
}
