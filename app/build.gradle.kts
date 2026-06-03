import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.nutritionassistant"
    compileSdk = 34  // 建议降到 34 稳一点，36 太新可能缺依赖

    defaultConfig {
        applicationId = "com.example.nutritionassistant"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val properties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }

        buildConfigField("String", "AI_BASE_URL", "\"${properties.getProperty("AI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")}\"")
        buildConfigField("String", "TIANAPI_URL", "\"${properties.getProperty("TIANAPI_URL", "https://apis.tianapi.com/")}\"")

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Room 编译时 schema 导出路径（可选，留着没坏处）
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    buildFeatures {
        viewBinding = true   // 启用 ViewBinding
        buildConfig = true
    }
}




dependencies {
    // ── Android 基础（传统 View） ──
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ── 生命周期 + ViewModel + LiveData ──
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.fragment:fragment-ktx:1.6.2")

    // ── 传统导航（Fragment 切换） ──
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.7")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")

    // ── Hilt ──
    implementation("com.google.dagger:hilt-android:2.50")
    ksp("com.google.dagger:hilt-android-compiler:2.50")
    // 传统 View 不用 hilt-navigation-compose，删掉

    // ── Room 数据库 ──
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── DataStore 偏好存储 ──
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ── CameraX 拍照 ──
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ── ML Kit 扫码 ──
    implementation("com.google.mlkit:barcode-scanning:17.2.0")

    // ── 网络请求 Retrofit + Moshi ──
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.0")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── 图片加载（传统 View 用 Coil 基础版） ──
    implementation("io.coil-kt:coil:2.5.0")

    // ── 图表（MPAndroidChart 完美支持传统 View） ──
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // ── 测试 ──
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // ── WorkManager 任务 ──
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")
}