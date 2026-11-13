package io.timestamp.app.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimestampCommandTest {
    @Test fun acceptsTheSixPhrases() {
        listOf("스탬프", "스탬프 찍어", "스탬프 찍어줘", "타임 스탬프", "타임 스탬프 찍어", "타임 스탬프 찍어줘")
            .forEach { assertTrue(it, isTimestampCommand(it)) }
    }

    @Test fun acceptsBothSpellingsAndWhitespace() {
        listOf("타임스탬프 찍어줘", "타임스템프 찍어줘", "스템프 찍어", "타임\t스\n탬프",
            "지금 타임 스탬프 찍어 줘").forEach { assertTrue(it, isTimestampCommand(it)) }
    }

    @Test fun ignoresEmptyUnrelatedAndIncompleteSpeech() {
        listOf("", " ", "브레이크 소리가 나", "타임", "찍어줘", "스", "타임 스")
            .forEach { assertFalse(it, isTimestampCommand(it)) }
    }
}
