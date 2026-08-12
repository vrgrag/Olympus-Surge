# ================================================================
# Olympus Surge — Proguard / R8 keep rules
# ================================================================
# See .cursor/rules/oracle_pitfalls.md §1 for the rationale behind
# every -keep block below. If R8 strips any of these classes, the
# AppsFlyer callbacks stop firing, Firebase Messaging throws, or the
# manifest can't resolve the FirebaseMessagingService.

# ── AppsFlyer ────────────────────────────────────────────────
-keep class com.appsflyer.** { *; }
-keep class com.android.installreferrer.** { *; }
-dontwarn com.appsflyer.**

# ── Firebase ────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── OkHttp ──────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ── kotlinx.serialization ───────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keep,includedescriptorclasses class com.olympussurge.game.oracle.model.**$$serializer { *; }
-keepclassmembers class com.olympussurge.game.oracle.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.olympussurge.game.oracle.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Oracle classes referenced from the manifest ─────────────
-keep class com.olympussurge.game.oracle.OracleRuntime { *; }
-keep class com.olympussurge.game.oracle.gateway.OracleTokenService { *; }
-keep class com.olympussurge.game.oracle.OraclePortalActivity { *; }
-keep class com.olympussurge.game.oracle.OracleGateActivity { *; }
-keep class com.olympussurge.game.oracle.OracleInviteActivity { *; }
-keep class com.olympussurge.game.oracle.OracleOfflineActivity { *; }

# ── WebView JS interface (none currently, but safe defaults) ─
-keepattributes JavascriptInterface
