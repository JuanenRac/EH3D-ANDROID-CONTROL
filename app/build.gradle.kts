plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }

// The owner's own Firebase project, when its file is there (never in the repository): without it the app is built the same and offers no notices through Google.
val firebase: Map<String, String> = file("google-services.json").takeIf { it.exists() }?.let { source ->
    val root = groovy.json.JsonSlurper().parse(source) as Map<*, *>
    val project = root["project_info"] as Map<*, *>
    val client = (root["client"] as List<*>).map { it as Map<*, *> }.first { ((it["client_info"] as Map<*, *>)["android_client_info"] as Map<*, *>)["package_name"] == "es.electrohobby3d.eh3d" }
    mapOf(
        "FIREBASE_APP_ID" to (client["client_info"] as Map<*, *>)["mobilesdk_app_id"].toString(),
        "FIREBASE_API_KEY" to ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"].toString(),
        "FIREBASE_PROJECT_ID" to project["project_id"].toString(),
        "FIREBASE_SENDER_ID" to project["project_number"].toString(),
    )
} ?: emptyMap()

android { namespace = "es.electrohobby3d.eh3d"; compileSdk = 37
    defaultConfig { applicationId = "es.electrohobby3d.eh3d"; minSdk = 26; targetSdk = 37; versionCode = 21; versionName = "0.2.1" }
    buildFeatures { compose = true; buildConfig = true }
    defaultConfig { listOf("FIREBASE_APP_ID", "FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_SENDER_ID").forEach { buildConfigField("String", it, "\"${firebase[it].orEmpty()}\"") } }
    // The eight languages of the app and no other: without this the APK also carries the texts of the libraries in every language they have, and the phone offers those.
    // The default (English) texts are always kept. tools/check_languages.py holds this list, the resource folders and the languages of the built APK.
    androidResources { localeFilters += listOf("en", "es", "de", "fr", "it", "ja", "zh-rCN", "pt-rPT") }
    // The release is shrunk and obfuscated (R8). Signing is not done here: the APK is signed afterwards with the maintainer's own key, kept outside the repository.
    buildTypes { debug { enableUnitTestCoverage = true }; release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    // Lint is part of the check: a warning stops the build. What is deliberate is written down in lint.xml with its reason.
    lint { warningsAsErrors = true; abortOnError = true; checkDependencies = false; lintConfig = file("lint.xml") }
}
// The version lives in two places the Android tools need (here, and the manifest every other tool reads); a build stops when they differ,
// so a release can never carry a number the project's own manifest does not say.
afterEvaluate {
    val manifestVersion = (groovy.json.JsonSlurper().parse(rootProject.file("eh3d.project.json")) as Map<*, *>)["version"]
    val appVersion = android.defaultConfig.versionName
    check(manifestVersion == appVersion) { "eh3d.project.json says $manifestVersion but versionName is $appVersion: change them together" }
}
// The notices of the libraries travel inside the APK, as the asset THIRD_PARTY_NOTICES (tools/third_party_notices.py makes the file).
abstract class NoticesAsset : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val notices: RegularFileProperty
    @get:OutputDirectory abstract val folder: DirectoryProperty
    @TaskAction fun copy() { notices.get().asFile.copyTo(folder.get().file("THIRD_PARTY_NOTICES").asFile, overwrite = true) }
}
androidComponents.onVariants { variant ->
    val noticesAsset = tasks.register<NoticesAsset>("${variant.name}NoticesAsset") { notices.set(rootProject.layout.projectDirectory.file("docs/THIRD_PARTY_NOTICES")) }
    variant.sources.assets?.addGeneratedSourceDirectory(noticesAsset, NoticesAsset::folder)
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.google.firebase:firebase-messaging:26.0.2")
    // Firebase brings a fragment library older than the one androidx.activity asks for (lint InvalidFragmentVersionForActivityResult)
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // The Android stubs of org.json do nothing in local tests; the real library lets the parsers be tested.
    testImplementation("org.json:json:20260814")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
