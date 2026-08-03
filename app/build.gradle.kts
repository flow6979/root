import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Read GROQ_API_KEY from local.properties (never committed) or the environment.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// Public build: `./gradlew assembleRelease -PpublicBuild` strips the PAID secret keys
// (Groq, ElevenLabs) so the APK is safe to publish. The Supabase ANON key is intentionally
// kept - it is a public client key protected by row-level security, designed to ship in apps.
// In a public build, built-in AI falls back to offline demo unless the user adds their own
// Gemini key (You -> AI); voice output uses the free no-key TTS.
val publicBuild = project.hasProperty("publicBuild")

val groqKey: String = if (publicBuild) "" else (localProps.getProperty("GROQ_API_KEY")
    ?: System.getenv("GROQ_API_KEY") ?: "").trim()
val supabaseUrl: String = (localProps.getProperty("SUPABASE_URL")
    ?: System.getenv("SUPABASE_URL") ?: "").trim()
val supabaseAnonKey: String = (localProps.getProperty("SUPABASE_ANON_KEY")
    ?: System.getenv("SUPABASE_ANON_KEY") ?: "").trim()
val elevenKey: String = if (publicBuild) "" else (localProps.getProperty("ELEVENLABS_API_KEY")
    ?: System.getenv("ELEVENLABS_API_KEY") ?: "").trim()

// Backend proxy (see /proxy). These are NOT secrets - the URL is public and the app token is
// only a light gate - so they ship in every build (including -PpublicBuild). When set, the app
// calls the proxy (which holds the real keys) instead of shipping Groq/ElevenLabs keys.
val proxyBaseUrl: String = (localProps.getProperty("PROXY_BASE_URL")
    ?: System.getenv("PROXY_BASE_URL") ?: "").trim()
val proxyAppToken: String = (localProps.getProperty("PROXY_APP_TOKEN")
    ?: System.getenv("PROXY_APP_TOKEN") ?: "").trim()

val releaseStoreFile: String? = localProps.getProperty("RELEASE_STORE_FILE")

android {
    namespace = "com.rootapp"
    compileSdk = 34

    signingConfigs {
        if (releaseStoreFile != null && rootProject.file(releaseStoreFile).exists()) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "com.rootapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 25
        versionName = "0.3.8"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GROQ_API_KEY", "\"$groqKey\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "ELEVENLABS_API_KEY", "\"$elevenKey\"")
        buildConfigField("String", "PROXY_BASE_URL", "\"$proxyBaseUrl\"")
        buildConfigField("String", "PROXY_APP_TOKEN", "\"$proxyAppToken\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // ---- Unit tests (JVM, runnable via ./gradlew testDebugUnitTest) ----
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")

    // ---- Instrumented / Compose UI tests (device or Robolectric) ----
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
