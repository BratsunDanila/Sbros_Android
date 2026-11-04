import java.util.Properties
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val developmentProperties=Properties().apply {val file=rootProject.file("local.properties");if(file.isFile)file.inputStream().use {load(it)}}
fun configValue(name: String)=developmentProperties.getProperty(name)?.takeIf{it.isNotBlank()}?:System.getenv(name)?:""
fun quoted(value: String)="\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","").replace("\r","")+"\""
val supabasePublicKey=configValue("SUPABASE_PUBLISHABLE_KEY")
val legacyAnon=runCatching {String(Base64.getUrlDecoder().decode(supabasePublicKey.split('.')[1])).contains(Regex("\"role\"\\s*:\\s*\"anon\""))}.getOrDefault(false)
require(supabasePublicKey.isEmpty()||supabasePublicKey.startsWith("sb_publishable_")||legacyAnon) {"SUPABASE_PUBLISHABLE_KEY accepts only a public Supabase publishable/anon key; enter personal AI keys only in the app"}
require(!supabasePublicKey.startsWith("sb_secret_")&&!runCatching {String(Base64.getUrlDecoder().decode(supabasePublicKey.split('.')[1])).contains(Regex("\"role\"\\s*:\\s*\"service_role\""))}.getOrDefault(false)) {"Only a Supabase publishable/anon key is permitted in the Android app"}
android {
    namespace = "ru.sbros.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.sbros.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.4.1"
        buildConfigField("String","SUPABASE_URL",quoted(configValue("SUPABASE_URL")))
        buildConfigField("String","SUPABASE_PUBLISHABLE_KEY",quoted(supabasePublicKey))
        javaCompileOptions.annotationProcessorOptions.arguments["room.schemaLocation"] = "$projectDir/schemas"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true;buildConfig=true }
    buildTypes { getByName("release") { isDebuggable=false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(bom)
    androidTestImplementation(bom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    annotationProcessor("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
