# R8 rules for the release build. The libraries bring their own (kotlinx.serialization, Retrofit, OkHttp, Room,
# Hilt); add rules here only for what a crash or a missing class shows R8 removed.

# Readable stack traces in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
