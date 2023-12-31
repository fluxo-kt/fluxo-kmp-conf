
###
# ProGuard/R8 assumenosideeffects rules
###

# Specifies methods that don't have any side effects (other than maybe returning a value).
# In the optimization step, ProGuard will then remove calls to such methods,
# if it can determine that the return values aren't used.

# https://www.guardsquare.com/en/products/proguard/manual/usage#assumenosideeffects
# https://www.guardsquare.com/en/products/proguard/manual/usage#classspecification
# https://www.guardsquare.com/en/products/proguard/manual/examples#logging

# http://www.alexeyshmalko.com/2014/proguard-real-world-example/#conclusion
# https://github.com/abgoyal/OT_4030X/blob/1cc51cbe346b0ed161957509f5004a1ace13161c/external/proguard/src/proguard/gui/boilerplate.pro#L93


-assumenosideeffects class org.slf4j.*, ** extends org.slf4j.* {
    *** trace(...);
}
-assumenoexternalsideeffects class java.lang.StringBuilder {
    public java.lang.StringBuilder();
    public java.lang.StringBuilder(int);
    public java.lang.StringBuilder(java.lang.String);
    public java.lang.StringBuilder append(java.lang.Object);
    public java.lang.StringBuilder append(java.lang.String);
    public java.lang.StringBuilder append(java.lang.StringBuffer);
    public java.lang.StringBuilder append(char[]);
    public java.lang.StringBuilder append(char[], int, int);
    public java.lang.StringBuilder append(boolean);
    public java.lang.StringBuilder append(char);
    public java.lang.StringBuilder append(int);
    public java.lang.StringBuilder append(long);
    public java.lang.StringBuilder append(float);
    public java.lang.StringBuilder append(double);
    public java.lang.String toString();
}
-assumenoexternalreturnvalues public final class java.lang.StringBuilder {
    public java.lang.StringBuilder append(java.lang.Object);
    public java.lang.StringBuilder append(java.lang.String);
    public java.lang.StringBuilder append(java.lang.StringBuffer);
    public java.lang.StringBuilder append(char[]);
    public java.lang.StringBuilder append(char[], int, int);
    public java.lang.StringBuilder append(boolean);
    public java.lang.StringBuilder append(char);
    public java.lang.StringBuilder append(int);
    public java.lang.StringBuilder append(long);
    public java.lang.StringBuilder append(float);
    public java.lang.StringBuilder append(double);
}


-assumenosideeffects class java.lang.System {
    *** getenv(...);
    int identityHashCode(java.lang.Object);
    java.lang.Class getCallerClass();
    java.lang.SecurityManager getSecurityManager();
    java.lang.String getProperty(...);
    java.lang.String lineSeparator();
    java.lang.String mapLibraryName(java.lang.String);
    java.util.Properties getProperties();
    long currentTimeMillis();
    long nanoTime();
}

-assumenosideeffects class java.lang.Math, java.lang.StrictMath {
    *** abs(...);
    *** max(...);
    *** min(...);
    *** round(...);
    *** signum(...);
    *** ulp(...);
    *** acos(...);
    *** asin(...);
    *** atan(...);
    *** atan2(...);
    *** cbrt(...);
    *** ceil(...);
    *** cos(...);
    *** cosh(...);
    *** exp(...);
    *** expm1(...);
    *** floor(...);
    *** hypot(...);
    *** IEEEremainder(...);
    *** log(...);
    *** log10(...);
    *** log1p(...);
    *** pow(...);
    *** random(...);
    *** rint(...);
    *** sin(...);
    *** sinh(...);
    *** sqrt(...);
    *** tan(...);
    *** tanh(...);
    *** toDegrees(...);
    *** toRadians(...);
}

-assumenosideeffects class java.lang.Character, java.lang.Boolean, java.lang.Number, ** extends java.lang.Number {
    <init>(...);
    *** bitCount(...);
    *** compareTo(...);
    *** decode(java.lang.String);
    *** highestOneBit(...);
    *** lowestOneBit(...);
    *** numberOfLeadingZeros(...);
    *** numberOfTrailingZeros(...);
    *** reverse(...);
    *** reverseBytes(...);
    *** rotateLeft(...);
    *** rotateRight(...);
    *** signum(...);
    *** valueOf(...);
    boolean booleanValue();
    boolean equals(java.lang.Object);
    boolean isInfinite(...);
    boolean isNaN(...);
    boolean parseBoolean(...);
    byte byteValue();
    byte parseByte(...);
    char charValue(...);
    double doubleValue();
    double longBitsToDouble(long);
    double parseDouble(java.lang.String);
    float floatValue();
    float intBitsToFloat(int);
    float parseFloat(java.lang.String);
    int compare(...);
    int floatToIntBits(float);
    int floatToRawIntBits(float);
    int hashCode();
    int intValue();
    int parseInt(...);
    java.lang.Integer getInteger(...);
    java.lang.Long getLong(...);
    java.lang.String to*String(...);
    java.lang.String toString(...);
    long doubleToLongBits(double);
    long doubleToRawLongBits(double);
    long longValue();
    long parseLong(...);
    short parseShort(...);
    short shortValue();
}

-assumenosideeffects class java.lang.Character {
    *** codePoint*(...);
    *** to*Case(...);
    boolean is*(...);
}

-assumenosideeffects class java.lang.Boolean {
    boolean logical*(...);
}

-assumenosideeffects class java.lang.CharSequence, ** implements java.lang.CharSequence {
    <init>(...);
    *** format(...);
    boolean contentEquals(java.lang.StringBuffer);
    boolean endsWith(java.lang.String);
    boolean equals(java.lang.Object);
    boolean equalsIgnoreCase(java.lang.String);
    boolean matches(java.lang.String);
    boolean regionMatches(...);
    boolean startsWith(...);
    byte getBytes(...);
    char charAt(int);
    char toCharArray();
    int compareTo(...);
    int compareToIgnoreCase(java.lang.String);
    int hashCode();
    int indexOf(...);
    int lastIndexOf(...);
    int length();
    java.lang.CharSequence subSequence(int,int);
    java.lang.String concat(java.lang.String);
    java.lang.String copyValueOf(...);
    java.lang.String replace(...);
    java.lang.String replace*(...);
    java.lang.String split(...);
    java.lang.String substring(...);
    java.lang.String toLowerCase(...);
    java.lang.String toString();
    java.lang.String toUpperCase(...);
    java.lang.String trim();
    java.lang.String valueOf(...);

    int capacity();
    int codePoint*(int);
}

-assumenosideeffects class java.lang.Iterable, ** implements java.lang.Iterable {
    java.util.Iterator iterator(...);
    *** spliterator(...);
}
-assumenosideeffects class java.util.Collection, ** implements java.util.Collection {
    *** toArray(...);
    boolean contains(...);
    boolean containsAll(...);
    boolean isEmpty();
    int size();
    int hashCode();
    boolean equals(java.lang.Object);
    *** stream(...);
    *** parallelStream(...);
}
-assumenosideeffects class java.util.List, ** implements java.util.List {
    *** get(...);
    *** listIterator(...);
    int indexOf(...);
    int lastIndexOf(...);
    java.util.List subList(int, int);
}
-assumenosideeffects class java.util.Map, ** implements java.util.Map {
    *** get(...);
    boolean contains*(...);
    boolean isEmpty();
    int size();
    java.util.Collection values();
    java.util.Set entrySet();
    java.util.Set keySet();
}
-assumenosideeffects class java.util.Queue, ** implements java.util.Queue {
    *** element();
    *** peek();
}
-assumenosideeffects class java.util.Deque, ** implements java.util.Deque {
    *** getFirst();
    *** getLast();
    *** peekFirst();
    *** peekLast();
    java.util.Iterator descendingIterator();
}
