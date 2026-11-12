# R8 rules for release builds.

# Native code looks these up by name over JNI: renaming or removing them crashes at start.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class ai.ibiko.llm.NativeLlm { *; }
-keep class ai.ibiko.linux.Pty { *; }
# ibiko_llm_jni.cpp calls onText(String) by name on whatever object is passed as the sink
-keepclassmembers class * implements ai.ibiko.llm.NativeLlm$Sink {
    boolean onText(java.lang.String);
}
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Readable crash traces in CrashLog
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
