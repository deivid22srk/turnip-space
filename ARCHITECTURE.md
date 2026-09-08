# Architecture

Turnip Space follows a Clean-ish MVVM layout with an explicit native layer.
All persistence is file-based JSON inside the app sandbox — no external
storage writes, no root.

```
┌─────────────────────────────────────────────────────────────┐
│ UI (Jetpack Compose / Material 3)                           │
│ onboarding · home · space detail · driver manager · settings│
│ ViewModels (AndroidViewModel + StateFlow)                   │
└──────────────┬──────────────────────────────────────────────┘
               │ domain/UseCases (single entry surface)
┌──────────────▼──────────────────────────────────────────────┐
│ domain/                                                     │
│ Models.kt (VirtualSpace, DriverInfo, LaunchResult…)         │
│ UseCases.kt (create/delete space, install APK, import/del   │
│              driver, select driver, launch, GPU, Shizuku)   │
└──────────────┬──────────────────────────────────────────────┘
┌──────────────▼──────────────────────────────────────────────┐
│ data/                                                       │
│ space/SpaceRepository  (spaces index + per-space dirs)      │
│ space/ApkInstaller     (SAF copy → metadata → native libs)  │
│ driver/DriverRepository(import → extract → ELF validate)    │
│ driver/DriverZipParser (meta.json tolerant parser)          │
│ driver/NativeDriverLoader (JNI bridge → dlopen)             │
│ gpu/GpuDetector + GpuProbe (EGL pbuffer GL_RENDERER probe)  │
│ shizuku/ShizukuManager (optional, degrades gracefully)      │
└──────────────┬──────────────────────────────────────────────┘
┌──────────────▼──────────────────────────────────────────────┐
│ virtual/  (the "virtual space" engine)                      │
│ PluginApk (DexClassLoader + Resources 30+/legacy fallback)  │
│ ActivityThreadHook + PluginInstrumentation                 │
│ StubActivity[1-4] (installed stub components)               │
│ VirtualEngine (launch orchestration, typed results)         │
└──────────────┬──────────────────────────────────────────────┘
┌──────────────▼──────────────────────────────────────────────┐
│ native (C/C++, CMake, NDK 27) — libturniploader.so          │
│ turnip_loader.cpp: dlopen(RTLD_NOW|RTLD_LOCAL) + ICD symbol │
│                    validation, handles kept open (no dlclose│
│                    of registered Vulkan ICDs)               │
│ gpu_probe.cpp: 1x1 EGL pbuffer → glGetString(GL_RENDERER)   │
└─────────────────────────────────────────────────────────────┘
```

## Storage layout (app sandbox only)

```
files/
├── logs/app.log (+rotated)          # AppLogger
├── drivers/
│   ├── index.json                   # driver registry
│   └── <uuid>/                      # extracted zip (incl. libvulkan_*.so)
└── spaces/
    ├── index.json                   # space registry
    └── <uuid>/
        ├── app/base.apk             # verbatim user APK
        ├── lib/<abi>/*.so           # extracted native libs
        └── cache/optimized/         # DexClassLoader odex output
```

## Launch sequence (VirtualEngine.launch)

1. Guard: space has an installed APK and a launchable activity.
2. Load `PluginApk`: `DexClassLoader(base.apk, optimizedDir, libDir, hostCL)`
   + resources (API 30+ `ResourcesProvider`; legacy `addAssetPath` fallback).
3. Driver preload: if a driver is selected, `NativeDriverLoader.openDriver()`
   dlopens `libvulkan_freedreno.so` from the sandbox and validates the ICD
   entrypoints. Failure is reported (warning + continue with system Vulkan).
4. Install `PluginInstrumentation` into `ActivityThread.mInstrumentation`
   (reflection, guarded; cached after first success).
5. Start the stub component for the space with the target activity in extras.
6. When the framework asks for the stub, `PluginInstrumentation.newActivity()`
   swaps in the plugin activity class via the plugin ClassLoader. Lifecycle
   then proceeds normally; if the swap fails, the real stub renders a
   diagnostic screen — the process never dies silently.

## Design decisions & honest limitations

- **Why stub intents instead of AMS spoofing:** hooking
  `IActivityManager`/`mH` broke repeatedly on Android 9+ (`ClientTransaction`).
  Launching real installed stub components avoids the entire AMS proxy layer
  at the cost of requiring per-space stub instances. Documented trade-off.
- **Plugin context is the host context.** v1 does not transparently swap
  `LoadedApk`/`Resources` inside `ContextImpl.attach`. Apps relying on their
  own resources during `attachBaseContext` may fail — surfaced as a typed
  error, never a crash. Production path: hook `LoadedApk` construction
  (VirtualApp technique) or use `Instrumentation.callActivityOnCreate`
  wrapping with a themed plugin context.
- **dlopen redirection:** transparent `dlopen("libvulkan.so")` interception
  requires PLT/inline hooking (bhook/Dobby) or the official
  `libadrenotools.so`. v1 preloads the driver and keeps the C bridge shaped
  like AdrenoTools' usage so the real library can be linked without changing
  call sites.
- **Validation never dlopens untrusted code.** Import-time checks are pure
  Kotlin ELF parsing; the runtime `dlopen` happens only on explicit launch
  and its result is always surfaced to the UI.
- **Handles are never `dlclose`d** (same policy as AdrenoTools hosts):
  unregistering a Vulkan ICD from a live process is unsafe.
- **Threading:** all repository/engine work runs on `Dispatchers.IO`;
  the EGL probe runs on `Dispatchers.Default`. Nothing blocking on main.
- **Crash policy:** global uncaught-exception handler logs the stack trace
  before delegating to the platform handler; every user-facing failure maps
  to a typed error key in `strings.xml` (EN + pt-BR).
