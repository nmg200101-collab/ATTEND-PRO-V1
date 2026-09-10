# ATTEND-PRO 1.9.82 release obfuscation rules.
# R8 obfuscation remains enabled; only runtime metadata required by Android/Kotlin is kept.
-keepattributes RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Android component entry points are discovered from the manifest by AGP/R8.
# Keep FileProvider from AndroidX intact.
-keep class androidx.core.content.FileProvider { *; }

# JourneyApps / ZXing use camera resources and reflection internally in some versions.
-dontwarn com.google.zxing.**
-dontwarn com.journeyapps.barcodescanner.**
