# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile


# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keep interface org.tensorflow.lite.** { *; }
-keep class com.example.demoapp.** { *; }

# TensorFlow Lite specific rules
-keep class org.tensorflow.lite.Interpreter { *; }
-keep class org.tensorflow.lite.Interpreter$Options { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.metadata.** { *; }
-dontwarn org.tensorflow.lite.**

# AutoValue
-keep class com.google.auto.value.** { *; }
-keep interface com.google.auto.value.** { *; }
-dontwarn com.google.auto.value.**

# DCM4CHE
-keep class org.dcm4che.** { *; }
-keep interface org.dcm4che.** { *; }
-dontwarn org.dcm4che.**

# Java ImageIO (needed for DCM4CHE)
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn java.awt.image.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep generic signatures
-keepattributes Signature

# Keep exceptions
-keepattributes Exceptions

# Keep annotations
-keepattributes *Annotation*

# Keep source file names for debugging
-keepattributes SourceFile,LineNumberTable

# Keep the application class
-keep public class * extends android.app.Application

# Keep Activity classes
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.preference.Preference
-keep public class * extends android.view.View
-keep public class * extends android.app.Fragment

# Keep View constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepnames class * implements java.io.Serializable

# Keep R classes
-keep class **.R$* {
    public static <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Additional TensorFlow Lite safety rules
-keep class org.tensorflow.lite.Interpreter$Options {
    public void setNumThreads(int);
    public void setUseNNAPI(boolean);
    public void addDelegate(org.tensorflow.lite.Delegate);
}

# Keep all TensorFlow Lite delegate classes
-keep class * implements org.tensorflow.lite.Delegate { *; }

# Keep TensorFlow Lite metadata classes
-keep class org.tensorflow.lite.metadata.** { *; }

# Keep TensorFlow Lite support classes
-keep class org.tensorflow.lite.support.** { *; }
-keep interface org.tensorflow.lite.support.** { *; }

# Prevent R8 from removing TensorFlow Lite native libraries
-keep class org.tensorflow.lite.NativeInterpreterWrapper { *; }
-keep class org.tensorflow.lite.NativeInterpreterWrapperExperimental { *; }