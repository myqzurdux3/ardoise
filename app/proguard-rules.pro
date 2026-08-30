# kotlinx.serialization keeps generated serializers reachable via reflection.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class fr.ardoise.tasks.** {
    *** Companion;
}
-keepclasseswithmembers class fr.ardoise.tasks.** {
    kotlinx.serialization.KSerializer serializer(...);
}
