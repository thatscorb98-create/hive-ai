# Serialization and OkHttp
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.hive.agents.**$$serializer { *; }
-keepclassmembers class com.hive.agents.** { *** Companion; }
-keepclasseswithmembers class com.hive.agents.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn okhttp3.**
-dontwarn okio.**
