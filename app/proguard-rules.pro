# video2gif R8/ProGuard 规则(参考 RenaAI-Android 模式)。
# Compose / AndroidX / Media3 各自带 consumer 规则,无需额外 keep;
# 这里只处理:堆栈可读性、JNI、ffmpeg-kit。

# 保留行号便于线上堆栈定位;隐藏原始文件名。
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 类重打包到顶层,缩短名字、减小体积。
-repackageclasses

# JNI:native 方法按名字注册,本体与所在类名都不能动。
-keepclasseswithmembernames class * { native <methods>; }

# ffmpeg-kit(com.moizhassan.ffmpeg fork,Java 包名仍为 com.arthenica.*):
# .so 经 JNI 按名字回调 Java 类/方法(FFmpegKitConfig、Session 回调、statistics 等),
# AAR 不带 consumer 规则,必须整包 keep。
-keep class com.arthenica.** { *; }
-dontwarn com.arthenica.**
