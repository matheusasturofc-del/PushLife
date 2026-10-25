package com.pushlife.app.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.Executors

// analisador que também fecha o detector no fim
interface FrameAnalyzer : ImageAnalysis.Analyzer, AutoCloseable

// câmera da frente + preview; a imagem fica só no aparelho, nunca é salva
// rgba = quadros em RGBA, que é o que o MediaPipe quer
@Composable
fun FrontCamera(createAnalyzer: () -> FrameAnalyzer, modifier: Modifier = Modifier, rgba: Boolean = false) {
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
                .apply { if (rgba) setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888) }
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            // fecha o detector na thread dele, depois do quadro atual
            executor.execute { analyzer.close() }
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
