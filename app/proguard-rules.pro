-keep class org.eclipse.jdt.** { *; }
-keep class com.android.tools.r8.** { *; }
-keep class org.autojs.plugin.jvmsource.api.** { *; }
-keep class org.autojs.plugin.jvmsource.java.worker.** { *; }
-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature,*Annotation*

# ECJ's JSR-199/annotation-processing and Ant adapters are desktop-only. The provider uses the
# batch compiler entry point on Android and deliberately does not expose annotation processing.
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
-dontwarn javax.tools.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.eclipse.jdt.core.**
-dontwarn org.eclipse.jdt.internal.compiler.ISourceElementRequestor**

# D8 bundles optional desktop diagnostics/keep-annotation paths that are unreachable on Android.
-dontwarn com.android.tools.r8.keepanno.annotations.**
-dontwarn com.sun.management.**
-dontwarn java.lang.management.**
-dontwarn javax.management.**
-dontwarn javax.xml.stream.**

# The common plugin API carries this source-retention annotation in metadata only.
-dontwarn kotlinx.parcelize.Parcelize
