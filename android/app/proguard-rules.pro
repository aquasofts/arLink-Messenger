# NearLink Messenger ProGuard/R8 rules

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.nearlink.messenger.**$$serializer { *; }
-keepclassmembers class com.nearlink.messenger.** {
    *** Companion;
}

# Hilt / Dagger generated
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# OkHttp / Okio platform internals
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Tink reflection
-keep class com.google.crypto.tink.proto.** { *; }
-dontwarn com.google.crypto.tink.proto.**

# Compose tooling on release
-dontwarn androidx.compose.ui.tooling.**

# Don't strip the Application class
-keep class com.nearlink.messenger.NearLinkApp { *; }

# Keep ViewModels accessible by reflection
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(...); }
