package io.timestamp.app.gleo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.core.content.ContextCompat
import io.timestamp.app.capture.CaptureService
import kotlinx.coroutines.*

fun sendGleoResponse(context: Context, response: String) {
    if (io.timestamp.app.BuildConfig.DEBUG) android.util.Log.i(CaptureService.TAG, "Gleo response: $response")
    context.sendBroadcast(Intent(APP_ACTION_RESPONSE).putExtra("packageName", context.packageName).putExtra("response", response))
}

class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RECORD_VEHICLE_MOMENT) return
        val ordered = isOrderedBroadcast
        val pending = goAsync()
        fun respond(value: String) {
            if (ordered) pending.resultData = value
            sendGleoResponse(context, value)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            try {
                val symptom = parseGleoSymptom(intent.getStringExtra("ACTION_PARAM"))
                val response = CompletableDeferred<String>()
                val callback = object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        resultData?.getString("response")?.let { response.complete(it) }
                    }
                }
                ContextCompat.startForegroundService(context, Intent(context, CaptureService::class.java)
                    .setAction(RECORD_VEHICLE_MOMENT).putExtra("symptom", symptom).putExtra("callback", callback))
                respond(withTimeout(6500) { response.await() })
            } catch (_: TimeoutCancellationException) {
                respond(gleoResponse("fail", "Recording could not be confirmed in time."))
            } catch (_: com.google.gson.JsonParseException) {
                respond(gleoResponse("fail", "The recording parameters are invalid."))
            } catch (_: IllegalArgumentException) {
                respond(gleoResponse("fail", "The recording parameters are invalid."))
            } catch (_: Exception) {
                respond(gleoResponse("fail", "Recording could not start because background service access is unavailable."))
            } finally { pending.finish(); coroutineContext.cancel() }
        }
    }
}
