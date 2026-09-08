# Turnip Space keeps minification disabled for v1 to keep reflection-based
# plugin code (ActivityThread/Instrumentation hooks) safe from R8 renaming.
# If minification is enabled later, keep the rules below.

-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# JNI bridge classes: native methods must not be renamed.
-keepclasseswithmembernames class * {
    native <methods>;
}

-keep class io.github.deivid22srk.turnipspace.virtual.** { *; }
-keep class io.github.deivid22srk.turnipspace.data.gpu.** { *; }
-keep class io.github.deivid22srk.turnipspace.data.driver.** { *; }
