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

# pdfbox-android (PDF statements): JPEG-2000 images need an optional decoder that is not shipped; such images are
# skipped. The library loads resources and some classes by name, so keep it intact.
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
