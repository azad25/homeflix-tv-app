# ---- HomeFlix "safe shrink" (default release build) ----
# Full R8 (optimize + shrink everything) crashed on TVs in 5.6, and a crash at
# start-up would also stop the self-updater from fixing it. So the default
# release build only *removes* unused code from the Material icons library
# (~2,000 icons, the app draws ~25): plain static drawing code, no reflection.
# Every other class is kept exactly as compiled: no optimisation, no renaming.
-dontoptimize
-dontobfuscate
-keep class !androidx.compose.material.icons.**, ** { *; }
-keepattributes *
-dontwarn **
