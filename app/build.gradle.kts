plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// OneDrive can lock generated test directories. Script builds keep all outputs local.
providers.gradleProperty("povBuildRoot").orNull?.let { layout.buildDirectory.set(file(it)) }

android {
    namespace = "dev.pov.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.pov.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.2-preview"
        testInstrumentationRunner = "dev.pov.tv.PlaybackAuditInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*") }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.8.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.8.0")
    implementation("androidx.media3:media3-ui:1.8.0")
    implementation("androidx.media3:media3-session:1.8.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
}
