package io.timestamp.app.voice

private const val separators = "\\s\\p{Z},."
private const val stamp = "스[\\s\\p{Z}]*[탬템][\\s\\p{Z}]*프"
private val stampPattern = Regex(stamp)
private val commandPrefix = Regex("^(?:타임(?=$|[$separators]|스)|$stamp|찍어[\\s\\p{Z}]*줘|찍어(?=$|[$separators]))[$separators]*")

// 명령 앞의 짧은 말도 버리고 명령 뒤의 설명만 남겨 엉뚱한 메모가 생기지 않게 한다.
fun stripTimestampCommand(text: String): String? {
    val command = stampPattern.find(text) ?: return null
    var rest = text.substring(command.range.last + 1).trimStart { it.isWhitespace() || Character.isSpaceChar(it) || it == ',' || it == '.' }
    while (true) {
        val prefix = commandPrefix.find(rest) ?: break
        rest = rest.substring(prefix.value.length)
    }
    return rest.trim { it.isWhitespace() || Character.isSpaceChar(it) }.ifBlank { null }
}
