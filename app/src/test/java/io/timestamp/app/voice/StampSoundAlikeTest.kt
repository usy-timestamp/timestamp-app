package io.timestamp.app.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// 음성 인식이 "스탬프"를 비슷한 소리로 적는 경우와 막아야 할 일상 낱말을 확인한다.
class StampSoundAlikeTest {
    @Test fun acceptsSoundAlikeSpellings() {
        listOf("스탬프", "스템프", "스탠프", "스땜프", "스탬푸", "쓰탬프", "스탬", "타임스템프", "타임 스탠프",
            "타임스탬", "스 템 프", "타임 스탬", "스텐프", "스탬 찍어줘", "스탬찍어", "스탬.", "쓰템푸 찍어줘")
            .forEach { assertTrue(it, isTimestampCommand(it)) }
    }

    @Test fun rejectsEverydayWords() {
        listOf("샘플", "캠프", "템플", "탬플", "셈플", "스텐", "스탠", "스탠드", "스탠다드", "스탠딩", "스템", "스타트",
            "스탭", "스테이크", "타임라인", "타임", "스탬피드", "미스탬", "스테인리스", "캠핑 스탠드 샘플",
            "스템이 부러졌어", "스탠딩 테이블", "스텐 프라이팬", "스탠 프로")
            .forEach { assertFalse(it, isTimestampCommand(it)) }
    }

    @Test fun stripsSoundAlikeCommandFromMemo() {
        assertEquals("끼익 소리가 나", stripTimestampCommand("스템프, 끼익 소리가 나"))
        assertEquals("끼익 소리가 나", stripTimestampCommand("타임 스탠프 찍어줘 끼익 소리가 나"))
        assertEquals("끼익 소리가 나", stripTimestampCommand("스탬 찍어, 끼익 소리가 나"))
        assertEquals("뒤에서 덜컥 소리", stripTimestampCommand("쓰탬푸. 스땜프, 뒤에서 덜컥 소리"))
        assertEquals("끼익 소리가 나", stripTimestampCommand("타임스탬, 끼익 소리가 나"))
        assertEquals("스탠드 쪽에서 소리가 나", stripTimestampCommand("스템프, 스탠드 쪽에서 소리가 나"))
    }

    @Test fun soundAlikeCommandOnlyHasNoMemo() {
        listOf("스템프", "타임 스탠프 찍어 줘", "스탬", "타임스탬 찍어줘", "쓰탬프, 스탬 찍어")
            .forEach { assertNull(it, stripTimestampCommand(it)) }
    }
}
