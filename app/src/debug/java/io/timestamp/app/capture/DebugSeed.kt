package io.timestamp.app.capture

import io.timestamp.app.data.CaptureEntity
import io.timestamp.app.data.CaptureRepository
import io.timestamp.app.data.SampleEntity
import io.timestamp.app.data.TriggerKind
import java.time.LocalDate
import java.time.ZoneId

internal object DebugSeed {
    suspend fun insert(repository: CaptureRepository) {
        repeat(4) { index ->
            val id = "m2-example-$index"
            if (repository.find(id) == null) {
                val time = LocalDate.now().minusDays(index.toLong()).atTime(18, 42, 10)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                repository.save(CaptureEntity(id, time, TriggerKind.DEBUG, "끼익 소리가 나"),
                    (-25..5).map { offset ->
                        SampleEntity(id, offset, speedKmh = when (offset) {
                            in -5..-1 -> 44f - (offset + 5) * 3f
                            in 0..5 -> 32f - offset
                            else -> 52f - (offset + 25) * 0.4f
                        }, brakePressed = offset >= -5, gear = "D")
                    })
                if (index < 3) repository.saveBodyMarks(id, setOf("왼쪽 앞바퀴"))
            }
        }
    }
}
