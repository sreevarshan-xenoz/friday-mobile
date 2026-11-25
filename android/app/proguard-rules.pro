# Keep Rust core reachable through JNI
-keep class dev.pocketdesk.android.bridge.** { *; }

# WorkManager builds its Room database by reflection (the no-argument
# constructor of WorkDatabase_Impl). R8 removed it in the release build, and
# the app stopped at launch: "NoSuchMethodException: WorkDatabase_Impl.<init>".
-keep class * extends androidx.room.RoomDatabase { <init>(); }
