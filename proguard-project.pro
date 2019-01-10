# This is a configuration file for ProGuard.
# http://proguard.sourceforge.net/index.html#manual/usage.html

-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose

# Optimization is turned off by default. Dex does not like code run
# through the ProGuard optimize and preverify steps (and performs some
# of these optimizations on its own).
-dontoptimize
-dontpreverify
# Note that if you want to enable optimization, you cannot just
# include optimization flags in your own project configuration file;
# instead you will need to point to the
# "proguard-android-optimize.txt" file instead of this one from your
# project.properties file.

-keepattributes *Annotation*
# -keep public class com.google.vending.licensing.ILicensingService
# -keep public class com.android.vending.licensing.ILicensingService

# For native methods, see http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames class * {
    native <methods>;
}

# keep setters in Views so that animations can still work.
# see http://proguard.sourceforge.net/manual/examples.html#beans
-keepclassmembers public class * extends android.view.View {
   void set*(***);
   *** get*();
}

# We want to keep methods in Activity that could be used in the XML attribute onClick
-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# The support library contains references to newer platform versions.
# Don't warn about those in case this app is linking against an older
# platform version.  We know about them, and they are safe.
-dontwarn android.support.**

-dontobfuscate

-dontwarn org.mozilla.javascript.tools.**
-dontwarn net.fortuna.ical4j.**
-dontwarn org.mozilla.javascript.xml.**
-dontwarn jsint.**
-dontwarn jlib.**
-dontwarn interact.**
-dontwarn jschemeweb.**
-dontwarn jscheme.bsf.**
-dontwarn edu.emory.mathcs.backport.java.util.concurrent.helpers.**
-dontwarn org.apache.commons.math3.geometry.euclidean.**
-dontwarn sun.tools.javac.Main
-dontwarn sun.tools.jar.Main
-dontwarn sun.rmi.rmic.Main
-dontwarn javax.xml.bind.DatatypeConverter
-dontwarn org.apache.commons.codec.binary.Base64
-dontwarn edu.northwestern.cbits.**
-dontwarn com.google.android.maps.**
-dontwarn java.nio.file.**
-dontwarn org.codehaus.mojo.**
-dontwarn org.ejml.ops.**
-dontwarn javax.swing.**
-dontwarn javax.tools.**
-dontwarn java.awt.**
-dontwarn com.sun.jdi.**
-dontwarn com.sun.jdi.**
-dontwarn java.applet.**
-dontwarn java.awt.**
-dontwarn sun.misc.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn edu.emory.mathcs.**

-dontwarn okio.**
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn okhttp3.**
-dontwarn com.samsung.android.knox.**

-dontnote jlib.**
-dontnote android.net.http.*
-dontnote org.apache.commons.**
-dontnote org.apache.http.**
-dontnote com.facebook.**
-dontnote com.fasterxml.**
-dontnote com.google.android.**
-dontnote org.mozilla.**
-dontnote org.msgpack.**
-dontnote com.getpebble.**
-dontnote edu.northwestern.cbits.purple_robot_manager.**
-dontnote edu.northwestern.cbits.xsi.**
-dontnote net.hockeyapp.**
-dontnote com.squareup.okhttp.**
-dontnote okhttp3.**

-keep class edu.northwestern.cbits.** { *; }
-keep class jscheme.** { *; }
-keep class jsint.** { *; }
-keep class org.mozilla.** { *; }
-keep class org.scribe.** { *; }

-keep class org.apache.http.** { *; }
-keepclassmembers class org.apache.http.** {*;}
-dontwarn org.apache.**

-keep class android.net.http.** { *; }
-keepclassmembers class android.net.http.** {*;}
-dontwarn android.net.**

# -keep class org.apache.commons.** { *; }

# Allow obfuscation of android.support.v7.internal.view.menu.**
# to avoid problem on Samsung 4.2.2 devices with appcompat v21
# see https://code.google.com/p/android/issues/detail?id=78377
-keep class !android.support.v7.internal.view.menu.**,android.support.** {*;}