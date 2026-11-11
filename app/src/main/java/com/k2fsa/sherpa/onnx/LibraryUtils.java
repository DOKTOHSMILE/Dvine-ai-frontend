// Ibiko: the desktop loader of sherpa-onnx's Java API, replaced for Android, where the libraries ship in the app
// (src/main/jniLibs/arm64-v8a: libsherpa-onnx-jni.so and libonnxruntime.so, from the v1.13.8 Android release).
package com.k2fsa.sherpa.onnx;

public class LibraryUtils {
    public static void load() {
        System.loadLibrary("onnxruntime");
        System.loadLibrary("sherpa-onnx-jni");
    }
}
