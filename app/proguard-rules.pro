-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.zenlock.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.zenlock.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
