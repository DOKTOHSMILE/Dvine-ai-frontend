# sherpa-onnx Java API (vendored)

The Java classes of sherpa-onnx v1.13.8 (https://github.com/k2-fsa/sherpa-onnx, `sherpa-onnx/java-api`), copied
unchanged except `LibraryUtils`, which loads the libraries the Android way. The JNI library reads these classes'
fields by name, so they must stay as they are and match `src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so` (same
release). Copyright Xiaomi Corporation and the sherpa-onnx authors, Apache License 2.0
(`app/src/main/assets/licenses/SHERPA_ONNX_LICENSE.txt`). Ibiko uses only the text-to-speech classes.
