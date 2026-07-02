-keep class com.adobs.ide.core.security.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class org.eclipse.jgit.** { *; }
-dontwarn org.eclipse.jgit.**