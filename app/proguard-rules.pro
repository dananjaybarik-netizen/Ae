# AI STUDIO ProGuard / R8 Keep Rules

# Preserve Kotlinx Serialization metadata & generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.model.**$$serializer { *; }
-keepclassmembers class com.example.model.** {
    *** Companion;
}

# Preserve Room database & DAO implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Preserve Media3 Transformer & GL Effect classes
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
