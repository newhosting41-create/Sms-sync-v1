package com.gatenoc.bkashsync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!AppStorage.isServiceEnabled(context)) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages[0].originatingAddress ?: ""
        val fullBody = StringBuilder()
        for (msg in messages) {
            fullBody.append(msg.messageBody ?: "")
        }
        val rawSms = fullBody.toString().trim()

        if (SmsParser.isBkashSender(sender)) {
            val parsedRecord = SmsParser.parse(sender, rawSms)
            if (parsedRecord != null) {
                AppStorage.addOrGetRecord(context, parsedRecord)
                SyncForegroundService.startService(context, syncNow = true)
            }
        }
    }
}
