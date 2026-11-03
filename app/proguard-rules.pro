# kotlinx.serialization: keep generated serializers for content models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.korimbekef.reading.**$$serializer { *; }
-keepclassmembers class com.korimbekef.reading.** {
    *** Companion;
}
-keepclasseswithmembers class com.korimbekef.reading.** {
    kotlinx.serialization.KSerializer serializer(...);
}
