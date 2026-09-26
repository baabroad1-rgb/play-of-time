# ── R8 keep rules for Play of Time (Alex 2026-07-13) ──────────────────────────
# Debug и release оба идут через R8 shrinking. Правила ниже — минимальные,
# чтобы Koin / Room / Compose / Apache POI выжили после агрессивной оптимизации.

# ── Общие: имена файлов и строки для отладочных стек-трейсов ─────────────
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-renamesourcefileattribute SourceFile

# ── Kotlin ────────────────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# ── Kotlinx serialization ────────────────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keep,includedescriptorclasses class ru.iflex.timemvp.**$$serializer { *; }
-keepclassmembers class ru.iflex.timemvp.** {
    *** Companion;
}
-keepclasseswithmembers class ru.iflex.timemvp.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Compose ───────────────────────────────────────────────────────────────
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-dontwarn androidx.compose.**

# ── Koin ──────────────────────────────────────────────────────────────────
-keep class ru.iflex.timemvp.features.**.MainViewModel { *; }
-keep class ru.iflex.timemvp.features.**.SettingsViewModel { *; }
-keep class ru.iflex.timemvp.features.**.StatsViewModel { *; }
-keep class ru.iflex.timemvp.features.**.AboutViewModel { *; }
-keep class ru.iflex.timemvp.core.** { *; }
-keep class ru.iflex.timemvp.database.** { *; }
-keep class ru.iflex.timemvp.features.**.domain.** { *; }
-keep class ru.iflex.timemvp.use_case.** { *; }
-keep class ru.iflex.timemvp.KoinModule { *; }
-keep class ru.iflex.timemvp.KoinModuleKt { *; }

# ── Room ──────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# ── Apache POI (XSSF Excel export) ────────────────────────────────────────
# POI тонны reflection'а, dynamic classloading через ClassInstantiator/
# serviceloader'ы. Keep класс-графа целиком — R8 всё равно даст выигрыш
# через ресурсы и другие библиотеки.
-keep class org.apache.poi.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.** { *; }
-keep class schemasMicrosoftCom** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep interface org.apache.poi.** { *; }
-keep interface org.openxmlformats.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.**
-dontwarn com.microsoft.schemas.**
-dontwarn schemasMicrosoftCom**
-dontwarn javax.xml.**

# log4j-api — РЕАЛЬНАЯ runtime-зависимость POI 5.x (static logger в
# POIXMLDocumentPart.<clinit>). AbstractLogger создаёт DefaultFlowMessageFactory
# через Class.newInstance() — R8 сохранял класс, но вырезал его no-arg
# конструктор → InstantiationException → ExceptionInInitializerError →
# «Export failed: ...XSSFWorkbook» (баг v12.33–v12.37, найден по logcat).
-keep class org.apache.logging.log4j.** { *; }
-dontwarn org.apache.logging.log4j.**

# Отсутствующие транзитивные зависимости POI/xmlbeans — эти классы никогда
# не подтягиваются в runtime нашим кодом. Игнорируем предупреждения R8.
-dontwarn net.sf.saxon.**
-dontwarn org.osgi.**
-dontwarn org.slf4j.**
-dontwarn java.awt.**
-dontwarn org.junit.**
-dontwarn junit.**
-dontwarn org.bouncycastle.**
-dontwarn com.zaxxer.sparsebits.**
-dontwarn de.rototor.pdfbox.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.brotli.**
-dontwarn com.graphbuilder.**
-dontwarn com.github.**
-dontwarn org.aspectj.**
-dontwarn org.jetbrains.annotations.**

# Apache Commons (POI dep)
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# ── ViewModel factory reflection ──────────────────────────────────────────
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
