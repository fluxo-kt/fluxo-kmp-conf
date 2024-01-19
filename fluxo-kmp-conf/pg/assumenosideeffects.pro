
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

# Note: -assumenosideeffects should not be used with constructors!
# It leads to bugs like https://sourceforge.net/p/proguard/bugs/702/
# Using -assumenoexternalsideeffects is fine though and should be used for that purpose.

# Note: -assumenoexternalsideeffects and -assumenoexternalreturnvalues are not supported by R8 and ignored!


# Remove Kotlin intrinsic assertions.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);

# maybe not safe, needs testing
#	public static void checkNotNull(...);
#	public static void throwUninitializedPropertyAccessException(...);
#	public static void throwNpe(...);
#	public static void throwJavaNpe(...);
#	public static void throwAssert(...);
#	public static void throwIllegalArgument(...);
#	public static void throwIllegalState(...);
}


-assumenosideeffects class org.slf4j.*, ** extends org.slf4j.* {
    *** trace(...);
    boolean isTraceEnabled(...);
}
-assumevalues class org.slf4j.*, ** extends org.slf4j.* {
    boolean isTraceEnabled(...) return false;
}

-assumenosideeffects class java.lang.Character {
    *** codePoint*(...);
    *** to*Case(...);
    boolean is*(...);
}

-assumenosideeffects class java.lang.Boolean {
    boolean logical*(...);
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
