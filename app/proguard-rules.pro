# Room entities/DAOs are referenced by generated code; preserve database implementations.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }

# Gson serializes the backup manifest by field name.
-keepattributes Signature,*Annotation*
-keep class com.minis.invoicevault.BackupManifest { <fields>; }
-keep class com.minis.invoicevault.WebDavConfig { <fields>; }
-keep class com.minis.invoicevault.RemoteBackup { <fields>; }

# XML parser entry points and bundled document-recognition engines.
-keep class com.minis.invoicevault.XmlInvoiceParser { *; }
-keep class com.minis.invoicevault.WebDavClient { *; }
-dontwarn org.apache.fontbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn com.gemalto.jp2.**
-keep class org.apache.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
