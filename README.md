# Turnip Space

Run apps and games inside a **managed virtual space** (no root, no system
modifications) and apply **custom Adreno Turnip Vulkan drivers** from
AdrenoTools-compatible `.zip` files to everything launched through it.

Turnip Space is an honest engineering project: the onboarding screen and the
[study document](docs/ESTUDO-E-LIMITACOES.md) state exactly what is and is not
possible on modern Android. No overpromising.

## What it does

- **Virtual spaces** — create, list, open and remove spaces; each hosts one
  user-provided APK (loaded as a plugin into the app-controlled process,
  Parallel-Space style, using `DexClassLoader` + `Instrumentation` hooks).
- **Turnip driver manager** — import AdrenoTools driver zips (tolerant
  `meta.json` parser + crash-safe ELF `.dynsym` validation), select a driver
  per space, and preload it into the process through the native bridge
  (`dlopen` via JNI) before the hosted app's native code runs.
- **Adreno GPU detection** — EGL pbuffer probe (`GL_RENDERER`) with SoC/KGSL
  fallbacks; suggests compatible drivers (a6xx/a7xx/a8xx only).
- **Optional Shizuku** — extended GPU diagnostics; the app degrades
  gracefully (no crash) when Shizuku is absent or permission is denied.
- **Robust error handling** — typed results for every failure (invalid APK,
  broken driver, missing ICD symbols, hook failures), an in-app ring-buffer +
  file logger, a crash handler that records stack traces, and log export.

## What it cannot do (read this)

- It **cannot** force a Turnip driver into an arbitrary installed app that was
  never built for it. Drivers only apply inside this app's own process.
- Universal compatibility with every Play Store APK is **not guaranteed**
  (heavy native engines are the hard case), especially on Android 13+.
- Turnip supports **Adreno 6xx/7xx/8xx only** — Adreno 5xx or older keeps the
  proprietary driver.

## Build

Open in Android Studio (JDK 17) or run:

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # unsigned release APK
```

CI: [.github/workflows/build.yml](.github/workflows/build.yml) builds both
variants on every push to `main` and publishes them as workflow artifacts.

## Requirements

- Android 8.0+ (minSdk 26); best results on Android 9–13
- arm64-v8a or armeabi-v7a device with an Adreno GPU for custom drivers

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — module map, engine design, known gaps
- [docs/ESTUDO-E-LIMITACOES.md](docs/ESTUDO-E-LIMITACOES.md) — full study
  (pt-BR): AdrenoTools internals, VirtualApp techniques, Android restrictions,
  driver zip format, compatibility matrix
