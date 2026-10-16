# Keep class and method names so stack traces in Musix logs stay readable; code is still shrunk and optimised.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature

# Settings, mixes and songs are stored as JSON with kotlinx.serialization.
-keep,includedescriptorclasses class app.musix.**$$serializer { *; }
-keepclassmembers class app.musix.** { *** Companion; }
-keepclasseswithmembers class app.musix.** { kotlinx.serialization.KSerializer serializer(...); }

# The FFmpeg audio renderer is created by name and calls into native code.
-keep class androidx.media3.decoder.ffmpeg.** { *; }

# Background mix generation is started by class name.
-keep class app.musix.mix.MixWorker { <init>(...); }

-dontwarn org.jetbrains.annotations.**
-dontwarn javax.annotation.**
