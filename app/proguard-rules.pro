# Release builds currently ship unminified (isMinifyEnabled = false in app/build.gradle.kts).
# This file exists because the release build type already references it; the rules below
# are the starting point for the day R8 minification is enabled (#6).

# The game is small enough that keeping its classes whole costs a negligible amount of
# APK size and removes every reflection risk (Firestore save models, Compose internals).
-keep class com.skalagrimmo.crucibleofruin.** { *; }

# Firebase Auth / Firestore ship their own consumer rules; nothing extra is required.

# If minification is enabled later, re-check that the object-pooled particle/projectile/
# damage-number game loop stays allocation-free, and that CloudSave models still round-trip.
