# 保持MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# 保持WebView相关
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
