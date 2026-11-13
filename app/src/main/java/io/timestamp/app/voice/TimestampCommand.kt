package io.timestamp.app.voice

// 받아 주는 말: 스탬프, 스탬프 찍어, 스탬프 찍어줘, 타임 스탬프, 타임 스탬프 찍어, 타임 스탬프 찍어줘.
// 여섯 가지 모두 "스탬프"가 들어 있어 그 낱말만 찾는다. 음성 인식이 "스템프"로 적는 경우도 받는다.
fun isTimestampCommand(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() || Character.isSpaceChar(it) }
    return "스탬프" in compact || "스템프" in compact
}
