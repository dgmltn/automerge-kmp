# Applied to every consumer's R8 run. JNA finds fields, methods and native symbols by their source
# names from native code, which R8 can't see and renames, so without these the whole binding fails
# in release builds only (first as "Can't obtain peer field ID", then NoClassDefFoundError for
# com.sun.jna.Native on every call). The jna artifact ships no rules of its own, so the library that
# brings JNA in has to.

# Native code reads JNA's own fields by name, so members must survive, not just class names.
-keep class com.sun.jna.** { *; }

# JNA marshals a Structure field by field, by name and declaration order.
-keepclassmembers class * extends com.sun.jna.Structure { *; }

# Library method names are the symbols JNA looks up, and Callbacks are invoked from native code.
-keep class * implements com.sun.jna.Library { *; }
-keep class * implements com.sun.jna.Callback { *; }

# The generated binding is a thin FFI shim in which nearly every type is reached from a Structure,
# Library or Callback, so there is little to shrink and much to break. It is all in this one
# package; subpackages belong to consumers and are theirs to shrink.
-keep class com.dgmltn.automerge.* { *; }

# JNA refers to desktop AWT types that Android lacks and that are never reached there.
-dontwarn java.awt.**
