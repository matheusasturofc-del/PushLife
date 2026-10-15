package com.pushlife.app.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.pushlife.app.core.pose.Joint
import com.pushlife.app.core.pose.Point
import com.pushlife.app.core.pose.PoseFrame
import com.pushlife.app.core.pose.classify.Vec3

/** Pose de um quadro e o esqueleto para desenhar. */
data class PoseReading(val frame: PoseFrame, val rig: Rig)

/** Câmera para os exercícios de corpo inteiro: entrega a pose detectada em cada quadro, ou null. */
@Composable
fun PoseCamera(onPose: (PoseReading?) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onPose)
    FrontCamera(createAnalyzer = { PoseAnalyzer { callback(it) } }, modifier = modifier)
}

private class PoseAnalyzer(private val onResult: (PoseReading?) -> Unit) : FrameAnalyzer {
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build(),
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
        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { pose -> if (!closed) onResult(pose.toReading(width, height)) }
            .addOnFailureListener { if (!closed) onResult(null) }
            .addOnCompleteListener { image.close() }
    }

    override fun close() {
        closed = true
        detector.close()
    }
}

private val JOINTS = mapOf(
    Joint.NOSE to PoseLandmark.NOSE,
    Joint.LEFT_SHOULDER to PoseLandmark.LEFT_SHOULDER,
    Joint.RIGHT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
    Joint.LEFT_ELBOW to PoseLandmark.LEFT_ELBOW,
    Joint.RIGHT_ELBOW to PoseLandmark.RIGHT_ELBOW,
    Joint.LEFT_WRIST to PoseLandmark.LEFT_WRIST,
    Joint.RIGHT_WRIST to PoseLandmark.RIGHT_WRIST,
    Joint.LEFT_HIP to PoseLandmark.LEFT_HIP,
    Joint.RIGHT_HIP to PoseLandmark.RIGHT_HIP,
    Joint.LEFT_KNEE to PoseLandmark.LEFT_KNEE,
    Joint.RIGHT_KNEE to PoseLandmark.RIGHT_KNEE,
    Joint.LEFT_ANKLE to PoseLandmark.LEFT_ANKLE,
    Joint.RIGHT_ANKLE to PoseLandmark.RIGHT_ANKLE,
)

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

/** Converte a pose do ML Kit (coordenadas da imagem já girada) para o modelo do app e o rig. */
private fun Pose.toReading(width: Float, height: Float): PoseReading? {
    if (allPoseLandmarks.isEmpty()) return null
    val points = JOINTS.mapNotNull { (joint, type) ->
        getPoseLandmark(type)?.let { lm -> joint to Point(lm.position.x, lm.position.y, lm.inFrameLikelihood) }
    }.toMap()
    // Os 33 marcos 3D, na ordem do ML Kit, para o classificador de poses.
    val all = allPoseLandmarks.sortedBy { it.landmarkType }.map { Vec3(it.position3D.x, it.position3D.y, it.position3D.z) }
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
