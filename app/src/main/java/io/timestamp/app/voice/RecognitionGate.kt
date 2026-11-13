package io.timestamp.app.voice

// 파일을 다 보낸 것과 인식 결과를 받은 것은 달라서 둘 다 끝나야 듣기를 다시 연다.
class RecognitionGate {
    var awaitingResult = false
        private set
    var sendingFile = false
        private set
    val canRestart get() = !awaitingResult && !sendingFile

    fun fileStarted() { sendingFile = true; awaitingResult = true }
    fun fileSent() { sendingFile = false; awaitingResult = true }
    fun speechStarted() { awaitingResult = true }
    fun resultFinished() { awaitingResult = false }
    fun reset() { sendingFile = false; awaitingResult = false }
}
