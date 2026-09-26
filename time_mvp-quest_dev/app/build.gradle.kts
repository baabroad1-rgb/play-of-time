import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// ── Auto-versioning ────────────────────────────────────────────────────────────
// Version is stored in app/version.properties and auto-incremented after every
// successful assembleDebug / assembleRelease.
//   versionCode  = int (Android requires this; +1 each build)
//   versionBase  = fixed prefix like "0.10" (edit by hand when ready for a bump)
//   versionPatch = last/patch digit (auto-incremented; only this number changes)
// Resulting versionName = "<versionBase>.<versionPatch>", e.g. "0.10.5".
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use(::load)
}
val readVersionCode: Int = (versionProps.getProperty("versionCode") ?: "1").toInt()
val readVersionBase: String = versionProps.getProperty("versionBase") ?: "0.10"
val readVersionPatch: Int = (versionProps.getProperty("versionPatch") ?: "0").toInt()
val readVersionName: String = "$readVersionBase.${"%02d".format(readVersionPatch)}"

android {
    namespace = "ru.iflex.timemvp"
    compileSdk = 36

    defaultConfig {
        applicationId = "ru.iflex.timemvp.quest"
        minSdk = 27
        targetSdk = 36
        versionCode = readVersionCode
        versionName = readVersionName

        // Дата релиза = дата сборки (About-экран показывает версию + дату).
        val buildDate = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // R8 на debug ОТКЛЮЧЁН (Alex 2026-08-25, отменяет решение от 14 июля —
            // см. предыдущую версию этого комментария в истории CLAUDE.md). Причина:
            // на POI/xmlbeans/commons в proguard-rules.pro висит блок-правило
            // `-keep ... { *; }` (защита от бага v12.33–v12.37 с log4j при экспорте
            // в Excel) — а POI и есть главный вес APK (~34 из ~46 MB дексов). R8
            // физически не может ужать ни один класс POI ни в каком режиме, так что
            // 10-минутная стоимость сборки почти ничего не давала по факту. Заодно
            // снимает остаточный риск R8 "assumed to be unreachable" мисшринка на
            // debug (см. WARNING в логе сборки про SVGUserAgent.getViewbox()) —
            // debug-сборка теперь безопаснее для итеративного тестирования, а не
            // просто быстрее. RELEASE не тронут — там minify/shrink/POI-защита
            // работают как прежде, влияет только на локально собираемый debug APK.
            // Стопвотч-латентность по-прежнему устранена архитектурно (см.
            // perf_stopwatch_latency.md) — R8 тут ни при чём независимо от minify.
            isMinifyEnabled   = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    // Исключаем ресурсы, которые Apache POI тащит в APK, но которые НЕ
    // используются нашим простым XLSX-экспортом (только числовые ячейки,
    // без фигур, без шаблонов таблиц, без пивот-таблиц). Экономия ~1.5 MB.
    packaging {
        resources {
            excludes += listOf(
                "org/apache/poi/sl/draw/geom/presetShapeDefinitions.xml",
                "org/apache/poi/xssf/usermodel/presetTableStyles.xml",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/*.kotlin_module"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true   // BuildConfig.VERSION_NAME / BUILD_DATE для About-экрана
    }

    applicationVariants.all {
        val variant = this
        outputs.map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                output.outputFileName = "play-of-time-v${variant.versionName}.apk"
            }
    }
}

// Bump versionCode (+1) and versionPatch (+1) after a successful debug/release
// APK build. Patch is reserved for 01..99 — once it WOULD exceed 99, roll over
// to the next MAJOR version (versionBase +1) and reset patch to 01 automatically
// (Alex 2026-08-11: this used to be a manual-only step and got missed — patch
// drifted to 100, 101, 102 past a versionBase bump that should've happened at 99).
// doLast only runs when the task actually executes, so UP-TO-DATE builds don't
// trigger a bump.
afterEvaluate {
    val bump: org.gradle.api.Task.() -> Unit = {
        doLast {
            val nextCode = readVersionCode + 1
            var nextBase = readVersionBase
            var nextPatch = readVersionPatch + 1
            if (nextPatch > 99) {
                nextBase = ((readVersionBase.toIntOrNull() ?: 0) + 1).toString()
                nextPatch = 1
            }
            val nextName = "$nextBase.${"%02d".format(nextPatch)}"
            versionProps.setProperty("versionCode",  nextCode.toString())
            versionProps.setProperty("versionBase",  nextBase)
            versionProps.setProperty("versionPatch", nextPatch.toString())
            versionPropsFile.outputStream().use { out ->
                versionProps.store(out, "Auto-bumped after assemble")
            }
            println("Version bumped → code=$nextCode name=$nextName")
        }
    }
    tasks.findByName("assembleDebug")?.bump()
    tasks.findByName("assembleRelease")?.bump()
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.kotlinx.serialization.json)

    // Jetpack Compose
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(libs.androidx.activity.compose)

    // Material Design 3
    implementation(libs.androidx.material3)

    // Google Fonts (Inter)
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.3")

    // Compose Navigation
    implementation(libs.androidx.navigation.compose)

    // Android Studio Preview support
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    // Koin
    val koinBom = platform("io.insert-koin:koin-bom:3.5.6")
    implementation(koinBom)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room
    implementation(libs.room)
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)
    ksp(libs.room.compiler)

    //Apache
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    implementation("org.apache.commons:commons-collections4:4.4")
}