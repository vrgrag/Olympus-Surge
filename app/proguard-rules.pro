# ==========================================================================
# ProGuard / R8 rules for the release build.
#
# Design principle: keep the minimum surface area, let R8 shrink and rename
# everything else with its default (short-name) obfuscation. Do NOT swap in
# a custom obfuscation dictionary — mangled unicode names are one of the
# fastest ways to trip Play Protect's suspicious-package heuristics.
# ==========================================================================

# --- WebView JavaScript bridge -------------------------------------------
# Methods annotated with @JavascriptInterface are looked up by NAME from
# page-side JS (window.NectarInput.focus(top, bottom)). If R8 renamed
# `focus` the JS call would throw "focus is not a function".
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- kotlinx.serialization ------------------------------------------------
# The @Serializable classes (OracleReply, others we may add later) generate
# a synthetic Companion.serializer() method that the runtime reaches via
# reflection when calling `json.decodeFromString(OracleReply.serializer(), raw)`.
# Keep the generated serializer classes and their Companion serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.olympussurge.game.atrium.network.**$$serializer { *; }
-keepclassmembers class com.olympussurge.game.atrium.network.** {
    *** Companion;
}
-keepclasseswithmembers class com.olympussurge.game.atrium.network.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- AppsFlyer ------------------------------------------------------------
# The AAR ships its own consumer-proguard-rules; kept here as a safety
# net for the callback listener interface, which we implement inline.
-keep class com.appsflyer.** { *; }
-keep interface com.appsflyer.** { *; }
-dontwarn com.appsflyer.**

# --- Firebase / Google Play Services --------------------------------------
# Both ship consumer-proguard-rules but we keep the messaging surface
# explicitly — FCM reaches into our service via reflection.
-keep class com.google.firebase.** { *; }
-keep interface com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- OkHttp ---------------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Our own shell ---------------------------------------------------------
# Application / Activities / Services / Providers are kept by AGP because
# they are referenced from the manifest. Nothing else needs a -keep — the
# router talks by static Kotlin references, not by class name, so R8 is
# free to obfuscate the shell into short names.

# The JS bridge inner class must be reachable by the bytecode generator
# for @JavascriptInterface reflection; the class member rule above covers
# the method, but we also pin the class name.
-keep class com.olympussurge.game.atrium.LyreInputRider$Bridge { *; }

# --- libGDX ---------------------------------------------------------------
# libGDX uses reflection for its input processors and native class lookup.
-keep class com.badlogic.** { *; }
-dontwarn com.badlogic.**

# --- Kotlin runtime -------------------------------------------------------
-dontwarn kotlin.**
-dontwarn kotlinx.**
