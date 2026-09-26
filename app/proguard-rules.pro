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

# Shizuku (issue #4): the shell UserService is started by the Shizuku server through its class name, so
# it must not be renamed or stripped, and the server-side binder interfaces it names must stay.
-keep class io.github.kvmy666.duostatusbar.settings.ShellService { public <init>(...); }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# WorkManager instantiates the worker by name; keep it resolvable across updates.
-keep class io.github.kvmy666.duostatusbar.settings.UpdateWorker { public <init>(...); }
