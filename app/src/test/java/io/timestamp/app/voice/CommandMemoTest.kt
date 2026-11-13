package io.timestamp.app.voice

import org.junit.Assert.*
import org.junit.Test

class CommandMemoTest {
    @Test fun commandOnlyHasNoMemo() {
        listOf("스탬프", "스템프", "타임스탬프", "타임 스템프 찍어줘", "스탬프 찍어", "스탬프 찍어 줘")
            .forEach { assertNull(it, stripTimestampCommand(it)) }
    }

    @Test fun stripsCommandWordsAndTheirSeparators() {
        listOf("스탬프 찍어줘 끼익 소리가 나", "타임스탬프, 끼익 소리가 나", "스템프. 찍어 줘, 끼익 소리가 나",
            "타임 스탬프찍어줘 끼익 소리가 나", "스탬프 찍어 끼익 소리가 나", "스탬프, 타임 스탬프. 찍어줘, 끼익 소리가 나")
            .forEach { assertEquals(it, "끼익 소리가 나", stripTimestampCommand(it)) }
    }

    @Test fun discardsWordsBeforeCommand() {
        listOf("지금 타임 스탬프 찍어 줘", "여기 스탬프", "지금 여기 타임스탬프 찍어줘")
            .forEach { assertNull(it, stripTimestampCommand(it)) }
        assertEquals("오른쪽 뒤에서 드르륵 소리가 나", stripTimestampCommand("여기 스탬프, 오른쪽 뒤에서 드르륵 소리가 나"))
    }

    @Test fun preservesWordsInsideTheMemo() {
        assertEquals("끼익 소리, 타임 스탬프 찍어줘라고 말하기 전에 났어", stripTimestampCommand("스탬프, 끼익 소리, 타임 스탬프 찍어줘라고 말하기 전에 났어"))
        assertEquals("타임벨트 소리가 나", stripTimestampCommand("스탬프, 타임벨트 소리가 나"))
        assertEquals("찍어둔 소리와 같아", stripTimestampCommand("스탬프, 찍어둔 소리와 같아"))
    }

    @Test fun whitespaceAndPunctuationOnlyAreNotAMemo() {
        listOf("스탬프, . \t", "지금 타임 스탬프 찍어 줘. ,", "타임\t스\n탬프\t찍어\n줘")
            .forEach { assertNull(it, stripTimestampCommand(it)) }
    }

    @Test fun doesNotInventAMemoWithoutACommand() {
        listOf("", " ", "끼익 소리가 나", "찍어줘", "타임")
            .forEach { assertNull(it, stripTimestampCommand(it)) }
    }

    @Test fun preservesPunctuationInTheDescription() {
        assertEquals("끼익, 덜컥 소리가 나.", stripTimestampCommand("스탬프, 끼익, 덜컥 소리가 나."))
    }
}