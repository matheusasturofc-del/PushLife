import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Contas (Supabase): endereço e chave pública do projeto, em supabase.properties na raiz.
// Sem o arquivo o app funciona normalmente, só sem login. Veja CONTAS.md.
val supabase = Properties().apply {
    rootProject.file("supabase.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

// Versão só em inglês, para tirar as capturas de tela da loja: ./gradlew assembleDebug -PenglishOnly
val englishOnly = project.hasProperty("englishOnly")

android {
    namespace = "com.pushlife.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pushlife.app"
        minSdk = 26
        targetSdk = 35
        // No GitHub cada build ganha um número maior (a Play Store exige que ele sempre suba).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 7
        versionName = "1.9.3"

        // Só processadores de celular (ARM). Sem isso o APK carrega ~60 MB de bibliotecas x86 inúteis.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        if (englishOnly) resourceConfigurations += "en"
        resValue("bool", "english_only", englishOnly.toString())
        resValue("string", "supabase_url", supabase.getProperty("url", ""))
        resValue("string", "supabase_key", supabase.getProperty("key", ""))
    }

    signingConfigs {
        // Chave fixa de desenvolvimento: assim cada APK novo instala por cima do anterior
        // sem apagar os dados. A chave de publicação na Play Store será outra, guardada em segredo.
        getByName("debug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Chave de publicação: só existe no GitHub (segredos), nunca no repositório.
        // Veja play-store/README.md para criar a chave e cadastrar os segredos.
        val releaseStore = System.getenv("PUSHLIFE_KEYSTORE_FILE")
        if (releaseStore != null) {
            create("release") {
                storeFile = file(releaseStore)
                storePassword = System.getenv("PUSHLIFE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PUSHLIFE_KEY_ALIAS")
                keyPassword = System.getenv("PUSHLIFE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        resValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Câmera + IA de rosto (roda no aparelho, modelo já vem dentro do app)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.pose.detection)
    // Modelo "preciso" do Google: acha melhor o corpo deitado (flexão, prancha, abdominal).
    implementation(libs.mlkit.pose.detection.accurate)

    testImplementation(libs.junit)
}
