package com.pushlife.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
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
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.pushlife.app.core.pose.Joint
import com.pushlife.app.core.pose.Point
import com.pushlife.app.core.pose.PoseFrame
import com.pushlife.app.core.pose.classify.Vec3
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Pose de um quadro, o esqueleto para desenhar e qual IA achou (para o diagnóstico na tela). */
data class PoseReading(val frame: PoseFrame, val rig: Rig, val engine: String)

/**
 * As IAs de pose que o app sabe usar. Todas rodam no aparelho, só no processador.
 * [asset]: o modelo que precisa estar no APK (null = vem dentro da biblioteca).
 */
enum class PoseEngine(val label: String, val asset: String?) {
    /** MediaPipe Lite; se ele não achar o corpo e o ML Kit achar, troca para o ML Kit. */
    AUTO("Auto", null),
    MEDIAPIPE_LITE("MediaPipe Lite", "pose/pose_landmarker_lite.task"),
    MEDIAPIPE_FULL("MediaPipe Full", "pose/pose_landmarker_full.task"),
    MLKIT("ML Kit", null),
    MOVENET_LIGHTNING("MoveNet Lightning", "pose/movenet_lightning.tflite"),
    MOVENET_THUNDER("MoveNet Thunder", "pose/movenet_thunder.tflite"),
    ;

    companion object {
        private const val PREFS = "pose_detector"
        private const val KEY = "engine"

        /** As que este APK tem (os modelos opcionais podem ter ficado de fora na compilação). */
        fun available(context: Context): List<PoseEngine> {
            val files = context.assets.list("pose").orEmpty().toSet()
            return entries.filter { e -> e.asset == null || e.asset.removePrefix("pose/") in files }
        }

        fun saved(context: Context): PoseEngine {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return entries.firstOrNull { it.name == name }?.takeIf { it in available(context) } ?: AUTO
        }

        fun save(context: Context, engine: PoseEngine) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, engine.name) }
    }
}

/**
 * Câmera para os exercícios de corpo inteiro: entrega a pose de cada quadro (null = ninguém), usando
 * a IA [engine]. Para trocar de IA, recrie a câmera (por exemplo com `key(engine)`).
 */
@Composable
fun PoseCamera(engine: PoseEngine, onPose: (PoseReading?) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onPose)
    val context = LocalContext.current.applicationContext
    FrontCamera(createAnalyzer = { PoseAnalyzer(context, engine) { callback(it) } }, modifier = modifier, rgba = true)
}

/** Uma IA de pose: recebe a imagem já em pé e devolve a pose, ou null. Usada sempre na mesma thread. */
private interface Detector : AutoCloseable {
    fun detect(bitmap: Bitmap): PoseReading?
}

/** Roda na thread da câmera, um quadro por vez (a câmera descarta os que chegam no meio). */
private class PoseAnalyzer(
    private val context: Context,
    private val engine: PoseEngine,
    private val onResult: (PoseReading?) -> Unit,
) : FrameAnalyzer {
    private val main = ContextCompat.getMainExecutor(context)
    private var detector: Detector? = null
    private var failed = false
    @Volatile private var closed = false

    override fun analyze(image: ImageProxy) {
        if (closed || failed) {
            image.close()
            if (failed) main.execute { if (!closed) onResult(null) }
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
            val d = detector ?: create().also { detector = it }
            d.detect(upright)
        } catch (e: Exception) {
            Log.w(TAG, "Falha na IA ${engine.label}", e)
            if (detector == null) failed = true
            null
        }
        main.execute { if (!closed) onResult(reading) }
    }

    private fun create(): Detector = when (engine) {
        PoseEngine.AUTO -> AutoDetector(context)
        PoseEngine.MEDIAPIPE_LITE, PoseEngine.MEDIAPIPE_FULL -> MediaPipeDetector(context, engine)
        PoseEngine.MLKIT -> MlKitDetector()
        PoseEngine.MOVENET_LIGHTNING, PoseEngine.MOVENET_THUNDER -> MoveNetDetector(context, engine)
    }

    override fun close() {
        closed = true
        detector?.close()
        detector = null
    }

    companion object {
        const val TAG = "PoseCamera"
    }
}

/**
 * MediaPipe Lite; se nos primeiros segundos ele não achar ninguém mas o ML Kit achar, este celular não
 * se dá bem com o MediaPipe: troca para o ML Kit e lembra disso nas próximas vezes.
 */
private class AutoDetector(context: Context) : Detector {
    private val prefs = context.getSharedPreferences("pose_detector", Context.MODE_PRIVATE)
    private var mediaPipe: MediaPipeDetector? =
        if (prefs.getBoolean(MEDIAPIPE_OFF, false)) null else runCatching { MediaPipeDetector(context, PoseEngine.MEDIAPIPE_LITE) }.getOrNull()
    private val mlKit by lazy { MlKitDetector() }
    private var foundSomeone = false
    private var missingSince = 0L

    override fun detect(bitmap: Bitmap): PoseReading? {
        val mp = mediaPipe ?: return mlKit.detect(bitmap)
        val reading = try {
            mp.detect(bitmap)
        } catch (e: RuntimeException) {
            Log.w(PoseAnalyzer.TAG, "MediaPipe falhou: usando o ML Kit", e)
            giveUpMediaPipe()
            return mlKit.detect(bitmap)
        }
        if (reading != null) {
            foundSomeone = true
            return reading
        }
        if (foundSomeone) return null
        // Ainda não achou ninguém: a cada 2 s pergunta ao ML Kit. Se ele achar, o MediaPipe não serve aqui.
        val now = SystemClock.uptimeMillis()
        if (missingSince == 0L) missingSince = now
        if (now - missingSince < 2_000L) return null
        missingSince = now
        val other = mlKit.detect(bitmap) ?: return null
        giveUpMediaPipe()
        return other
    }

    private fun giveUpMediaPipe() {
        prefs.edit { putBoolean(MEDIAPIPE_OFF, true) }
        mediaPipe?.close()
        mediaPipe = null
    }

    override fun close() {
        mediaPipe?.close()
        mlKit.close()
    }

    private companion object {
        const val MEDIAPIPE_OFF = "mediapipe_off"
    }
}

/** MediaPipe Pose Landmarker (Google, Apache 2.0): 33 pontos e também a profundidade (3D). */
private class MediaPipeDetector(context: Context, private val engine: PoseEngine) : Detector {
    private val landmarker = PoseLandmarker.createFromOptions(
        context,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(engine.asset!!).setDelegate(Delegate.CPU).build())
            .setRunningMode(RunningMode.VIDEO)
            .setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f)
            .setMinPosePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .build(),
    )
    private var lastTimestamp = 0L

    override fun detect(bitmap: Bitmap): PoseReading? {
        val timestamp = maxOf(SystemClock.uptimeMillis(), lastTimestamp + 1).also { lastTimestamp = it }
        return landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), timestamp)
            .toReading(bitmap.width.toFloat(), bitmap.height.toFloat(), engine.label)
    }

    override fun close() = landmarker.close()
}

/** ML Kit Pose (Google), modelo rápido. */
private class MlKitDetector : Detector {
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
    )

    override fun detect(bitmap: Bitmap): PoseReading? =
        Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0))).toReading(bitmap.width.toFloat(), bitmap.height.toFloat())

    override fun close() = detector.close()
}

/**
 * MoveNet (Google/TensorFlow, Apache 2.0): 17 pontos, feito para fitness. A imagem vai quadrada (com
 * faixas pretas) no tamanho que o modelo pede.
 */
private class MoveNetDetector(context: Context, private val engine: PoseEngine) : Detector {
    private val interpreter: Interpreter
    private val size: Int
    private val floatInput: Boolean
    private val input: ByteBuffer
    private val output = Array(1) { Array(1) { Array(17) { FloatArray(3) } } }
    private val pixels: IntArray
    private val square: Bitmap
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        val model = context.assets.openFd(engine.asset!!).use { fd ->
            FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
        }
        interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
        val tensor = interpreter.getInputTensor(0)
        size = tensor.shape()[1]
        floatInput = tensor.dataType() == DataType.FLOAT32
        input = ByteBuffer.allocateDirect(size * size * 3 * if (floatInput) 4 else 1).order(ByteOrder.nativeOrder())
        pixels = IntArray(size * size)
        square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    override fun detect(bitmap: Bitmap): PoseReading? {
        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        val padX = (size - bitmap.width * scale) / 2f
        val padY = (size - bitmap.height * scale) / 2f
        Canvas(square).apply {
            drawColor(android.graphics.Color.BLACK)
            drawBitmap(bitmap, null, RectF(padX, padY, size - padX, size - padY), paint)
        }
        square.getPixels(pixels, 0, size, 0, 0, size, size)
        input.rewind()
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (floatInput) {
                input.putFloat(r.toFloat()).putFloat(g.toFloat()).putFloat(b.toFloat())
            } else {
                input.put(r.toByte()).put(g.toByte()).put(b.toByte())
            }
        }
        input.rewind()
        interpreter.run(input, output)
        val kp = output[0][0]
        // Pontos de volta para a imagem original: (y, x, confiança), em 0..1 do quadrado.
        val points = MOVENET_JOINTS.mapValues { (_, i) ->
            Point((kp[i][1] * size - padX) / scale, (kp[i][0] * size - padY) / scale, kp[i][2])
        }
        if (points.values.count { it.visibility >= MOVENET_MIN_SCORE } < 5) return null
        return reading(points, emptyList(), bitmap.width.toFloat(), bitmap.height.toFloat(), engine.label, MOVENET_MIN_SCORE)
    }

    override fun close() = interpreter.close()

    private companion object {
        /** O MoveNet dá notas mais baixas que os outros: 0,3 já é um ponto confiável. */
        const val MOVENET_MIN_SCORE = 0.3f

        /** Índices dos 17 pontos do MoveNet (padrão COCO). */
        val MOVENET_JOINTS = mapOf(
            Joint.NOSE to 0,
            Joint.LEFT_SHOULDER to 5, Joint.RIGHT_SHOULDER to 6,
            Joint.LEFT_ELBOW to 7, Joint.RIGHT_ELBOW to 8,
            Joint.LEFT_WRIST to 9, Joint.RIGHT_WRIST to 10,
            Joint.LEFT_HIP to 11, Joint.RIGHT_HIP to 12,
            Joint.LEFT_KNEE to 13, Joint.RIGHT_KNEE to 14,
            Joint.LEFT_ANKLE to 15, Joint.RIGHT_ANKLE to 16,
        )
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
private fun PoseLandmarkerResult.toReading(width: Float, height: Float, engine: String): PoseReading? {
    val landmarks = landmarks().firstOrNull()?.takeIf { it.size >= LANDMARKS } ?: return null
    val world = worldLandmarks().firstOrNull()?.takeIf { it.size >= LANDMARKS }
    val points = Joint.entries.associateWith { joint ->
        val lm = landmarks[joint.index]
        val w = world?.get(joint.index)?.let { Vec3(it.x(), it.y(), it.z()) }
        Point(lm.x() * width, lm.y() * height, lm.visibility().orElse(1f), w)
    }
    // Os 33 marcos em pixels (z na mesma escala de x), como o classificador de poses espera.
    val all = landmarks.take(LANDMARKS).map { Vec3(it.x() * width, it.y() * height, it.z() * width) }
    return reading(points, all, width, height, engine)
}

/** Resultado do ML Kit (pixels da imagem em pé) → modelo do app. */
private fun Pose.toReading(width: Float, height: Float): PoseReading? {
    if (allPoseLandmarks.size < LANDMARKS) return null
    val byType = allPoseLandmarks.associateBy { it.landmarkType }
    val points = Joint.entries.mapNotNull { joint ->
        byType[joint.index]?.let { joint to Point(it.position.x, it.position.y, it.inFrameLikelihood) }
    }.toMap()
    val all = allPoseLandmarks.sortedBy { it.landmarkType }.map { Vec3(it.position3D.x, it.position3D.y, it.position3D.z) }
    return reading(points, all, width, height, PoseEngine.MLKIT.label)
}

private fun reading(
    points: Map<Joint, Point>,
    all: List<Vec3>,
    width: Float,
    height: Float,
    engine: String,
    minVisibility: Float = 0.5f,
): PoseReading {
    val frame = PoseFrame(points, minVisibility, all)
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
    return PoseReading(frame, rig, engine)
}
