# native / llama.cpp

The app's JNI library is `dvine_llm`, built from this directory by
`app/build.gradle` → `externalNativeBuild { cmake { path … } }`.

```
CMakeLists.txt            top level: options, add_subdirectory(llama.cpp), dvine_llm
dvine_llm.cpp             the model runner (ggml + llama, KV cache, sampling)
dvine_llm_jni.cpp         Java entry points: open / stop / decode
llama.cpp/                llama.cpp b4748 (ggml/ tree: ggml.h, ggml-common.h, ggml-alloc, ggml-backend, …)
```

## What is built

Only two third-party trees are compiled:

* `llama.cpp/ggml` — the tensor library (`ggml`, `ggml-base`, `ggml-cpu`).
* `llama.cpp` — the `llama` library.

Everything else in llama.cpp is off: tools, tests, examples, the server, the
unified binary and `common`, plus every backend except the CPU one
(`GGML_CUDA`, `GGML_METAL`, `GGML_VULKAN`, `GGML_OPENCL`, … are all `OFF`).

Nothing outside this directory is needed to build it: ggml vendors its own
dependencies (`ggml/src/ggml-cpu/llamafile`, `ggml/src/ggml-cpu/kleidiai`, …)
and llama.cpp's vendored helpers are not built (see `llama.cpp/vendor/`).

## Build (from Android Studio or `./gradlew :app:assembleDebug`)

```
arm64-v8a   armeabi-v7a   x86   x86_64
```

arm64 builds with `-march=armv8.2-a+dotprod+fp16` (KleidiAI kernels off: they pinned every core);
the other ABIs build the plain ARMv7/NEON or x86 paths. The Kotlin side
checks `android.cpu.features` (ARMv8.2 dotprod / AES) before loading
`libdvine_llm.so`.

## Where ggml comes from

ggml is vendored inside `llama.cpp/ggml`. `CMakeLists.txt` only sets the
options and calls `add_subdirectory(llama.cpp)`; llama.cpp finds no `ggml`
target yet (`if (NOT TARGET ggml …)`) and adds its own `ggml/` with those
options. There is no `ggml/` folder beside `CMakeLists.txt`, so it must not be
added here (0.96.2 tried, and the native build stopped configuring).
