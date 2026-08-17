# Default ProGuard rules for the release build.
# Health Connect and OkHttp ship consumer rules via their AARs; keep defaults.
-keepattributes Signature
-keepattributes *Annotation*

# kotlinx.serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1>$<2> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
