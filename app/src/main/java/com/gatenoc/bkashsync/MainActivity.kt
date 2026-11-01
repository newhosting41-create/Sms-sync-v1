package com.gatenoc.bkashsync

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var endpointInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var statusBadge: TextView
    private lateinit var serviceSwitch: Switch
    private lateinit var statsText: TextView
    private lateinit var listContainer: LinearLayout

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshSmsList()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUi()
        checkAndRequestPermissions()

        if (AppStorage.isServiceEnabled(this)) {
            SyncForegroundService.startService(this, syncNow = true)
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(SyncForegroundService.ACTION_UI_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateReceiver, filter)
        }
        refreshSmsList()
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(updateReceiver) } catch (_: Exception) {}
    }

    private fun cardBg(bgColor: String = "#1E293B", strokeColor: String = "#334155"): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = 28f
            setStroke(2, Color.parseColor(strokeColor))
        }
    }

    private fun btnBg(bgColor: String): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(bgColor))
            cornerRadius = 20f
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupUi() {
        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 50, 40, 60)
        }

        val title = TextView(this).apply {
            text = "⚡ bKash SMS Sync Gateway"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        }
        val subTitle = TextView(this).apply {
            text = "Method ID: 76 • Real-time Auto Payment Verification"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            setPadding(0, 6, 0, 30)
        }
        mainLayout.addView(title)
        mainLayout.addView(subTitle)

        val serviceCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = cardBg("#1E293B", "#0EA5E9")
            setPadding(36, 32, 36, 32)
            gravity = Gravity.CENTER_VERTICAL
        }
        statusBadge = TextView(this).apply {
            text = "Background Listener"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        serviceSwitch = Switch(this).apply {
            isChecked = AppStorage.isServiceEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    val ep = endpointInput.text.toString().trim()
                    val tk = tokenInput.text.toString().trim()
                    if (ep.isBlank() || tk.length < 16) {
                        Toast.makeText(this@MainActivity, "Save valid Endpoint & Token (16+ chars) first!", Toast.LENGTH_LONG).show()
                        this.isChecked = false
                        return@setOnCheckedChangeListener
                    }
                    AppStorage.saveCredentials(this@MainActivity, ep, tk)
                    AppStorage.setServiceEnabled(this@MainActivity, true)
                    SyncForegroundService.startService(this@MainActivity, syncNow = true)
                } else {
                    AppStorage.setServiceEnabled(this@MainActivity, false)
                    SyncForegroundService.stopService(this@MainActivity)
                }
                updateServiceBadge()
            }
        }
        serviceCard.addView(statusBadge)
        serviceCard.addView(serviceSwitch)
        mainLayout.addView(serviceCard)

        val configCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(36, 36, 36, 36)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 28 }
        }

        val epLabel = TextView(this).apply {
            text = "Android App Sync Endpoint URL"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
        }
        endpointInput = EditText(this).apply {
            hint = "https://yourdomain.com/payment/bkash_sms_sync"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            textSize = 14f
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            setText(AppStorage.getEndpoint(this@MainActivity))
        }

        val tkLabel = TextView(this).apply {
            text = "Device Pairing Token (X-Device-Token)"
            setTextColor(Color.parseColor("#CBD5E1"))
            textSize = 13f
            setPadding(0, 20, 0, 0)
        }
        tokenInput = EditText(this).apply {
            hint = "Paste 24-char token from Admin Panel"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 14f
            setText(AppStorage.getToken(this@MainActivity))
        }

        val connectBtn = Button(this).apply {
            text = "💾 Save & Test Connection (Ping)"
            setTextColor(Color.WHITE)
            background = btnBg("#0284C7")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 24 }
            setOnClickListener { testAndSaveConnection() }
        }

        configCard.addView(epLabel)
        configCard.addView(endpointInput)
        configCard.addView(tkLabel)
        configCard.addView(tokenInput)
        configCard.addView(connectBtn)
        mainLayout.addView(configCard)

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 28 }
        }

        val syncNowBtn = Button(this).apply {
            text = "🔄 Sync Queue"
            setTextColor(Color.WHITE)
            background = btnBg("#16A34A")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                rightMargin = 10
            }
            setOnClickListener {
                SyncForegroundService.startService(this@MainActivity, syncNow = true)
                Toast.makeText(this@MainActivity, "Syncing pending SMS & statuses...", Toast.LENGTH_SHORT).show()
            }
        }

        val manualBtn = Button(this).apply {
            text = "📋 Paste SMS"
            setTextColor(Color.WHITE)
            background = btnBg("#7C3AED")
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = 10
            }
            setOnClickListener { showManualPasteDialog() }
        }

        actionsRow.addView(syncNowBtn)
        actionsRow.addView(manualBtn)
        mainLayout.addView(actionsRow)

        val batteryBtn = Button(this).apply {
            text = "🛡️ Allow Unrestricted Background / Auto-Start"
            setTextColor(Color.parseColor("#FDE047"))
            background = cardBg("#1E293B", "#CA8A04")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20 }
            setOnClickListener { requestIgnoreBatteryOptimizations() }
        }
        mainLayout.addView(batteryBtn)

        val historyHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 36, 0, 16)
        }
        statsText = TextView(this).apply {
            text = "SMS Logs (0)"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val clearBtn = TextView(this).apply {
            text = "🗑️ Clear Logs"
            setTextColor(Color.parseColor("#F87171"))
            textSize = 13f
            setPadding(16, 8, 16, 8)
            setOnClickListener { showClearDialog() }
        }
        historyHeader.addView(statsText)
        historyHeader.addView(clearBtn)
        mainLayout.addView(historyHeader)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(listContainer)

        rootScroll.addView(mainLayout)
        setContentView(rootScroll)

        updateServiceBadge()
        refreshSmsList()
    }

    @SuppressLint("SetTextI18n")
    private fun updateServiceBadge() {
        val active = AppStorage.isServiceEnabled(this)
        statusBadge.text = if (active) "🟢 Listener RUNNING (24/7)" else "🔴 Listener STOPPED"
        statusBadge.setTextColor(if (active) Color.parseColor("#4ADE80") else Color.parseColor("#F87171"))
    }

    private fun testAndSaveConnection() {
        val ep = endpointInput.text.toString().trim()
        val tk = tokenInput.text.toString().trim()
        if (ep.isBlank() || tk.length < 16) {
            Toast.makeText(this, "Please enter valid URL and 16+ char Token", Toast.LENGTH_LONG).show()
            return
        }
        AppStorage.saveCredentials(this, ep, tk)
        Toast.makeText(this, "Pinging server...", Toast.LENGTH_SHORT).show()

        uiScope.launch {
            val res = withContext(Dispatchers.IO) {
                ApiClient.pingServer(ep, tk)
            }
            if (res.success) {
                Toast.makeText(this@MainActivity, "✅ ${res.message}", Toast.LENGTH_LONG).show()
                if (!serviceSwitch.isChecked) {
                    serviceSwitch.isChecked = true
                }
            } else {
                Toast.makeText(this@MainActivity, "❌ Failed: ${res.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showManualPasteDialog() {
        val input = EditText(this).apply {
            hint = "You have received Tk 500.00 from 017XXXXXXXX. Fee Tk 0.00. Balance Tk 1,500.00. TrxID ABC1234567 at 01/11/2026 14:30"
            minLines = 4
            setPadding(32, 32, 32, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Paste bKash Payment SMS")
            .setMessage("Paste exact bKash SMS body to verify & sync with server:")
            .setView(input)
            .setPositiveButton("Verify & Upload") { _, _ ->
                val raw = input.text.toString().trim()
                val parsed = SmsParser.parse("bKash", raw)
                if (parsed == null) {
                    Toast.makeText(this, "❌ Invalid bKash SMS format!", Toast.LENGTH_LONG).show()
                } else {
                    AppStorage.addOrGetRecord(this, parsed)
                    refreshSmsList()
                    SyncForegroundService.startService(this, syncNow = true)
                    Toast.makeText(this, "✅ Added TrxID: ${parsed.trxId}. Syncing...", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClearDialog() {
        val options = arrayOf("Clear Pending/Failed Only", "Clear Synced/Used Only", "Clear All History")
        AlertDialog.Builder(this)
            .setTitle("Manage Local SMS Logs")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> AppStorage.clearByType(this, "PENDING")
                    1 -> AppStorage.clearByType(this, "SYNCED")
                    2 -> AppStorage.clearByType(this, "ALL")
                }
                refreshSmsList()
            }
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun refreshSmsList() {
        val records = AppStorage.getRecords(this)
        val syncedCount = records.count { it.syncStatus == "SYNCED" || it.syncStatus == "USED" || it.syncStatus == "DUPLICATE" }
        val pendingCount = records.count { it.syncStatus == "PENDING" || it.syncStatus == "RETRY" }

        statsText.text = "SMS Logs (${records.size}) • Synced: $syncedCount • Pending: $pendingCount"
        listContainer.removeAllViews()

        if (records.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No bKash payment SMS recorded yet."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 14f
                setPadding(10, 30, 10, 30)
            }
            listContainer.addView(emptyView)
            return
        }

        for (item in records) {
            val badgeColor = when (item.syncStatus) {
                "USED" -> "#22C55E"
                "SYNCED", "DUPLICATE" -> "#38BDF8"
                "PENDING", "RETRY" -> "#FACC15"
                else -> "#F87171"
            }

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = cardBg("#1E293B", "#334155")
                setPadding(32, 28, 32, 28)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 20 }
            }

            val topRow = TextView(this).apply {
                text = "TrxID: ${item.trxId}   •   Tk ${String.format("%.2f", item.amountBdt)}   [${item.syncStatus}]"
                setTextColor(Color.parseColor(badgeColor))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            }

            val midRow = TextView(this).apply {
                text = "From: ${item.senderNumber}  |  SMS Time: ${item.smsTimeStr}"
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                setPadding(0, 8, 0, 6)
            }

            val bodyRow = TextView(this).apply {
                text = item.rawSms
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
            }

            card.addView(topRow)
            card.addView(midRow)
            card.addView(bodyRow)

            if (item.serverNote.isNotBlank()) {
                val noteRow = TextView(this).apply {
                    text = "Server: ${item.serverNote}"
                    setTextColor(Color.parseColor("#E2E8F0"))
                    textSize = 11f
                    setPadding(0, 6, 0, 0)
                }
                card.addView(noteRow)
            }

            listContainer.addView(card)
        }
    }

    private fun checkAndRequestPermissions() {
        val perms = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Battery unrestricted mode is already enabled!", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }
}
