# Keep line numbers for readable crash traces
-keepattributes SourceFile,LineNumberTable

# Media3 / Coil / Room ship their own consumer rules.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
