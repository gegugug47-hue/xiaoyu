# ==========================================================================
# M5-06：release 构建的 R8 / 资源收缩保留规则
# 原则：只保留运行期通过反射、序列化、注解处理器访问的符号，其余照常裁剪混淆。
# 注意：Room / Hilt / Compose / OkHttp 自身已带 consumer rules，这里是兜底补强。
# ==========================================================================

# 崩溃栈可读性：保留行号与源文件名（不保留原始文件名，避免泄露目录结构）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 注解、泛型签名、内部类信息：Room / Hilt / kotlinx.serialization 都依赖
-keepattributes Signature,InnerClasses,EnclosingMethod,AnnotationDefault,*Annotation*

# Kotlin 元数据（反射读取 suspend 信息、可空性、默认参数）
-keep class kotlin.Metadata { *; }
-keepclassmembers class **$WhenMappings {
    <fields>;
}

# --------------------------------------------------------------------------
# kotlinx.serialization：序列化器由编译器生成，靠反射 + 伴生对象获取，不能被裁剪
# --------------------------------------------------------------------------
-keepclassmembers class **$$serializer {
    <fields>;
}
-keep,includedescriptorclasses class com.xiaoyu.**$$serializer { *; }
-keepclassmembers class com.xiaoyu.** {
    *** Companion;
}
-keepclasseswithmembers class com.xiaoyu.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable 类自身的伴生对象访问入口
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# --------------------------------------------------------------------------
# Room：数据库实现类在编译期生成，入口必须保留
# --------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class * {
    *;
}
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# --------------------------------------------------------------------------
# Hilt / Dagger：注解处理器生成的组件与成员注入入口
# --------------------------------------------------------------------------
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
# ViewModel 由反射创建
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
# 生成代码里的 Module / Component 需要保留类名（Hilt 的 consumer rules 之外的兜底）
-keep,allowobfuscation @interface dagger.Module
-keep,allowobfuscation @interface dagger.Component

# --------------------------------------------------------------------------
# Compose：运行时靠 lambda 静态字段与合成类工作，仅保留必要入口
# --------------------------------------------------------------------------
-keepclassmembers class androidx.compose.ui.platform.ComposeView {
    <init>(...);
}
-keepclassmembers class androidx.compose.runtime.internal.ComposableLambdaImpl {
    *;
}

# --------------------------------------------------------------------------
# OkHttp / Okio：可选 TLS 实现与平台检测通过反射完成，缺失时不应报错
# --------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.slf4j.**

# --------------------------------------------------------------------------
# 其他：JSON / 网络层常见的可选依赖告警
# --------------------------------------------------------------------------
-dontwarn kotlinx.serialization.**
-dontwarn javax.annotation.**
