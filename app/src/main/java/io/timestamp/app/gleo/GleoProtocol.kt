package io.timestamp.app.gleo

import com.google.gson.JsonObject
import com.google.gson.JsonParser

const val RECORD_VEHICLE_MOMENT = "RECORD_VEHICLE_MOMENT"
const val APP_ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"

fun parseGleoSymptom(raw: String?): String? {
    var text = raw?.trim().orEmpty().ifBlank { "{}" }
    repeat(2) {
        if (text.startsWith('"') && text.endsWith('"')) {
            text = try { JsonParser.parseString(text).asString } catch (_: Exception) { text.substring(1, text.length - 1) }
        }
    }
    val value = JsonParser.parseString(text.ifBlank { "{}" })
    require(value.isJsonObject) { "Parameters must be an object." }
    val symptom = value.asJsonObject.get("symptom") ?: return null
    if (symptom.isJsonNull) return null
    require(symptom.isJsonPrimitive && symptom.asJsonPrimitive.isString) { "Symptom must be a string." }
    return symptom.asString.trim().ifBlank { null }
}

fun gleoResponse(status: String, message: String, mode: String = "voice", captureId: String? = null): String {
    require(status in setOf("success", "asking", "fail"))
    require(mode in setOf("voice", "beep", "silent"))
    return JsonObject().apply {
        addProperty("functionName", RECORD_VEHICLE_MOMENT)
        addProperty("status", status)
        addProperty("message", message)
        addProperty("mode", mode)
        val data = com.google.gson.JsonArray()
        captureId?.let { data.add(JsonObject().apply { addProperty("captureId", it) }) }
        addProperty("data", data.toString())
    }.toString()
}
