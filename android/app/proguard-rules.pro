# 目前 release 不开混淆；保留 kotlinx.serialization 的序列化器，避免以后开混淆踩坑
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class site.seaunit.shuati.** {
    *** Companion;
}
