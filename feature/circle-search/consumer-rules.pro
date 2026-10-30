# Vosk reaches libvosk through JNA reflection and native method registration; R8 must keep both.
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.* { *; }
-dontwarn com.sun.jna.**
-dontwarn org.vosk.**
