# Tiny Swords ProGuard rules
-keepattributes SourceFile,LineNumberTable
-keep class com.tinyswords.data.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keep,includedescriptorclasses class com.tinyswords.**$$serializer { *; }
-keepclassmembers class com.tinyswords.** {
    *** Companion;
}
-keepclasseswithmembers class com.tinyswords.** {
    kotlinx.serialization.KSerializer serializer(...);
}
