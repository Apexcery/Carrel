# The libraries Carrel uses (kotlinx.serialization, Ktor, supabase-kt, Room) ship their own shrinking rules.

# ML Kit (the barcode scanner) makes its component registrars by reflection. Firebase's own rule keeps only their names,
# and R8's full mode drops their constructors with it, so the scanner crashed as it opened.
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
