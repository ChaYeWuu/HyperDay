# HyperDay R8 rules.
# Most dependencies (kotlinx-serialization, Compose, Miuix, Kyant0 backdrop)
# ship their own consumer rules in META-INF/proguard; these cover the rest.

# kotlinx.serialization: keep generated serializers for @Serializable models.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep line numbers for readable release crash stacks (no source file names).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
