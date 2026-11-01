package com.gatenoc.bkashsync

import java.util.UUID

data class SmsRecord(
    val localEventId: String,
    val senderHeader: String,
    val senderNumber: String,
    val amountBdt: Double,
    val feeBdt: Double,
    val balanceAfter: Double,
    val trxId: String,
    val smsTimeStr: String,
    val rawSms: String,
    var syncStatus: String, // "PENDING", "SYNCED", "USED", "DUPLICATE", "FLAGGED", "FAILED"
    var serverNote: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object SmsParser {
    // Exact match with your PHP backend pattern in payment/bkash_sms_sync.php
    private val BKASH_REGEX = Regex(
        """^You have received Tk\s*([\d,]+(?:\.\d{2})?)\s+from\s+(01\d{9})\.?(?:\s+Ref\s+[^\r\n]*?\.)?\s+(?:Fee|Commission)\s+Tk\s*([\d,]+(?:\.\d{2})?)\.\s+Balance\s+Tk\s*([\d,]+(?:\.\d{2})?)\.\s+TrxID\s+([A-Z0-9]{10})\s+at\s+(\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2})\s*$""",
        RegexOption.IGNORE_CASE
    )

    fun isBkashSender(sender: String?): Boolean {
        if (sender.isNullOrBlank()) return false
        return sender.trim().equals("bKash", ignoreCase = true)
    }

    fun parse(sender: String, rawBody: String): SmsRecord? {
        val cleanSms = rawBody.trim().replace(Regex("""\s+"""), " ")
        val match = BKASH_REGEX.matchEntire(cleanSms) ?: return null

        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val senderNum = match.groupValues[2]
        val fee = match.groupValues[3].replace(",", "").toDoubleOrNull() ?: 0.0
        val balance = match.groupValues[4].replace(",", "").toDoubleOrNull() ?: 0.0
        val trxId = match.groupValues[5].uppercase()
        val timeStr = match.groupValues[6]

        if (amount <= 0 || amount > 1000000) return null

        val eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").take(20)

        return SmsRecord(
            localEventId = eventId,
            senderHeader = "bKash",
            senderNumber = senderNum,
            amountBdt = amount,
            feeBdt = fee,
            balanceAfter = balance,
            trxId = trxId,
            smsTimeStr = timeStr,
            rawSms = rawBody.trim(),
            syncStatus = "PENDING"
        )
    }
}
