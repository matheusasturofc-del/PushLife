package com.pushlife.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.pushlife.app.core.pose.Joint
import com.pushlife.app.core.pose.Point
import com.pushlife.app.core.pose.PoseFrame
import com.pushlife.app.core.pose.classify.Vec3

/** Pose de um quadro e o esqueleto para desenhar. */
data class PoseReading(val frame: PoseFrame, val rig: Rig)

/**
 * Câmera para os exercícios de corpo inteiro: entrega a pose detectada em cada quadro, ou null.
 * Usa o MediaPipe Pose Landmarker do Google, que também estima a profundidade (pontos 3D em metros).
 * [accurate] usa o modelo "heavy", o mais preciso; sem ele, o "full", mais rápido para exercícios ágeis.
 */
@Composable
fun PoseCamera(onPose: (PoseReading?) -> Unit, modifier: Modifier = Modifier, accurate: Boolean = false) {
    val callback by rememberUpdatedState(onPose)
    val context = LocalContext.current.applicationContext
    FrontCamera(createAnalyzer = { PoseAnalyzer(context, accurate) { callback(it) } }, modifier = modifier, rgba = true)
}

/**
 * Roda o detector na thread da câmera, um quadro por vez (a câmera descarta os que chegam no meio).
 * Tenta a placa de vídeo (GPU), bem mais rápida; se ela falhar, ou se o app fechou sozinho na última
 * vez que tentou, usa o processador (CPU).
 */
private class PoseAnalyzer(
    private val context: Context,
    accurate: Boolean,
    private val onResult: (PoseReading?) -> Unit,
) : FrameAnalyzer {
    private val model = if (accurate) "pose/pose_landmarker_heavy.task" else "pose/pose_landmarker_full.task"
    private val main = ContextCompat.getMainExecutor(context)
    private val prefs = context.getSharedPreferences("pose_detector", Context.MODE_PRIVATE)
    private var landmarker: PoseLandmarker? = null
    private var usingGpu = false
    private var gpuFrames = 0
    private var unavailable = false
    private var lastTimestamp = 0L
    @Volatile private var closed = false

    init {
        // Ficou marcado "testando a GPU" de uma vez anterior: o app caiu nela. Daqui em diante, só CPU.
        if (prefs.getBoolean(GPU_TESTING, false)) {
            prefs.edit { putBoolean(GPU_TESTING, false).putBoolean(GPU_OFF, true) }
        }
    }

    override fun analyze(image: ImageProxy) {
        if (closed) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val frame = try {
            image.toBitmap()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Quadro da câmera inválido", e)
            null
        } finally {
            image.close()
        }
        if (frame == null) return
        // Gira o quadro para ficar em pé, como na tela (sem espelhar, igual aos outros detectores).
        val upright = if (rotation == 0) frame else {
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val reading = detect(upright)?.toReading(upright.width.toFloat(), upright.height.toFloat())
        main.execute { if (!closed) onResult(reading) }
    }

    private fun detect(bitmap: Bitmap): PoseLandmarkerResult? {
        val detector = landmarker ?: create() ?: return null
        val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1).also { lastTimestamp = it }
        return try {
            detector.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp).also {
                if (usingGpu && ++gpuFrames == GPU_OK_FRAMES) prefs.edit { putBoolean(GPU_TESTING, false) }
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Falha ao detectar a pose", e)
            if (usingGpu) {
                // A GPU deste celular não deu conta: troca para a CPU no próximo quadro.
                prefs.edit { putBoolean(GPU_TESTING, false).putBoolean(GPU_OFF, true) }
                detector.close()
                landmarker = null
            }
            null
        }
    }

    /** Cria o detector nesta thread (a GPU exige usar na mesma thread em que foi criada). */
    private fun create(): PoseLandmarker? {
        if (unavailable || closed) return null
        if (!prefs.getBoolean(GPU_OFF, false)) {
            prefs.edit(commit = true) { putBoolean(GPU_TESTING, true) }
            build(Delegate.GPU)?.let {
                usingGpu = true
                landmarker = it
                return it
            }
            prefs.edit { putBoolean(GPU_TESTING, false).putBoolean(GPU_OFF, true) }
        }
        usingGpu = false
        landmarker = build(Delegate.CPU)
        if (landmarker == null) unavailable = true
        return landmarker
    }

    private fun build(delegate: Delegate): PoseLandmarker? = try {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(model).setDelegate(delegate).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        PoseLandmarker.createFromOptions(context, options)
    } catch (e: Exception) {
        Log.w(TAG, "Não deu para abrir o detector de poses ($delegate)", e)
        null
    }

    override fun close() {
        closed = true
        // Fechou normalmente: a GPU não derrubou o app.
        if (usingGpu && gpuFrames < GPU_OK_FRAMES) prefs.edit { putBoolean(GPU_TESTING, false) }
        // Fecha na thread da câmera, depois do quadro que estiver em andamento.
        val detector = landmarker
        landmarker = null
        detector?.close()
    }

    private companion object {
        const val TAG = "PoseCamera"
        const val GPU_TESTING = "gpu_testing"
        const val GPU_OFF = "gpu_off"
        const val GPU_OK_FRAMES = 30
    }
}

/** Ligações do esqueleto desenhado por cima da câmera. */
private val BONES = listOf(
    Joint.LEFT_SHOULDER to Joint.RIGHT_SHOULDER,
    Joint.LEFT_SHOULDER to Joint.LEFT_ELBOW, Joint.LEFT_ELBOW to Joint.LEFT_WRIST,
    Joint.RIGHT_SHOULDER to Joint.RIGHT_ELBOW, Joint.RIGHT_ELBOW to Joint.RIGHT_WRIST,
    Joint.LEFT_SHOULDER to Joint.LEFT_HIP, Joint.RIGHT_SHOULDER to Joint.RIGHT_HIP,
    Joint.LEFT_HIP to Joint.RIGHT_HIP,
    Joint.LEFT_HIP to Joint.LEFT_KNEE, Joint.LEFT_KNEE to Joint.LEFT_ANKLE,
    Joint.RIGHT_HIP to Joint.RIGHT_KNEE, Joint.RIGHT_KNEE to Joint.RIGHT_ANKLE,
)

/** Converte o resultado do MediaPipe (coordenadas 0..1 da imagem em pé) para o modelo do app e o rig. */
private fun PoseLandmarkerResult.toReading(width: Float, height: Float): PoseReading? {
    val landmarks = landmarks().firstOrNull()?.takeIf { it.size >= LANDMARKS } ?: return null
    val world = worldLandmarks().firstOrNull()?.takeIf { it.size >= LANDMARKS }
    val points = Joint.entries.associateWith { joint ->
        val lm = landmarks[joint.index]
        val w = world?.get(joint.index)?.let { Vec3(it.x(), it.y(), it.z()) }
        Point(lm.x() * width, lm.y() * height, lm.visibility().orElse(1f), w)
    }
    // Os 33 marcos em pixels (z na mesma escala de x), como o classificador de poses espera.
    val all = landmarks.take(LANDMARKS).map { Vec3(it.x() * width, it.y() * height, it.z() * width) }
    val frame = PoseFrame(points, all = all)
    fun norm(p: Point) = P01(p.x / width, p.y / height)
    val rig = Rig(
        imageAspect = width / height,
        points = points.keys.mapNotNull { frame[it] }.map(::norm),
        segments = BONES.mapNotNull { (a, b) ->
            val pa = frame[a] ?: return@mapNotNull null
            val pb = frame[b] ?: return@mapNotNull null
            norm(pa) to norm(pb)
        },
    )
    return PoseReading(frame, rig)
}

private const val LANDMARKS = 33
