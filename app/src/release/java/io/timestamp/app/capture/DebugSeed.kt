package io.timestamp.app.capture

import io.timestamp.app.data.CaptureRepository

internal object DebugSeed {
    suspend fun insert(repository: CaptureRepository) = Unit
}
