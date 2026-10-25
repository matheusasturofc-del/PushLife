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
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.pushlife.app.core.pose.Joint
import com.pushlife.app.core.pose.Point
import com.pushlife.app.core.pose.PoseFrame
import com.pushlife.app.core.pose.Vec3

// pose + esqueleto pra desenhar
data class PoseReading(val frame: PoseFrame, val rig: Rig)

// câmera da frente + pose de cada quadro (null = ninguém)
// MediaPipe Pose Lite (Google, Apache 2.0) no processador: leve, acha deitado e dá profundidade
@Composable
fun PoseCamera(onPose: (PoseReading?) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onPose)
    val context = LocalContext.current.applicationContext
    FrontCamera(createAnalyzer = { PoseAnalyzer(context) { callback(it) } }, modifier = modifier, rgba = true)
}

// roda na thread da câmera, um quadro por vez
private class PoseAnalyzer(private val context: Context, private val onResult: (PoseReading?) -> Unit) : FrameAnalyzer {
    private val main = ContextCompat.getMainExecutor(context)
    private var landmarker: PoseLandmarker? = null
    private var failed = false
    private var lastTimestamp = 0L
    @Volatile private var closed = false

    override fun analyze(image: ImageProxy) {
        if (closed || failed) {
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
        // deixa o quadro em pé (sem espelhar)
        val upright = if (rotation == 0) {
            frame
        } else {
            Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
        val reading = try {
            val detector = landmarker ?: create().also { landmarker = it }
            val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1).also { lastTimestamp = it }
            detector.detectForVideo(BitmapImageBuilder(upright).build(), timestamp)
                .toReading(upright.width.toFloat(), upright.height.toFloat())
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao detectar a pose", e)
            if (landmarker == null) failed = true
            null
        }
        main.execute { if (!closed) onResult(reading) }
    }

    // cria o detector só no primeiro quadro (demora um pouco pra abrir)
    private fun create(): PoseLandmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )

    override fun close() {
        closed = true
        landmarker?.close()
        landmarker = null
    }

    private companion object {
        const val TAG = "PoseCamera"
        const val MODEL = "pose/pose_landmarker_lite.task"
    }
}

// ossos do esqueleto desenhado
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

// MediaPipe → modelo do app (com os pontos 3D em metros)
private fun PoseLandmarkerResult.toReading(width: Float, height: Float): PoseReading? {
    val landmarks = landmarks().firstOrNull()?.takeIf { it.size >= LANDMARKS } ?: return null
    val world = worldLandmarks().firstOrNull()?.takeIf { it.size >= LANDMARKS }
    val points = Joint.entries.associateWith { joint ->
        val lm = landmarks[joint.index]
        val w = world?.get(joint.index)?.let { Vec3(it.x(), it.y(), it.z()) }
        Point(lm.x() * width, lm.y() * height, lm.visibility().orElse(1f), w)
    }
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
