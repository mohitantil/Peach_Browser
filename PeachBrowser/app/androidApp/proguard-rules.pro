# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepattributes *Annotation*
-keepattributes JavascriptInterface

# Preserve WebKit JavaScript Interface bridge methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclasseswithmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve Peach Native JavaScript Bridge methods explicitly
-keepclassmembers class com.example.peachbrowser.webview.PeachNativeBridge {
    <methods>;
}

# Preserve Compose Multiplatform & Android Compose runtime
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keep class peachbrowser.app.shared.generated.resources.** { *; }

# Preserve SQLDelight Database entities and driver
-keep class com.example.peachbrowser.db.** { *; }
-keep class app.cash.sqldelight.** { *; }

# Preserve Koin Dependency Injection
-keep class io.insert-koin.** { *; }
