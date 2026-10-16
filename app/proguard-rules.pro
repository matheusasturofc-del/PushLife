# Regras do R8 para a versão de publicação.

# MediaPipe (detector de poses): usa JNI e protobuf por reflexão.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.auto.value.**
-dontwarn javax.annotation.**

# TensorFlow Lite (MoveNet).
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
