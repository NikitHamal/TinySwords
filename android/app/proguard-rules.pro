# Tiny Swords RTS — release proguard rules.
# Compose runtime is preserved automatically via consumer rules; Android keeps
# kotlinx Coroutines stable. We additionally retain our game data classes so
# JSON persistence using their qualified names keeps working after R8.

-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

-keep class com.tinyswords.realmwar.game.** { *; }
-keep class com.tinyswords.realmwar.storage.** { *; }
-dontwarn org.jetbrains.annotations.**
