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
import com.google.android.gms.tasks.Tasks
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.pushlife.app.core.pose.Joint
import com.pushlife.app.core.pose.Point
import com.pushlife.app.core.pose.PoseFrame
import com.pushlife.app.core.pose.classify.Vec3

/** Pose de um quadro, o esqueleto para desenhar e qual IA achou (para o diagnóstico na tela). */
data class PoseReading(val frame: PoseFrame, val rig: Rig, val engine: String)

/**
 * Câmera para os exercícios de corpo inteiro: entrega a pose de cada quadro (null = ninguém).
 * [onFrame] é chamado a cada quadro analisado, com ou sem pessoa (para medir quadros por segundo).
 *
 * Usa o MediaPipe Pose Landmarker (código aberto do Google, Apache 2.0), que também estima a
 * profundidade. Se nos primeiros segundos ele não achar ninguém mas o ML Kit achar, este celular
 * não se dá bem com o MediaPipe: troca para o ML Kit e lembra disso nas próximas vezes.
 */
@Composable
fun PoseCamera(onPose: (PoseReading?) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onPose)
    val context = LocalContext.current.applicationContext
    FrontCamera(createAnalyzer = { PoseAnalyzer(context) { callback(it) } }, modifier = modifier, rgba = true)
}

private enum class Engine(val label: String) { MEDIAPIPE("MediaPipe"), MLKIT("ML Kit") }

/** Roda na thread da câmera, um quadro por vez (a câmera descarta os que chegam no meio). */
private class PoseAnalyzer(private val context: Context, private val onResult: (PoseReading?) -> Unit) : FrameAnalyzer {
    private val main = ContextCompat.getMainExecutor(context)
    private val prefs = context.getSharedPreferences("pose_detector", Context.MODE_PRIVATE)
    private var engine = if (prefs.getBoolean(MEDIAPIPE_OFF, false)) Engine.MLKIT else Engine.MEDIAPIPE
    private var mediaPipe: PoseLandmarker? = null
    private var mlKit: PoseDetector? = null
    private var lastTimestamp = 0L
    private var mediaPipeFoundSomeone = false
    private var missingSince = 0L
    @Volatile private var closed = false

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
        // Gira o quadro para ficar em pé, como aparece na tela (sem espelhar).
        val upright = if (rotation == 0) {
            frame
        } else {
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val reading = try {
            if (engine == Engine.MEDIAPIPE) withMediaPipe(upright) else withMlKit(upright)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao detectar a pose (${engine.label})", e)
            if (engine == Engine.MEDIAPIPE) switchToMlKit()
            null
        }
        main.execute { if (!closed) onResult(reading) }
    }

    private fun withMediaPipe(bitmap: Bitmap): PoseReading? {
        val detector = mediaPipe ?: createMediaPipe() ?: run {
            switchToMlKit()
            return withMlKit(bitmap)
        }
        val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1).also { lastTimestamp = it }
        val reading = detector.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
            .toReading(bitmap.width.toFloat(), bitmap.height.toFloat())
        if (reading != null) {
            mediaPipeFoundSomeone = true
            return reading
        }
        if (mediaPipeFoundSomeone) return null
        // Ainda não achou ninguém: a cada 2 s pergunta ao ML Kit. Se ele achar, o MediaPipe não serve aqui.
        val now = SystemClock.uptimeMillis()
        if (missingSince == 0L) missingSince = now
        if (now - missingSince < CHECK_AFTER_MS) return null
        missingSince = now
        val other = withMlKit(bitmap) ?: return null
        Log.w(TAG, "MediaPipe não achou o corpo, mas o ML Kit achou: usando o ML Kit neste celular")
        switchToMlKit()
        return other
    }

    private fun createMediaPipe(): PoseLandmarker? = try {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build()
        PoseLandmarker.createFromOptions(context, options).also { mediaPipe = it }
    } catch (e: Exception) {
        Log.w(TAG, "Não deu para abrir o MediaPipe", e)
        null
    }

    private fun switchToMlKit() {
        engine = Engine.MLKIT
        prefs.edit { putBoolean(MEDIAPIPE_OFF, true) }
        mediaPipe?.close()
        mediaPipe = null
    }

    private fun withMlKit(bitmap: Bitmap): PoseReading? {
        val detector = mlKit ?: PoseDetection.getClient(
            PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
        ).also { mlKit = it }
        return Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
            .toReading(bitmap.width.toFloat(), bitmap.height.toFloat())
    }

    override fun close() {
        closed = true
        mediaPipe?.close()
        mediaPipe = null
        mlKit?.close()
        mlKit = null
    }

    private companion object {
        const val TAG = "PoseCamera"
        const val MODEL = "pose/pose_landmarker_full.task"
        const val MEDIAPIPE_OFF = "mediapipe_off"
        const val CHECK_AFTER_MS = 2_000L
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

private const val LANDMARKS = 33

/** Resultado do MediaPipe (0..1 da imagem em pé) → modelo do app. Os pontos 3D vão junto. */
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
    return reading(points, all, width, height, Engine.MEDIAPIPE)
}

/** Resultado do ML Kit (pixels da imagem em pé) → modelo do app. */
private fun Pose.toReading(width: Float, height: Float): PoseReading? {
    if (allPoseLandmarks.size < LANDMARKS) return null
    val byType = allPoseLandmarks.associateBy { it.landmarkType }
    val points = Joint.entries.mapNotNull { joint ->
        byType[joint.index]?.let { joint to Point(it.position.x, it.position.y, it.inFrameLikelihood) }
    }.toMap()
    val all = allPoseLandmarks.sortedBy { it.landmarkType }.map { Vec3(it.position3D.x, it.position3D.y, it.position3D.z) }
    return reading(points, all, width, height, Engine.MLKIT)
}

private fun reading(points: Map<Joint, Point>, all: List<Vec3>, width: Float, height: Float, engine: Engine): PoseReading {
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
    return PoseReading(frame, rig, engine.label)
}
