package com.arashxzo.blackout

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ============================================================
// BACKEND_URL is injected at build time from
// `android/local.properties` (which is gitignored).
// See `local.properties.example` for setup instructions.
// ============================================================
val BACKEND_URL: String = BuildConfig.BACKEND_URL

data class HistoryItem(val id: String, val time: String, val target: String, val qty: Int)
data class LogItem(val time: String, val msg: String, val type: LogType)
enum class LogType { INFO, ERROR, WARN, COMMAND }
data class SimCard(val subId: Int, val slotIndex: Int, val displayName: String)
data class AdminUser(val deviceId: String, val credits: Int, val isBanned: Boolean, val totalSms: Int, val createdAt: String)
data class AdminTransaction(val id: String, val deviceId: String, val amount: Float, val credits: Int, val status: String, val createdAt: String)
data class AdminStats(val users: Int, val sms: Int, val revenue: Float)
data class Plan(val id: String, val name: String, val price: Float, val credits: Int, val isActive: Boolean)
data class AdminPlan(val id: String, val name: String, val price: Float, val credits: Int, val isActive: Boolean)

data class AppStrings(
    val appName: String, val tabHome: String, val tabHistory: String, val tabSettings: String, val tabAdmin: String,
    val step1: String, val step2: String, val step3: String, val next: String, val back: String, val execute: String,
    val targetHint: String, val qtyHint: String, val noSim: String, val autoNum: String,
    val logTitle: String, val logReady: String, val logInit: String,
    val histEmpty: String, val histDetails: String, val histTarget: String, val histTime: String, val histQty: String, val histCopy: String, val histClose: String, val copied: String,
    val setLang: String, val setLangDesc: String, val yes: String, val no: String, val panicTitle: String, val panicDesc: String, val panicConfirm: String,
    val dlgTitle: String, val dlgSending: String,
    val licTitle: String, val licDesc: String, val licWait: String, val licSuccess: String, val creditsLeft: String, val buyCredits: String, val noCredits: String, val checkStatus: String,
    val devInfo: String, val copyDevId: String,
    val adminLoginTitle: String, val adminPassHint: String, val adminLoginBtn: String, val adminWrongPass: String,
    val adminDashboard: String, val adminUsers: String, val adminTx: String, val adminPlans: String, val adminBanned: String, val adminUnban: String,
    val adminSetCredits: String, val adminDelete: String, val adminSave: String, val bannedMsg: String,
    val disclaimerTitle: String, val disclaimerText: String, val disclaimerAgree: String, val disclaimerExit: String,
    val adminPlanName: String, val adminPlanPrice: String, val adminPlanCredits: String, val adminPlanActive: String
)

val stringsEn = AppStrings(
    "BLACKOUT", "HOME", "HISTORY", "SETTINGS", "ADMIN",
    "TARGET_NUMBER", "SELECT_SIM", "PAYLOAD_COUNT", "NEXT", "BACK", "EXECUTE",
    "Enter number", "1-100", "NO SIM DETECTED", "AUTO:",
    "> SYSTEM LOGS", "> INITIALIZING...", "[SYS] System ready.",
    "No history found.", "OPERATION DETAILS", "TARGET:", "TIME:", "QTY:", "COPY", "CLOSE", "Copied!",
    "LANGUAGE", "Select interface language", "YES", "NO", "PANIC BUTTON", "Wipe all data and history", "WIPE DATA",
    "EXECUTING", "Sending",
    "OUT OF CREDITS", "You need to buy credits to use this tool.", "Waiting for payment...", "CREDITS ADDED!", "Credits: %d", "Buy Credits", "Not enough credits!", "CHECK STATUS",
    "DEVICE INFO", "COPY DEVICE ID",
    "ADMIN LOGIN", "Enter Admin Password", "LOGIN", "WRONG PASSWORD!",
    "DASHBOARD", "USERS", "TRANSACTIONS", "PLANS", "BAN", "UNBAN",
    "SET CREDITS", "DELETE", "SAVE", "YOUR ACCOUNT HAS BEEN BANNED!",
    "⚠️ LEGAL DISCLAIMER", "This tool is strictly for authorized security research and educational purposes.\n\nUsing this tool against devices you do not own or have explicit permission to test is illegal and strictly prohibited.\n\nThe developer assumes no liability for any misuse, damage, or legal consequences caused by this software. By clicking 'I AGREE', you confirm that you take full responsibility for your actions.", "I AGREE", "EXIT",
    "Name", "Price ($)", "Credits", "Active"
)

val stringsFa = AppStrings(
    "بلک‌اوت", "خانه", "تاریخچه", "تنظیمات", "مدیریت",
    "شماره هدف", "انتخاب سیم‌کارت", "تعداد پی‌لود", "بعدی", "قبلی", "اجرا",
    "شماره را وارد کنید", "۱-۱۰۰", "سیم‌کارت یافت نشد", "خودکار:",
    "> لاگ سیستم", "> در حال راه‌اندازی...", "[سیستم] آماده به کار.",
    "تاریخچه‌ای یافت نشد.", "جزئیات عملیات", "هدف:", "زمان:", "تعداد:", "کپی", "بستن", "کپی شد!",
    "زبان", "انتخاب زبان برنامه", "بله", "خیر", "دکمه وحشت", "پاک کردن تمام اطلاعات و تاریخچه", "پاکسازی اطلاعات",
    "در حال اجرا", "ارسال",
    "اعتبار تمام شد", "برای استفاده از این ابزار باید اعتبار خریداری کنید.", "در انتظار پرداخت...", "اعتبار اضافه شد!", "اعتبار باقی‌مانده: %d", "خرید اعتبار", "اعتبار کافی نیست!", "بررسی وضعیت",
    "اطلاعات دستگاه", "کپی شناسه دستگاه",
    "ورود مدیر", "رمز عبور مدیر را وارد کنید", "ورود", "رمز عبور اشتباه است!",
    "داشبورد", "کاربران", "تراکنش‌ها", "پلن‌ها", "مسدود کردن", "رفع مسدودی",
    "تنظیم اعتبار", "حذف", "ذخیره", "حساب کاربری شما مسدود شده است!",
    "⚠️ سلب مسئولیت قانونی", "این ابزار صرفاً برای تحقیقات امنیتی مجاز و اهداف آموزشی طراحی شده است.\n\nاستفاده از این ابزار علیه دستگاه‌هایی که مالک آن‌ها نیستید یا اجازه صریح برای تست آن‌ها را ندارید، غیرقانونی است و اکیداً ممنوع می‌باشد.\n\nتوسعه‌دهنده هیچ مسئولیتی در قبال سوءاستفاده، خسارات یا عواقب قانونی ناشی از این نرم‌افزار نمی‌پذیرد. با کلیک روی 'تایید می‌کنم'، شما تایید می‌کنید که مسئولیت کامل اقدامات خود را می‌پذیرید.", "تایید می‌کنم", "خروج",
    "نام", "قیمت (دلار)", "تعداد اعتبار", "فعال"
)

val DarkBg = Color(0xFF050505)
val PanelBg = Color(0xFF0F0F11)
val PrimaryColor = Color(0xFF00FF41)
val TextColor = Color(0xFFE0E0E0)
val HintColor = Color(0xFF555555)
val BorderColor = Color(0xFF222222)
val LogInfo = Color(0xFF00FF41)
val LogError = Color(0xFFFF3333)
val LogWarn = Color(0xFFFFCC00)
val LogCmd = Color(0xFF00BFFF)

// --- Network functions ---
suspend fun fetchCredits(deviceId: String): Pair<Int, Boolean> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/get-credits?device_id=$deviceId").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                Pair(json.optInt("credits", 0), json.optBoolean("banned", false))
            } else Pair(0, false)
        } catch (_: Exception) { Pair(0, false) }
    }
}

suspend fun consumeCredits(deviceId: String, amount: Int): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply { put("deviceId", deviceId); put("amount", amount) }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url("$BACKEND_URL/api/consume-credits").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) JSONObject(response.body?.string() ?: "{}").optBoolean("success", false) else false
        } catch (_: Exception) { false }
    }
}

suspend fun createPaymentLink(deviceId: String, planId: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply { put("deviceId", deviceId); put("planId", planId) }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url("$BACKEND_URL/api/create-payment").post(body).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) JSONObject(response.body?.string() ?: "{}").optString("invoice_url", null) else null
        } catch (_: Exception) { null }
    }
}

suspend fun fetchPublicPlans(): List<Plan> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/get-plans").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<Plan>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(Plan(obj.getString("id"), obj.getString("name"), obj.getDouble("price_usd").toFloat(), obj.getInt("credits"), true))
                }
                list
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

suspend fun adminLogin(password: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/admin/users").header("Authorization", "Bearer $password").build()
            client.newCall(request).execute().isSuccessful
        } catch (_: Exception) { false }
    }
}

suspend fun fetchAdminStats(password: String): AdminStats? {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/admin/stats").header("Authorization", "Bearer $password").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                AdminStats(json.optInt("users"), json.optInt("sms"), json.optDouble("revenue").toFloat())
            } else null
        } catch (_: Exception) { null }
    }
}

suspend fun fetchAdminUsers(password: String): List<AdminUser> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/admin/users").header("Authorization", "Bearer $password").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<AdminUser>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(AdminUser(
                        obj.getString("device_id"),
                        obj.getInt("credits"),
                        obj.optInt("is_banned", 0) == 1,
                        obj.optInt("total_sms_sent", 0),
                        obj.getString("created_at")
                    ))
                }
                list
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

suspend fun fetchAdminTransactions(password: String): List<AdminTransaction> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/admin/transactions").header("Authorization", "Bearer $password").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<AdminTransaction>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(AdminTransaction(
                        obj.getString("id"),
                        obj.getString("device_id"),
                        obj.getDouble("amount_usd").toFloat(),
                        obj.getInt("credits"),
                        obj.getString("status"),
                        obj.getString("created_at")
                    ))
                }
                list
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

suspend fun adminUserAction(password: String, deviceId: String, action: String, value: Int = 0): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply { put("deviceId", deviceId); put("action", action); put("value", value) }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url("$BACKEND_URL/api/admin/user-action").header("Authorization", "Bearer $password").post(body).build()
            client.newCall(request).execute().isSuccessful
        } catch (_: Exception) { false }
    }
}

suspend fun fetchAdminPlans(password: String): List<AdminPlan> {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url("$BACKEND_URL/api/admin/plans").header("Authorization", "Bearer $password").build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val arr = JSONArray(response.body?.string() ?: "[]")
                val list = mutableListOf<AdminPlan>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(AdminPlan(obj.getString("id"), obj.getString("name"), obj.getDouble("price_usd").toFloat(), obj.getInt("credits"), obj.optInt("is_active", 1) == 1))
                }
                list
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }
}

suspend fun updateAdminPlan(password: String, id: String, name: String, price: Float, credits: Int, isActive: Boolean): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val json = JSONObject().apply { put("id", id); put("name", name); put("price", price); put("credits", credits); put("is_active", if (isActive) 1 else 0) }
            val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
            val request = Request.Builder().url("$BACKEND_URL/api/admin/plans/update").header("Authorization", "Bearer $password").post(body).build()
            client.newCall(request).execute().isSuccessful
        } catch (_: Exception) { false }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sanity-check: BACKEND_URL should be set via local.properties
        val isConfigured = BACKEND_URL.isNotBlank() && !BACKEND_URL.contains("configure-me")

        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        setContent {
            if (!isConfigured) {
                // Show a clear configuration error screen instead of failing silently
                MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = PanelBg, primary = PrimaryColor)) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(DarkBg).padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PanelBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, LogError)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(
                                    "⚠️ BACKEND_URL NOT CONFIGURED",
                                    color = LogError,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Text(
                                    "1. Copy android/local.properties.example → android/local.properties\n\n" +
                                    "2. Set BACKEND_URL=https://your-worker.workers.dev\n\n" +
                                    "3. Rebuild the project in Android Studio",
                                    color = TextColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
                return@setContent
            }

            var currentLang by remember { mutableStateOf(prefs.getString("language", "en") ?: "en") }
            val strings = if (currentLang == "fa") stringsFa else stringsEn
            val context = LocalContext.current
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

            var hasAcceptedDisclaimer by remember { mutableStateOf(prefs.getBoolean("accepted_disclaimer", false)) }
            var credits by remember { mutableStateOf<Int?>(null) }
            var isBanned by remember { mutableStateOf(false) }
            var isAdminMode by remember { mutableStateOf(false) }
            var adminPassword by remember { mutableStateOf("") }

            MaterialTheme(colorScheme = darkColorScheme(background = DarkBg, surface = PanelBg, primary = PrimaryColor, onPrimary = DarkBg, onBackground = TextColor, onSurface = TextColor)) {
                if (!hasAcceptedDisclaimer) {
                    DisclaimerScreen(
                        strings = strings,
                        onAgree = { prefs.edit { putBoolean("accepted_disclaimer", true) }; hasAcceptedDisclaimer = true },
                        onExit = { (context as? ComponentActivity)?.finish() }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        val (c, b) = fetchCredits(deviceId)
                        credits = c
                        isBanned = b
                    }

                    if (credits == null) {
                        Box(modifier = Modifier.fillMaxSize().background(DarkBg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PrimaryColor) }
                    } else if (isBanned) {
                        Box(modifier = Modifier.fillMaxSize().background(DarkBg).padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(strings.bannedMsg, color = LogError, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        }
                    } else if (credits!! <= 0 && !isAdminMode) {
                        LicenseScreen(strings, deviceId, onActivated = { credits = it })
                    } else {
                        AppRoot(
                            strings, currentLang, credits!!, deviceId, isAdminMode, adminPassword,
                            onLangChange = { currentLang = it; prefs.edit { putString("language", it) } },
                            onCreditsUpdate = { credits = it },
                            onAdminAuthSuccess = { pass -> isAdminMode = true; adminPassword = pass }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DisclaimerScreen(strings: AppStrings, onAgree: () -> Unit, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(DarkBg).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LogError), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️ LEGAL DISCLAIMER\nسلب مسئولیت قانونی", color = LogError, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    Text("This tool is strictly for authorized security research and educational purposes.\n\nUsing this tool against devices you do not own or have explicit permission to test is illegal and strictly prohibited.\n\nThe developer assumes no liability for any misuse, damage, or legal consequences caused by this software. By clicking 'I AGREE', you confirm that you take full responsibility for your actions.", color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.Justify, lineHeight = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp)); HorizontalDivider(color = BorderColor); Spacer(modifier = Modifier.height(16.dp))
                    Text("این ابزار صرفاً برای تحقیقات امنیتی مجاز و اهداف آموزشی طراحی شده است.\n\nاستفاده از این ابزار علیه دستگاه‌هایی که مالک آن‌ها نیستید یا اجازه صریح برای تست آن‌ها را ندارید، غیرقانونی است و اکیداً ممنوع می‌باشد.\n\nتوسعه‌دهنده هیچ مسئولیتی در قبال سوءاستفاده، خسارات یا عواقب قانونی ناشی از این نرم‌افزار نمی‌پذیرد. با کلیک روی 'تایید می‌کنم'، شما تایید می‌کنید که مسئولیت کامل اقدامات خود را می‌پذیرید.", color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.Justify, lineHeight = 20.sp)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onExit, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = LogError), border = BorderStroke(1.dp, LogError), shape = RoundedCornerShape(4.dp)) { Text("EXIT / خروج", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    Button(onClick = onAgree, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = DarkBg), shape = RoundedCornerShape(4.dp)) { Text("I AGREE / تایید", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun LicenseScreen(strings: AppStrings, deviceId: String, onActivated: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isChecking by remember { mutableStateOf(false) }
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    var isLoadingPlans by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        plans = fetchPublicPlans()
        isLoadingPlans = false
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg).padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, LogError), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("> ${strings.licTitle}", color = LogError, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(strings.licDesc, color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(32.dp))

                if (isLoadingPlans) {
                    CircularProgressIndicator(color = PrimaryColor)
                } else {
                    plans.forEach { plan ->
                        Button(
                            onClick = {
                                scope.launch {
                                    val url = createPaymentLink(deviceId, plan.id)
                                    if (url != null) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    else Toast.makeText(context, "Network Error", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when(plan.id) {
                                    "starter" -> Color(0xFF2E7D32)
                                    "pro" -> Color(0xFF1565C0)
                                    "elite" -> Color(0xFFC62828)
                                    else -> PrimaryColor
                                },
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("${plan.name}: ${plan.credits} SMS - $${plan.price}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = { scope.launch { isChecking = true; val (newCredits, _) = fetchCredits(deviceId); isChecking = false; if (newCredits > 0) onActivated(newCredits) else Toast.makeText(context, strings.licWait, Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryColor), border = BorderStroke(1.dp, PrimaryColor), shape = RoundedCornerShape(4.dp), enabled = !isChecking) {
                    if (isChecking) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = PrimaryColor, strokeWidth = 2.dp) else Text(strings.checkStatus, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Device ID: $deviceId", color = HintColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(strings: AppStrings, currentLang: String, credits: Int, deviceId: String, isAdminMode: Boolean, adminPassword: String, onLangChange: (String) -> Unit, onCreditsUpdate: (Int) -> Unit, onAdminAuthSuccess: (String) -> Unit) {
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = PanelBg, modifier = Modifier.height(64.dp)) {
                NavigationBarItem(icon = { Icon(Icons.Default.Home, null, modifier = Modifier.size(20.dp)) }, label = { Text(strings.tabHome, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }, selected = currentTab == 0, onClick = { currentTab = 0 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryColor, selectedTextColor = PrimaryColor, indicatorColor = Color.Transparent))
                NavigationBarItem(icon = { Icon(Icons.Default.History, null, modifier = Modifier.size(20.dp)) }, label = { Text(strings.tabHistory, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }, selected = currentTab == 1, onClick = { currentTab = 1 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryColor, selectedTextColor = PrimaryColor, indicatorColor = Color.Transparent))
                NavigationBarItem(icon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(20.dp)) }, label = { Text(strings.tabSettings, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }, selected = currentTab == 2, onClick = { currentTab = 2 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryColor, selectedTextColor = PrimaryColor, indicatorColor = Color.Transparent))
                if (isAdminMode) {
                    NavigationBarItem(icon = { Icon(Icons.Default.Star, null, modifier = Modifier.size(20.dp)) }, label = { Text(strings.tabAdmin, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }, selected = currentTab == 3, onClick = { currentTab = 3 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = LogWarn, selectedTextColor = LogWarn, indicatorColor = Color.Transparent))
                }
            }
        }, containerColor = DarkBg
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentTab) {
                0 -> TerminalHomeScreen(strings, credits, deviceId, onCreditsUpdate)
                1 -> HistoryScreen(strings)
                2 -> SettingsScreen(strings, currentLang, onLangChange, credits, deviceId, onCreditsUpdate, onAdminAuthSuccess)
                3 -> if (isAdminMode) AdminScreen(strings, adminPassword) else TerminalHomeScreen(strings, credits, deviceId, onCreditsUpdate)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalHomeScreen(strings: AppStrings, credits: Int, deviceId: String, onCreditsUpdate: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentStep by remember { mutableIntStateOf(1) }
    var targetNumber by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var logs by remember { mutableStateOf(listOf<LogItem>()) }
    var simCards by remember { mutableStateOf(listOf<SimCard>()) }
    var selectedSim by remember { mutableStateOf<SimCard?>(null) }
    var autoNum by remember { mutableStateOf<String?>(null) }
    val logListState = rememberLazyListState()

    val isServiceRunning by SmsEventBus.isRunning.collectAsState()
    val serviceProgress by SmsEventBus.progress.collectAsState()
    val serviceTotal by SmsEventBus.total.collectAsState()

    LaunchedEffect(Unit) { SmsEventBus.logs.collect { newLog -> logs = logs + newLog } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.SEND_SMS] == true && permissions[Manifest.permission.READ_PHONE_STATE] == true) {
            loadSims(context) { sims ->
                simCards = sims
                if (sims.isNotEmpty()) selectedSim = sims[0]
                autoNum = getAutoNumber(context, sims, selectedSim)
                scope.launch {
                    SmsEventBus.emitLog(strings.logInit.replace("...", ""), LogType.COMMAND)
                    delay(500)
                    SmsEventBus.emitLog(strings.logInit.replace("...", "") + " OK", LogType.INFO)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS))
    }

    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) logListState.animateScrollToItem(logs.size - 1) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        // (Text logo removed)

        Text(strings.creditsLeft.format(credits), color = if (credits > 0) PrimaryColor else LogError, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    Text("> ", color = PrimaryColor, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
                    Text(text = when (currentStep) { 1 -> strings.step1; 2 -> strings.step2; else -> strings.step3 }, color = PrimaryColor, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                AnimatedVisibility(visible = currentStep == 1) {
                    TerminalTextField(value = targetNumber, onValueChange = { targetNumber = it }, hint = if (autoNum != null) "${strings.autoNum} $autoNum" else strings.targetHint, isNumber = true)
                }

                AnimatedVisibility(visible = currentStep == 2) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (simCards.isEmpty()) {
                            Text(strings.noSim, color = LogError, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                                OutlinedTextField(value = selectedSim?.displayName ?: "", onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth(), shape = RoundedCornerShape(4.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryColor, unfocusedBorderColor = BorderColor, focusedTextColor = TextColor, unfocusedTextColor = TextColor), textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
                                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(PanelBg)) {
                                    simCards.forEach { sim -> DropdownMenuItem(text = { Text(sim.displayName, color = TextColor, fontFamily = FontFamily.Monospace) }, onClick = { selectedSim = sim; autoNum = getAutoNumber(context, simCards, sim); expanded = false }) }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = currentStep == 3) {
                    TerminalTextField(value = quantity, onValueChange = { quantity = it }, hint = strings.qtyHint, isNumber = true)
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (currentStep > 1) {
                        Button(onClick = { currentStep-- }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PrimaryColor), border = BorderStroke(1.dp, PrimaryColor), shape = RoundedCornerShape(4.dp)) { Text(strings.back, fontFamily = FontFamily.Monospace) }
                    }

                    Button(
                        onClick = {
                            if (currentStep < 3) { currentStep++ }
                            else {
                                val dest = if (targetNumber.isNotEmpty()) targetNumber else autoNum
                                if (dest == null) { SmsEventBus.emitLog("[ERR] Target is empty", LogType.ERROR); return@Button }
                                val qty = quantity.toIntOrNull() ?: 1
                                if (credits < qty) { Toast.makeText(context, strings.noCredits, Toast.LENGTH_SHORT).show(); return@Button }

                                scope.launch(Dispatchers.IO) {
                                    val deducted = consumeCredits(deviceId, qty)
                                    withContext(Dispatchers.Main) {
                                        if (!deducted) { Toast.makeText(context, strings.noCredits, Toast.LENGTH_SHORT).show(); return@withContext }
                                        onCreditsUpdate(credits - qty)
                                        val intent = Intent(context, SmsService::class.java).apply { putExtra("target", dest); putExtra("quantity", qty); putExtra("subId", selectedSim?.subId ?: 0) }
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                                            ContextCompat.startForegroundService(context, intent)
                                            saveHistory(context, dest, qty)
                                            currentStep = 1; targetNumber = ""; quantity = ""
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = DarkBg), shape = RoundedCornerShape(4.dp)
                    ) { Text(if (currentStep == 3) strings.execute else strings.next, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(strings.logTitle, color = HintColor, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 8.dp))
                LazyColumn(state = logListState, modifier = Modifier.fillMaxSize()) {
                    if (logs.isEmpty()) item { Text(strings.logReady, color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    items(logs) { log -> Text(text = "[${log.time}] ${log.msg}", color = when (log.type) { LogType.INFO -> LogInfo; LogType.ERROR -> LogError; LogType.WARN -> LogWarn; LogType.COMMAND -> LogCmd }, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
    }

    if (isServiceRunning) {
        AlertDialog(onDismissRequest = { }, title = { Text(strings.dlgTitle, fontFamily = FontFamily.Monospace, color = PrimaryColor) }, text = {
            Column { Text("${strings.dlgSending} $serviceProgress / $serviceTotal", color = TextColor, fontFamily = FontFamily.Monospace); Spacer(modifier = Modifier.height(16.dp)); LinearProgressIndicator(progress = { serviceProgress.toFloat() / serviceTotal.toFloat() }, modifier = Modifier.fillMaxWidth(), color = PrimaryColor, trackColor = BorderColor) }
        }, confirmButton = {}, containerColor = PanelBg, shape = RoundedCornerShape(8.dp))
    }
}

@Composable
fun TerminalTextField(value: String, onValueChange: (String) -> Unit, hint: String, isNumber: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().background(Color.Black, RoundedCornerShape(4.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("> ", color = PrimaryColor, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 16.sp), cursorBrush = SolidColor(PrimaryColor), modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (isNumber) KeyboardType.Number else KeyboardType.Phone), decorationBox = { innerTextField -> if (value.isEmpty()) Text(hint, color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 16.sp); innerTextField() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(strings: AppStrings) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf(loadHistory(context)) }
    var selectedItem by remember { mutableStateOf<HistoryItem?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("> ${strings.tabHistory}", color = PrimaryColor, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            if (historyList.isNotEmpty()) { IconButton(onClick = { saveHistoryList(context, emptyList()); historyList = emptyList() }) { Icon(Icons.Default.Delete, contentDescription = null, tint = LogError) } }
        }

        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(strings.histEmpty, color = HintColor, fontFamily = FontFamily.Monospace) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(historyList) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().clickable { selectedItem = item }) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(item.target, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = TextColor); Text("[${item.qty}x]", color = LogWarn, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(item.time, color = HintColor, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }

    if (selectedItem != null) {
        AlertDialog(onDismissRequest = { selectedItem = null }, title = { Text("> ${strings.histDetails}", fontFamily = FontFamily.Monospace, color = PrimaryColor) }, text = {
            Column(modifier = Modifier.background(Color.Black, RoundedCornerShape(4.dp)).padding(12.dp)) {
                Text("TARGET: ${selectedItem!!.target}", color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp); Spacer(modifier = Modifier.height(4.dp))
                Text("TIME  : ${selectedItem!!.time}", color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp); Spacer(modifier = Modifier.height(4.dp))
                Text("QTY   : ${selectedItem!!.qty}", color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            }
        }, confirmButton = { TextButton(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Phone", selectedItem!!.target)); Toast.makeText(context, strings.copied, Toast.LENGTH_SHORT).show(); selectedItem = null }) { Text(strings.histCopy, color = PrimaryColor, fontFamily = FontFamily.Monospace) } }, dismissButton = { TextButton(onClick = { selectedItem = null }) { Text(strings.histClose, color = HintColor, fontFamily = FontFamily.Monospace) } }, containerColor = PanelBg, shape = RoundedCornerShape(8.dp))
    }
}

@Composable
fun SettingsScreen(strings: AppStrings, currentLang: String, onLangChange: (String) -> Unit, credits: Int, deviceId: String, onCreditsUpdate: (Int) -> Unit, onAdminAuthSuccess: (String) -> Unit) {
    val context = LocalContext.current
    var showPanicDialog by remember { mutableStateOf(false) }

    var devIdClickCount by remember { mutableIntStateOf(0) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var adminPassInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("> ${strings.tabSettings}", color = PrimaryColor, fontSize = 18.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(strings.setLang, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextColor)
                Text(strings.setLangDesc, fontSize = 12.sp, color = HintColor, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onLangChange("en") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (currentLang == "en") PrimaryColor else Color.Transparent), border = BorderStroke(1.dp, if (currentLang == "en") PrimaryColor else BorderColor), shape = RoundedCornerShape(4.dp)) { Text("ENGLISH", color = if (currentLang == "en") DarkBg else TextColor, fontFamily = FontFamily.Monospace) }
                    Button(onClick = { onLangChange("fa") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (currentLang == "fa") PrimaryColor else Color.Transparent), border = BorderStroke(1.dp, if (currentLang == "fa") PrimaryColor else BorderColor), shape = RoundedCornerShape(4.dp)) { Text("فارسی", color = if (currentLang == "fa") DarkBg else TextColor, fontFamily = FontFamily.Monospace) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(strings.devInfo, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextColor)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Device ID: $deviceId", fontSize = 12.sp, color = HintColor, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { devIdClickCount++; if (devIdClickCount >= 5) { devIdClickCount = 0; showAdminLoginDialog = true } })
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; clipboard.setPrimaryClip(ClipData.newPlainText("Device ID", deviceId)); Toast.makeText(context, "Device ID Copied!", Toast.LENGTH_SHORT).show(); devIdClickCount++; if (devIdClickCount >= 5) { devIdClickCount = 0; showAdminLoginDialog = true } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = DarkBg)) { Text(strings.copyDevId, fontFamily = FontFamily.Monospace) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = PanelBg), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(strings.panicTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = LogError)
                Text(strings.panicDesc, fontSize = 12.sp, color = HintColor, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(bottom = 16.dp))
                Button(onClick = { showPanicDialog = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = LogError, contentColor = Color.White), shape = RoundedCornerShape(4.dp)) { Text(strings.panicConfirm, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showPanicDialog) {
        AlertDialog(onDismissRequest = { showPanicDialog = false }, title = { Text(strings.panicTitle, fontFamily = FontFamily.Monospace, color = LogError) }, text = { Text(strings.panicDesc, color = TextColor, fontFamily = FontFamily.Monospace) }, confirmButton = {
            TextButton(onClick = { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().clear().apply(); context.cacheDir.deleteRecursively(); Toast.makeText(context, "DATA WIPED", Toast.LENGTH_LONG).show(); val intent = context.packageManager.getLaunchIntentForPackage(context.packageName); intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); (context as? ComponentActivity)?.finish(); showPanicDialog = false }) { Text(strings.yes, color = LogError, fontFamily = FontFamily.Monospace) }
        }, dismissButton = { TextButton(onClick = { showPanicDialog = false }) { Text(strings.no, color = HintColor, fontFamily = FontFamily.Monospace) } }, containerColor = PanelBg, shape = RoundedCornerShape(8.dp))
    }

    if (showAdminLoginDialog) {
        AlertDialog(
            onDismissRequest = { showAdminLoginDialog = false; adminPassInput = "" },
            title = { Text("> ${strings.adminLoginTitle}", fontFamily = FontFamily.Monospace, color = LogWarn) },
            text = {
                Column {
                    Text(strings.adminPassHint, color = HintColor, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = adminPassInput, onValueChange = { adminPassInput = it }, visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextColor, unfocusedTextColor = TextColor))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (adminLogin(adminPassInput)) {
                            onAdminAuthSuccess(adminPassInput)
                            showAdminLoginDialog = false
                            adminPassInput = ""
                        } else {
                            Toast.makeText(context, strings.adminWrongPass, Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text(strings.adminLoginBtn, color = LogWarn, fontFamily = FontFamily.Monospace) }
            },
            dismissButton = { TextButton(onClick = { showAdminLoginDialog = false; adminPassInput = "" }) { Text(strings.no, color = HintColor, fontFamily = FontFamily.Monospace) } },
            containerColor = PanelBg
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(strings: AppStrings, adminPassword: String) {
    var currentSubTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(selectedTabIndex = currentSubTab, containerColor = PanelBg, contentColor = LogWarn, edgePadding = 0.dp) {
            Tab(selected = currentSubTab == 0, onClick = { currentSubTab = 0 }, text = { Text(strings.adminDashboard, fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
            Tab(selected = currentSubTab == 1, onClick = { currentSubTab = 1 }, text = { Text(strings.adminUsers, fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
            Tab(selected = currentSubTab == 2, onClick = { currentSubTab = 2 }, text = { Text(strings.adminTx, fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
            Tab(selected = currentSubTab == 3, onClick = { currentSubTab = 3 }, text = { Text(strings.adminPlans, fontFamily = FontFamily.Monospace, fontSize = 12.sp) })
        }
        Spacer(modifier = Modifier.height(16.dp))

        when (currentSubTab) {
            0 -> AdminDashboard(strings, adminPassword)
            1 -> AdminUsersTab(strings, adminPassword)
            2 -> AdminTransactionsTab(strings, adminPassword)
            3 -> AdminPlansTab(strings, adminPassword)
        }
    }
}

@Composable
fun AdminDashboard(strings: AppStrings, adminPassword: String) {
    var stats by remember { mutableStateOf<AdminStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun loadStats() {
        scope.launch {
            isLoading = true
            errorMsg = null
            val res = fetchAdminStats(adminPassword)
            if (res != null) stats = res else errorMsg = "Failed to connect to server. Check VPN/Internet."
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadStats() }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LogWarn) }
    } else if (errorMsg != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(errorMsg!!, color = LogError, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { loadStats() }, colors = ButtonDefaults.buttonColors(containerColor = LogWarn)) { Text("RETRY") }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("TOTAL USERS", color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp); Text("${stats!!.users}", color = PrimaryColor, fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold) } }
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("TOTAL REVENUE", color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp); Text("$${stats!!.revenue}", color = LogInfo, fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold) } }
            Card(colors = CardDefaults.cardColors(containerColor = PanelBg), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) { Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("SMS SENT", color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp); Text("${stats!!.sms}", color = LogWarn, fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersTab(strings: AppStrings, adminPassword: String) {
    var users by remember { mutableStateOf(listOf<AdminUser>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUser by remember { mutableStateOf<AdminUser?>(null) }
    var creditAmount by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refreshUsers() { scope.launch { users = fetchAdminUsers(adminPassword) } }
    LaunchedEffect(Unit) { refreshUsers() }
    val filteredUsers = users.filter { it.deviceId.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search Device ID...", color = HintColor) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextColor, unfocusedTextColor = TextColor))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(filteredUsers) { user ->
                Card(colors = CardDefaults.cardColors(containerColor = if (user.isBanned) Color(0xFF3B1111) else PanelBg), border = BorderStroke(1.dp, if (user.isBanned) LogError else BorderColor), modifier = Modifier.fillMaxWidth().clickable { selectedUser = user; creditAmount = user.credits.toString() }) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) { Text(user.deviceId, color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp); Text("Used: ${user.totalSms} SMS", color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                        Column(horizontalAlignment = Alignment.End) { Text("${user.credits} CR", color = PrimaryColor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); if (user.isBanned) Text("BANNED", color = LogError, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }

    if (selectedUser != null) {
        AlertDialog(
            onDismissRequest = { if (!isProcessing) selectedUser = null },
            title = { Text("Manage User", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(selectedUser!!.deviceId, color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    OutlinedTextField(value = creditAmount, onValueChange = { creditAmount = it }, label = { Text(strings.adminSetCredits) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextColor, unfocusedTextColor = TextColor), enabled = !isProcessing)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { isProcessing = true; val amt = creditAmount.toIntOrNull() ?: 0; if (adminUserAction(adminPassword, selectedUser!!.deviceId, "set_credits", amt)) { Toast.makeText(context, "Credits Updated", Toast.LENGTH_SHORT).show(); refreshUsers(); selectedUser = null } else Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show(); isProcessing = false } }, modifier = Modifier.weight(1f), enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = DarkBg)) { if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBg) else Text(strings.adminSave, fontSize = 12.sp) }
                        Button(onClick = { scope.launch { isProcessing = true; val action = if (selectedUser!!.isBanned) "unban" else "ban"; if (adminUserAction(adminPassword, selectedUser!!.deviceId, action)) { Toast.makeText(context, if (action == "ban") "User Banned" else "User Unbanned", Toast.LENGTH_SHORT).show(); refreshUsers(); selectedUser = null } else Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show(); isProcessing = false } }, modifier = Modifier.weight(1f), enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = LogWarn, contentColor = DarkBg)) { Text(if (selectedUser!!.isBanned) strings.adminUnban else strings.adminBanned, fontSize = 12.sp) }
                    }
                    Button(onClick = { scope.launch { isProcessing = true; if (adminUserAction(adminPassword, selectedUser!!.deviceId, "delete")) { Toast.makeText(context, "User Deleted", Toast.LENGTH_SHORT).show(); refreshUsers(); selectedUser = null } else Toast.makeText(context, "Failed", Toast.LENGTH_SHORT).show(); isProcessing = false } }, modifier = Modifier.fillMaxWidth(), enabled = !isProcessing, colors = ButtonDefaults.buttonColors(containerColor = LogError, contentColor = Color.White)) { Text(strings.adminDelete, fontSize = 12.sp) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { if (!isProcessing) selectedUser = null }) { Text(strings.no, color = HintColor) } },
            containerColor = PanelBg
        )
    }
}

@Composable
fun AdminTransactionsTab(strings: AppStrings, adminPassword: String) {
    var transactions by remember { mutableStateOf(listOf<AdminTransaction>()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { scope.launch { transactions = fetchAdminTransactions(adminPassword); isLoading = false } }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = LogWarn) }
    } else if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No transactions found.", color = HintColor, fontFamily = FontFamily.Monospace) }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(transactions) { tx ->
                val statusColor = when(tx.status) { "finished", "confirmed" -> LogInfo; "failed", "expired" -> LogError; else -> LogWarn }
                Card(colors = CardDefaults.cardColors(containerColor = PanelBg), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(tx.deviceId, color = TextColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("$${tx.amount}", color = LogInfo, fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(tx.createdAt, color = HintColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp); Text(tx.status.uppercase(), color = statusColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPlansTab(strings: AppStrings, adminPassword: String) {
    var plans by remember { mutableStateOf(listOf<AdminPlan>()) }
    var selectedPlan by remember { mutableStateOf<AdminPlan?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refreshPlans() { scope.launch { plans = fetchAdminPlans(adminPassword) } }
    LaunchedEffect(Unit) { refreshPlans() }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
        items(plans) { plan ->
            Card(
                colors = CardDefaults.cardColors(containerColor = if (plan.isActive) PanelBg else Color(0xFF1A1A1A)),
                border = BorderStroke(1.dp, if (plan.isActive) BorderColor else LogError),
                modifier = Modifier.fillMaxWidth().clickable { selectedPlan = plan }
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(plan.name, color = if (plan.isActive) TextColor else HintColor, fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("$${plan.price} - ${plan.credits} SMS", color = if (plan.isActive) PrimaryColor else HintColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                    if (!plan.isActive) Text("INACTIVE", color = LogError, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (selectedPlan != null) {
        var editName by remember { mutableStateOf(selectedPlan!!.name) }
        var editPrice by remember { mutableStateOf(selectedPlan!!.price.toString()) }
        var editCredits by remember { mutableStateOf(selectedPlan!!.credits.toString()) }
        var editActive by remember { mutableStateOf(selectedPlan!!.isActive) }

        AlertDialog(
            onDismissRequest = { if (!isProcessing) selectedPlan = null },
            title = { Text("Edit Plan: ${selectedPlan!!.id}", fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text(strings.adminPlanName) }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextColor, unfocusedTextColor = TextColor), enabled = !isProcessing)
                    OutlinedTextField(value = editPrice, onValueChange = { editPrice = it }, label = { Text(strings.adminPlanPrice) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextColor, unfocusedTextColor = TextColor), enabled = !isProcessing)
                    OutlinedTextField(value = editCredits, onValueChange = { editCredits = it }, label = { Text(strings.adminPlanCredits) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextColor, unfocusedTextColor = TextColor), enabled = !isProcessing)

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = editActive, onCheckedChange = { editActive = it }, enabled = !isProcessing)
                        Text(strings.adminPlanActive, color = TextColor, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isProcessing = true
                                val p = editPrice.toFloatOrNull() ?: selectedPlan!!.price
                                val c = editCredits.toIntOrNull() ?: selectedPlan!!.credits
                                if (updateAdminPlan(adminPassword, selectedPlan!!.id, editName, p, c, editActive)) {
                                    Toast.makeText(context, "Plan Updated", Toast.LENGTH_SHORT).show()
                                    refreshPlans()
                                    selectedPlan = null
                                } else {
                                    Toast.makeText(context, "Failed to update", Toast.LENGTH_SHORT).show()
                                }
                                isProcessing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProcessing,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor, contentColor = DarkBg)
                    ) {
                        if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DarkBg) else Text(strings.adminSave, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { if (!isProcessing) selectedPlan = null }) { Text(strings.no, color = HintColor) } },
            containerColor = PanelBg
        )
    }
}

fun loadSims(context: Context, callback: (List<SimCard>) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    val sims = try {
        sm.activeSubscriptionInfoList?.map { SimCard(it.subscriptionId, it.simSlotIndex, "SIM ${it.simSlotIndex + 1} (${it.displayName})") } ?: emptyList()
    } catch (_: SecurityException) { emptyList() }
    callback(sims)
}

@Suppress("DEPRECATION")
fun getAutoNumber(context: Context, sims: List<SimCard>, selected: SimCard?): String? {
    if (sims.isEmpty()) return null
    val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    for (sim in sims) {
        if (sim.subId == selected?.subId) continue
        try {
            val info = sm.getActiveSubscriptionInfo(sim.subId)
            if (Build.VERSION.SDK_INT >= 33) {
                val num = sm.getPhoneNumber(sim.subId)
                if (!num.isNullOrEmpty()) return num
            }
            if (!info?.number.isNullOrEmpty()) return info?.number
        } catch (_: Exception) {}
    }
    return null
}

fun saveHistory(context: Context, target: String, qty: Int) {
    val list = loadHistory(context).toMutableList()
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
    list.add(HistoryItem(UUID.randomUUID().toString(), time, target, qty))
    saveHistoryList(context, list)
}

fun loadHistory(context: Context): List<HistoryItem> {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString("history", "[]")
    val type = object : TypeToken<List<HistoryItem>>() {}.type
    return try { Gson().fromJson(json, type) } catch (_: Exception) { emptyList() }
}

fun saveHistoryList(context: Context, list: List<HistoryItem>) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString("history", Gson().toJson(list)) }
}