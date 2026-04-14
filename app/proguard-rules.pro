# Add project specific ProGuard rules here.

# Keep Protocol18 parser classes
-keep class com.albion.radar.parser.** { *; }
-keep class com.albion.radar.entity.** { *; }
-keep class com.albion.radar.service.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Optimization settings
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
