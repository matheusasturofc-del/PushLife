import java.net.URI
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
        versionName = "1.13.0"

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

    // Os modelos de pose são lidos direto do APK, sem descompactar.
    androidResources {
        noCompress += "task"
    }
}

/**
 * Baixa o modelo de pose do MediaPipe (Google, código aberto, Apache 2.0) para dentro do APK.
 * Não fica no repositório; fica guardado no cache do Gradle depois da primeira vez.
 */
abstract class DownloadPoseModels : DefaultTask() {
    /** Arquivo → endereço. */
    @get:Input
    abstract val models: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @TaskAction
    fun download() {
        val out = outputDir.get().dir("pose").asFile.apply { mkdirs() }
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        for ((file, url) in models.get()) {
            val cached = File(cache, file)
            if (cached.length() == 0L) {
                val part = File(cache, "$file.part")
                URI(url).toURL().openStream().use { input -> part.outputStream().use { input.copyTo(it) } }
                check(part.length() > 1_000_000) { "Download incompleto: $url" }
                part.renameTo(cached)
            }
            cached.copyTo(File(out, file), overwrite = true)
        }
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<DownloadPoseModels>("download${variant.name.replaceFirstChar { it.uppercase() }}PoseModels") {
            // "lite": leve e rápido no processador de celulares simples, e acha o corpo também deitado.
            models.put(
                "pose_landmarker_lite.task",
                "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/1/pose_landmarker_lite.task",
            )
            cacheDir.set(File(gradle.gradleUserHomeDir, "caches/pushlife-pose-models"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, DownloadPoseModels::outputDir)
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

    // Câmera + IA de pose (MediaPipe, código aberto do Google), que roda no aparelho
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mediapipe.tasks.vision)

    testImplementation(libs.junit)
}
