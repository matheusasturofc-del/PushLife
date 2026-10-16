import java.net.URI
import java.util.Properties
import java.util.zip.GZIPInputStream
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
        versionName = "1.12.0"

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
        noCompress += listOf("task", "tflite")
    }
}

/**
 * Baixa os modelos de pose (Google, código aberto, Apache 2.0) para dentro do APK. Não ficam no
 * repositório; ficam guardados no cache do Gradle depois da primeira vez.
 * [required]: arquivo → endereço; sem ele a compilação para. [optional]: arquivo → endereços para tentar
 * em ordem; se nenhum funcionar o app só não oferece aquela IA.
 */
abstract class DownloadPoseModels : DefaultTask() {
    @get:Input
    abstract val required: MapProperty<String, String>

    @get:Input
    abstract val optional: MapProperty<String, List<String>>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @TaskAction
    fun download() {
        val out = outputDir.get().dir("pose").asFile.apply { mkdirs() }
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        for ((file, url) in required.get()) {
            val model = fetch(cache, file, listOf(url)) ?: throw GradleException("Não deu para baixar $url")
            model.copyTo(File(out, file), overwrite = true)
        }
        for ((file, urls) in optional.get()) {
            val model = fetch(cache, file, urls)
            if (model == null) logger.warn("Modelo opcional $file indisponível; o app não vai oferecê-lo.")
            else model.copyTo(File(out, file), overwrite = true)
        }
    }

    /** Baixa para o cache (uma vez). Aceita o modelo puro ou um .tar.gz com um .tflite dentro. */
    private fun fetch(cache: File, file: String, urls: List<String>): File? {
        val cached = File(cache, file)
        if (cached.length() > 0L) return cached
        for (url in urls) {
            try {
                val bytes = URI(url).toURL().openStream().use { it.readBytes() }
                val model = if (bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) tflite(bytes) else bytes
                if (model == null || model.size < 1_000_000) {
                    logger.warn("Arquivo inesperado em $url")
                    continue
                }
                val part = File(cache, "$file.part")
                part.writeBytes(model)
                part.renameTo(cached)
                return cached
            } catch (e: Exception) {
                logger.warn("Falhou $url: ${e.message}")
            }
        }
        return null
    }

    /** O primeiro .tflite dentro de um .tar.gz. */
    private fun tflite(gz: ByteArray): ByteArray? {
        val tar = GZIPInputStream(gz.inputStream()).use { it.readBytes() }
        var pos = 0
        while (pos + 512 <= tar.size) {
            val name = String(tar, pos, 100).trimEnd('\u0000').trim()
            if (name.isEmpty()) return null
            val size = String(tar, pos + 124, 12).trimEnd('\u0000').trim().toLong(8).toInt()
            if (name.endsWith(".tflite")) return tar.copyOfRange(pos + 512, pos + 512 + size)
            pos += 512 + (size + 511) / 512 * 512
        }
        return null
    }
}

androidComponents {
    onVariants { variant ->
        val task = tasks.register<DownloadPoseModels>("download${variant.name.replaceFirstChar { it.uppercase() }}PoseModels") {
            val mediapipe = "https://storage.googleapis.com/mediapipe-models/pose_landmarker"
            required.set(
                mapOf(
                    "pose_landmarker_lite.task" to "$mediapipe/pose_landmarker_lite/float16/1/pose_landmarker_lite.task",
                    "pose_landmarker_full.task" to "$mediapipe/pose_landmarker_full/float16/1/pose_landmarker_full.task",
                ),
            )
            // MoveNet (TensorFlow): outra IA de pose aberta do Google, bem rápida.
            optional.set(
                listOf("lightning", "thunder").associate { name ->
                    "movenet_$name.tflite" to listOf(
                        "https://tfhub.dev/google/lite-model/movenet/singlepose/$name/tflite/float16/4?lite-format=tflite",
                        "https://www.kaggle.com/api/v1/models/google/movenet/tfLite/singlepose-$name-tflite-float16/1/download",
                        "https://storage.googleapis.com/tfhub-lite-models/google/lite-model/movenet/singlepose/$name/tflite/float16/4.tflite",
                    )
                },
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
    // ML Kit: reserva, caso o MediaPipe não ache o corpo neste celular.
    implementation(libs.mlkit.pose.detection)
    // Roda o MoveNet.
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
}
