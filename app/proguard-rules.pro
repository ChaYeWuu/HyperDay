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

# Shizuku / Stellar UserService: the server instantiates this class by name
# through reflection (getDeclaredConstructor().newInstance()) inside its own
# process. R8 has no static reference to that constructor, so without these
# rules it either strips the no-arg constructor or the class itself and the
# bind fails with NoSuchMethodException: <obfuscated>.<init> [].
-keep class com.chayewuu.hypermatter.shizuku.PrivilegedServiceImpl { *; }
-keep class com.chayewuu.hypermatter.shizuku.IPrivilegedService { *; }
-keep interface com.chayewuu.hypermatter.shizuku.IPrivilegedService { *; }

# Firewall API reached purely through reflection (INetworkManagementService /
# IConnectivityManager hidden methods). Keep the names so getMethod() matches.
-keep class android.os.INetworkManagementService$Stub { *; }
-keep class android.os.INetworkManagementService$Stub$Proxy { *; }
-keep class android.net.IConnectivityManager$Stub { *; }
-keep class android.net.IConnectivityManager$Stub$Proxy { *; }
