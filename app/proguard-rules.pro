# Room and Media3 publish their consumer rules.

# DefaultRenderersFactory loads the optional software audio renderer by name.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { public <init>(...); }

# LibVLC looks up its Java API from JNI, so these classes must retain their
# original names and members in minified release builds.
-keep class org.videolan.libvlc.** { *; }
-keep interface org.videolan.libvlc.** { *; }

# JNA accesses native mapping fields and callback methods by reflection.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.Callback { *; }
# JNA's desktop window helpers are unused on Android.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
