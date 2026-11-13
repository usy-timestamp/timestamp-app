package io.timestamp.app.voice

// 받아 주는 말: 스탬프, 스탬프 찍어(줘), 타임 스탬프, 타임 스탬프 찍어(줘).
// 음성 인식이 소리가 비슷하게 적는 표기도 받는다. 글자 사이 공백은 상관없다.
// 1) 긴 꼴: [스쓰] + [탬템탠텐땜뗌] + [프푸]. 스템프, 스탠프, 스땜프, 스탬푸, 쓰탬프 등.
// 2) 짧은 꼴: [스쓰] + [탬땜]. 앞이 낱말 시작이거나 "타임"이고, 뒤가 끝, 한글 아닌 글자, "찍"일 때만.
// 막는 말: 샘플, 캠프, 템플, 스텐, 스탠드, 스탠딩, 스템, 스타트, 스탭, 스테이크, 타임라인 등.
private const val gap = "[\\s\\p{Z}]*"
internal const val STAMP_REGEX =
    "(?:[스쓰]$gap[탬템땜뗌]$gap[프푸]" +
        // 탠, 텐은 "스텐 프라이팬"처럼 띄어 쓴 일상 말과 겹치지 않게 붙여 쓴 꼴만 받는다
        "|[스쓰]$gap[탠텐][프푸]" +
        "|(?:(?<![가-힣])|(?<=타임))[스쓰]$gap[탬땜](?=$|[^가-힣]|찍))"
internal val stampPattern = Regex(STAMP_REGEX)

fun isTimestampCommand(text: String): Boolean = stampPattern.containsMatchIn(text)
