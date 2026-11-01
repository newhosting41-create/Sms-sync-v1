package com.gatenoc.bkashsync

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ApiResult(
    val httpCode: Int,
    val success: Boolean,
    val status: String,
    val message: String,
    val json: JSONObject? = null
)

object ApiClient {

    private fun postJson(endpoint: String, token: String, payload: JSONObject): ApiResult {
        if (endpoint.isBlank() || token.length < 16) {
            return ApiResult(0, false, "error", "Endpoint or Device Token not configured")
        }
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 15000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Device-Token", token)
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val responseText = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            } ?: ""

            val json = try { JSONObject(responseText) } catch (_: Exception) { null }
            val ok = json?.optBoolean("success", false) ?: false
            val st = json?.optString("status", "") ?: ""
            val msg = json?.optString("message", if (ok) "OK" else "HTTP $code") ?: "HTTP $code"

            ApiResult(code, ok, st, msg, json)
        } catch (e: Exception) {
            ApiResult(0, false, "network_error", e.localizedMessage ?: "Network error")
        } finally {
            conn?.disconnect()
        }
    }

    fun pingServer(endpoint: String, token: String): ApiResult {
        val body = JSONObject().apply {
            put("action", "ping")
        }
        return postJson(endpoint, token, body)
    }

    fun syncSmsEvent(endpoint: String, token: String, record: SmsRecord): ApiResult {
        val body = JSONObject().apply {
            put("sender", "bKash")
            put("raw_sms", record.rawSms)
            put("local_event_id", record.localEventId)
        }
        return postJson(endpoint, token, body)
    }

    fun checkTrxStatuses(endpoint: String, token: String, trxIds: List<String>): Map<String, String> {
        if (trxIds.isEmpty()) return emptyMap()
        val arr = JSONArray()
        trxIds.take(100).forEach { arr.put(it) }

        val body = JSONObject().apply {
            put("action", "status")
            put("trx_ids", arr)
        }

        val res = postJson(endpoint, token, body)
        val resultMap = mutableMapOf<String, String>()
        if (res.success && res.json != null) {
            val items = res.json.optJSONObject("items")
            if (items != null) {
                val keys = items.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    resultMap[k.uppercase()] = items.optString(k, "unused")
                }
            }
        }
        return resultMap
    }
}
