package io.timestamp.app.data

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

object TriggerKind {
    const val VOICE = "VOICE"
    const val DEBUG = "DEBUG"
    const val WARNING_LIGHT = "WARNING_LIGHT"
    const val GLEO = "GLEO"
    val values = setOf(VOICE, DEBUG, WARNING_LIGHT, GLEO)
}

@Entity(tableName = "captures")
data class CaptureEntity(
    @PrimaryKey val id: String,
    val createdAtMs: Long,
    val trigger: String,
    val memo: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = "OPEN",
) {
    init {
        require(trigger in TriggerKind.values)
        require(status in setOf("OPEN", "REPAIRED", "RESOLVED", "RECURRED"))
    }
}

@Entity(
    tableName = "samples", primaryKeys = ["captureId", "offsetSec"],
    foreignKeys = [ForeignKey(
        entity = CaptureEntity::class, parentColumns = ["id"], childColumns = ["captureId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class SampleEntity(
    val captureId: String,
    val offsetSec: Int,
    val speedKmh: Float? = null,
    val brakePressed: Boolean? = null,
    val gear: String? = null,
    val warningLights: String = "",
    val outsideTempC: Float? = null,
) {
    init { require(offsetSec in -25..5) }
}

@Entity(
    tableName = "body_marks", primaryKeys = ["captureId", "part"],
    foreignKeys = [ForeignKey(
        entity = CaptureEntity::class, parentColumns = ["id"], childColumns = ["captureId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class BodyMarkEntity(val captureId: String, val part: String)

@Entity(tableName = "repair_events")
data class RepairEventEntity(
    @PrimaryKey val id: String,
    val repairedAtMs: Long,
    val memo: String? = null,
    val testDriveReminderEnabled: Boolean = false,
)

@Entity(
    tableName = "repair_captures", primaryKeys = ["repairEventId", "captureId"],
    indices = [Index("captureId")],
    foreignKeys = [
        ForeignKey(entity = RepairEventEntity::class, parentColumns = ["id"],
            childColumns = ["repairEventId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CaptureEntity::class, parentColumns = ["id"],
            childColumns = ["captureId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class RepairCaptureEntity(val repairEventId: String, val captureId: String)

@Entity(
    tableName = "recheck_events",
    indices = [Index("repairEventId"), Index("newCaptureId")],
    foreignKeys = [
        ForeignKey(entity = RepairEventEntity::class, parentColumns = ["id"],
            childColumns = ["repairEventId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CaptureEntity::class, parentColumns = ["id"],
            childColumns = ["newCaptureId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class RecheckEventEntity(
    @PrimaryKey val id: String,
    val repairEventId: String,
    val createdAtMs: Long,
    val speedKmh: Float?,
    val brakePressed: Boolean?,
    val answer: String,
    val newCaptureId: String? = null,
) {
    init { require(answer in setOf("NO", "YES", "NO_RESPONSE")) }
}

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val dataSharingConsent: Boolean = false,
    val consentAtMs: Long? = null,
    val announceCapture: Boolean = true,
    val recheckEnabled: Boolean = true,
    val voiceTriggerEnabled: Boolean = true,
    val microphoneGranted: Boolean = false,
    val carSpeedGranted: Boolean = false,
    val carPowertrainGranted: Boolean = false,
    val locationGranted: Boolean = false,
    @ColumnInfo(defaultValue = "'ASK_AFTER_RECORDING'") val memoInputMode: String = MemoInputMode.ASK_AFTER_RECORDING.name,
    @ColumnInfo(defaultValue = "0") val onboardingCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "''") val testDriveReminderParts: String = "",
) {
    init { require(id == 1) }
}

data class CaptureRecord(
    @Embedded val capture: CaptureEntity,
    @Relation(parentColumn = "id", entityColumn = "captureId") val samples: List<SampleEntity>,
    @Relation(parentColumn = "id", entityColumn = "captureId") val bodyMarks: List<BodyMarkEntity> = emptyList(),
)
