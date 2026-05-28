-keep class androidx.wear.watchface.** { *; }
-keep class com.iosync.watchface.** { *; }
-keep class com.google.android.gms.wearable.** { *; }

# Health Services API
-keep class androidx.health.services.client.** { *; }
-keep class com.google.android.gms.fitness.** { *; }

# Guava (ListenableFuture für Health Services)
-keep class com.google.common.util.concurrent.** { *; }
-keep class com.google.guava.** { *; }

-dontwarn kotlin.**
-dontwarn com.google.common.**
-dontwarn javax.annotation.**
