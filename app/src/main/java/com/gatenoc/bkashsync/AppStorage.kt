package com.gatenoc.bkashsync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AppStorage {
    private const val PREF_NAME = "bkash_sync_store"
    private const val KEY_ENDPOINT = "endpoint_url"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_RECORDS = "sms_records_json"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getEndpoint(context: Context): String =
        prefs(context).getString(KEY_ENDPOINT, "")?.trim() ?: ""

    fun getToken(context: Context): String =
        prefs(context).getString(KEY_TOKEN, "")?.trim() ?: ""

    fun saveCredentials(context: Context, endpoint: String, token: String) {
        prefs(context).edit()
            .putString(KEY_ENDPOINT, endpoint.trim())
            .putString(KEY_TOKEN, token.trim())
            .apply()
    }

    fun isServiceEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SERVICE_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
    }

    @Synchronized
    fun getRecords(context: Context): MutableList<SmsRecord> {
        val raw = prefs(context).getString(KEY_RECORDS, "[]") ?: "[]"
        val list = mutableListOf<SmsRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    SmsRecord(
                        localEventId = o.optString("localEventId"),
                        senderHeader = o.optString("senderHeader", "bKash"),
                        senderNumber = o.optString("senderNumber"),
                        amountBdt = o.optDouble("amountBdt", 0.0),
                        feeBdt = o.optDouble("feeBdt", 0.0),
                        balanceAfter = o.optDouble("balanceAfter", 0.0),
                        trxId = o.optString("trxId"),
                        smsTimeStr = o.optString("smsTimeStr"),
                        rawSms = o.optString("rawSms"),
                        syncStatus = o.optString("syncStatus", "PENDING"),
                        serverNote = o.optString("serverNote", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @Synchronized
    fun saveRecords(context: Context, records: List<SmsRecord>) {
        val arr = JSONArray()
        records.take(300).forEach { r ->
            val o = JSONObject().apply {
                put("localEventId", r.localEventId)
                put("senderHeader", r.senderHeader)
                put("senderNumber", r.senderNumber)
                put("amountBdt", r.amountBdt)
                put("feeBdt", r.feeBdt)
                put("balanceAfter", r.balanceAfter)
                put("trxId", r.trxId)
                put("smsTimeStr", r.smsTimeStr)
                put("rawSms", r.rawSms)
                put("syncStatus", r.syncStatus)
                put("serverNote", r.serverNote)
                put("createdAt", r.createdAt)
            }
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_RECORDS, arr.toString()).apply()
    }

    @Synchronized
    fun addOrGetRecord(context: Context, record: SmsRecord): SmsRecord {
        val list = getRecords(context)
        val existing = list.find { it.trxId.equals(record.trxId, ignoreCase = true) }
        if (existing != null) return existing
        list.add(0, record)
        saveRecords(context, list)
        return record
    }

    @Synchronized
    fun updateStatus(context: Context, trxId: String, status: String, note: String = "") {
        val list = getRecords(context)
        var changed = false
        for (item in list) {
            if (item.trxId.equals(trxId, ignoreCase = true)) {
                item.syncStatus = status
                if (note.isNotEmpty()) item.serverNote = note
                changed = true
            }
        }
        if (changed) saveRecords(context, list)
    }

    @Synchronized
    fun clearByType(context: Context, type: String) {
        val list = getRecords(context)
        val filtered = when (type) {
            "ALL" -> emptyList()
            "PENDING" -> list.filterNot { it.syncStatus == "PENDING" || it.syncStatus == "FAILED" }
            "SYNCED" -> list.filterNot { it.syncStatus == "SYNCED" || it.syncStatus == "USED" || it.syncStatus == "DUPLICATE" }
            else -> list
        }
        saveRecords(context, filtered)
    }
}
