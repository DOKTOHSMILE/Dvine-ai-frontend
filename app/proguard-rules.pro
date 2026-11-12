# R8 rules for release builds.

# Native code looks these up by name over JNI: renaming or removing them crashes at start.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class ai.ibiko.llm.NativeLlm { *; }
-keep class ai.ibiko.linux.Pty { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Readable crash traces in CrashLog
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
