# Preserve launcher class names and public/protected methods for crash reports.
# Shrink dependency names and private implementation details to fit the APK budget.
-keepnames,allowoptimization class com.gathin.tvhome.**
-keepclassmembernames,allowoptimization class com.gathin.tvhome.** { public *; protected *; }

# Shizuku instantiates this service by its class name.
-keep class com.gathin.tvhome.CpuUserService { *; }
