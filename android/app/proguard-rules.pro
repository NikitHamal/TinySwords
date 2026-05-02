# Keep Compose runtime metadata
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep game saved state classes
-keep class com.tinyswords.realmwar.data.** { *; }
-keep class com.tinyswords.realmwar.game.save.** { *; }

# AndroidX
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
