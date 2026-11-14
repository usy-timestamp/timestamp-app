package io.timestamp.app.gleo

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class GleoProtocolTest {
    @Test fun absentParametersAreEmpty() {
        listOf(null, "", " ", "{}", "\"\"").forEach { assertNull(parseGleoSymptom(it)) }
    }
    @Test fun acceptsObjectAndQuotedObject() {
        val json = """{"symptom":" 끼익 소리 "}"""
        assertEquals("끼익 소리", parseGleoSymptom(json))
        assertEquals("끼익 소리", parseGleoSymptom(com.google.gson.Gson().toJson(json)))
        assertEquals("끼익 소리", parseGleoSymptom("\"" + json + "\""))
    }
    @Test fun blankAndNullSymptomsAreAbsent() {
        assertNull(parseGleoSymptom("""{"symptom":" "}"""))
        assertNull(parseGleoSymptom("""{"symptom":null}"""))
    }
    @Test fun preservesEscapesAndUnicode() {
        val memo = "끼익 \"소리\"\n다시"
        val json = com.google.gson.JsonObject().apply { addProperty("symptom", memo) }.toString()
        assertEquals(memo, parseGleoSymptom(json))
    }
    @Test fun rejectsInvalidParameters() {
        listOf("[]", "broken", "{", """{"symptom":12}""", """{"symptom":true}""").forEach {
            try { parseGleoSymptom(it); fail(it) } catch (_: Exception) { }
        }
    }
    @Test fun responseContainsContractAndArrayString() {
        val json = JsonParser.parseString(gleoResponse("success", "The vehicle state was recorded.", "silent", "abc")).asJsonObject
        assertEquals(RECORD_VEHICLE_MOMENT, json["functionName"].asString)
        assertEquals("success", json["status"].asString)
        assertEquals("silent", json["mode"].asString)
        assertEquals("The vehicle state was recorded.", json["message"].asString)
        assertEquals("abc", JsonParser.parseString(json["data"].asString).asJsonArray[0].asJsonObject["captureId"].asString)
    }
    @Test fun failureDoesNotClaimCapture() {
        val json = JsonParser.parseString(gleoResponse("fail", "Recording could not start.")).asJsonObject
        assertEquals("voice", json["mode"].asString)
        assertEquals(0, JsonParser.parseString(json["data"].asString).asJsonArray.size())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidStatus() { gleoResponse("ok", "Saved.") }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidMode() { gleoResponse("fail", "Failed.", "loud") }
}
