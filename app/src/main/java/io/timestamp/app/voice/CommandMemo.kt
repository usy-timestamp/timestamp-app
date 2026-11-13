package io.timestamp.app.voice

private const val separators = "\\s\\p{Z},."
private val commandPrefix = Regex("^(?:타임(?=$|[$separators]|[스쓰])|$STAMP_REGEX|찍어[\\s\\p{Z}]*줘|찍어(?=$|[$separators]))[$separators]*")

// 명령 앞의 짧은 말도 버리고 명령 뒤의 설명만 남겨 엉뚱한 메모가 생기지 않게 한다.
// 명령 판정과 같은 표기 규칙(STAMP_REGEX)으로 떼어 낸다.
fun stripTimestampCommand(text: String): String? {
    val command = stampPattern.find(text) ?: return null
    var rest = text.substring(command.range.last + 1).trimStart { it.isWhitespace() || Character.isSpaceChar(it) || it == ',' || it == '.' }
    while (true) {
        val prefix = commandPrefix.find(rest) ?: break
        rest = rest.substring(prefix.value.length)
    }
    return rest.trim { it.isWhitespace() || Character.isSpaceChar(it) }.ifBlank { null }
}
