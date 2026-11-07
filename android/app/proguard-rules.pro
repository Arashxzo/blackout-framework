# ============================================================
# BLACKOUT — ProGuard / R8 Rules
# ============================================================
# Required because `minifyEnabled true` is set in build.gradle.
# ============================================================

# --- Gson: HistoryItem is serialized/deserialized via reflection ---
-keep class com.arashxzo.blackout.HistoryItem { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# --- OkHttp / Okio (networking to Cloudflare Worker) ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# --- Kotlin Metadata (reflection safety) ---
-keep class kotlin.Metadata { *; }

# --- Jetpack Compose ---
-dontwarn androidx.compose.**

# --- BuildConfig (contains BACKEND_URL at runtime) ---
-keep class com.arashxzo.blackout.BuildConfig { *; }