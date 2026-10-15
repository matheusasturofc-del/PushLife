package com.pushlife.app.camera

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

/** Um analisador de quadros que também libera seus recursos (o detector do ML Kit). */
interface FrameAnalyzer : ImageAnalysis.Analyzer, AutoCloseable

/**
 * Câmera frontal com pré-visualização e um analisador de quadros. A imagem é processada só no
 * aparelho e nunca é salva.
 */
@Composable
fun FrontCamera(createAnalyzer: () -> FrameAnalyzer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE // permite recortar em círculo
        }
    }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = createAnalyzer()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val cameraProvider = providerFuture.get().also { provider = it }
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

/** O maior rosto do quadro: tamanho relativo à imagem (0..1) e o rig para desenhar. */
data class FaceReading(val size: Float, val rig: Rig)

/** Câmera para a flexão: informa o maior rosto do quadro, ou null se não houver rosto. */
@Composable
fun FaceCamera(onFace: (FaceReading?) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onFace)
    FrontCamera(createAnalyzer = { FaceSizeAnalyzer { callback(it) } }, modifier = modifier)
}

private class FaceSizeAnalyzer(private val onResult: (FaceReading?) -> Unit) : FrameAnalyzer {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // olhos, nariz e boca para o rig
            .setMinFaceSize(0.1f)
            .build(),
    )
    @Volatile private var closed = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null || closed) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val width = (if (rotation % 180 == 0) image.width else image.height).toFloat()
        val height = (if (rotation % 180 == 0) image.height else image.width).toFloat()
        // Os listeners do ML Kit rodam na thread principal.
        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { faces ->
                if (closed) return@addOnSuccessListener
                val face = faces.maxByOrNull { it.boundingBox.width() }
                if (face == null) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                val box = face.boundingBox
                // Média de largura e altura: mais estável que só a largura quando a cabeça inclina.
                val size = (box.width() / width + box.height() / height) / 2f
                val rig = Rig(
                    imageAspect = width / height,
                    points = face.allLandmarks.map { P01(it.position.x / width, it.position.y / height) },
                    box = Box01(box.left / width, box.top / height, box.right / width, box.bottom / height),
                )
                onResult(FaceReading(size, rig))
            }
            .addOnFailureListener { if (!closed) onResult(null) }
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        closed = true
        detector.close()
    }
}
