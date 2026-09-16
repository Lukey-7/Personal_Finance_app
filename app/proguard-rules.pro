# Keep Room entities (reflection-free, but safe)
-keep class com.pft.financetracker.data.local.** { *; }
# Strip all logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Tink (used by androidx.security.crypto) references compile-time-only annotations.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# SQLCipher uses JNI; keep its classes intact.
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
