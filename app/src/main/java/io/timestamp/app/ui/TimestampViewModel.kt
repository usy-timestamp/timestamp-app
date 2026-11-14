package io.timestamp.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.timestamp.app.analysis.PatternAnalyzer
import io.timestamp.app.analysis.SymptomGroup
import io.timestamp.app.data.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AppState(val records: List<CaptureRecord> = emptyList(), val groups: List<SymptomGroup> = emptyList(),
                    val settings: AppSettingsEntity? = null)

class TimestampViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CaptureRepository = RoomCaptureRepository(AppDatabase.get(application).captureDao())
    private val analyzer = PatternAnalyzer()
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val state = combine(repository.observeRecords(), repository.observeSettings()) { records, settings ->
        AppState(records, analyzer.analyze(records), settings)
    }.catch { e ->
        if (e is CancellationException) throw e
        error.value = "저장된 기록을 읽지 못했어요. 앱을 다시 열어 주세요."
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppState())

    private fun mutate(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { action() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error.value = "저장하지 못했어요. 다시 시도해 주세요." }
            finally { busy.value = false }
        }
    }

    fun finishOnboarding(permissions: PermissionState, consent: Boolean, done: () -> Unit) = mutate {
        require(permissions.requiredGranted && consent)
        // 서버 전송 동의와 구분하기 위해 첫 실행 완료와 기존 동의 시각을 함께 저장한다.
        repository.saveSettings(repository.settings().copy(onboardingCompleted = true, consentAtMs = System.currentTimeMillis(),
            microphoneGranted = permissions.microphone, carSpeedGranted = permissions.carSpeed,
            carPowertrainGranted = permissions.carPowertrain))
        done()
    }

    fun setVoiceEnabled(enabled: Boolean) = mutate {
        repository.saveSettings(repository.settings().copy(voiceTriggerEnabled = enabled))
    }

    fun setMemoInputMode(mode: io.timestamp.app.data.MemoInputMode) = mutate {
        repository.saveSettings(repository.settings().copy(memoInputMode = mode.name))
    }

    fun saveParts(id: String, parts: Set<String>, done: () -> Unit) = mutate {
        repository.saveBodyMarks(id, parts)
        done()
    }

    fun deleteAll(done: () -> Unit) = mutate {
        repository.deleteAll()
        done()
    }

    fun delete(id: String, done: () -> Unit) = mutate {
        repository.delete(id)
        done()
    }

    fun completeRepair(group: SymptomGroup, date: Long, memo: String?, done: () -> Unit) = mutate {
        val settings = repository.settings()
        repository.completeRepair(RepairEventEntity(UUID.randomUUID().toString(), date, memo,
            reminderEnabled(settings.testDriveReminderParts, group.part)), group.records.map { it.capture.id })
        done()
    }

    fun toggleReminder(part: String) = mutate {
        val saved = repository.settings()
        val parts = saved.testDriveReminderParts.split('|').filter(String::isNotBlank).map(io.timestamp.app.analysis.BodyParts::displayName).toSet()
        repository.saveSettings(saved.copy(testDriveReminderParts =
            (if (part in parts) parts - part else parts + part).joinToString("|")))
    }
}
