# Entry points are instantiated by name from assets/xposed_init — never rename or strip them.
-keep class io.github.kvmy666.duostatusbar.MainHook { *; }
-keep class io.github.kvmy666.duostatusbar.probe.** { *; }
-keep class io.github.kvmy666.duostatusbar.hook.** { *; }

# Rive runtime is reached through JNI: keep everything it reflects on.
-keep class app.rive.** { *; }
-dontwarn app.rive.**

# Xposed bridge
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**
