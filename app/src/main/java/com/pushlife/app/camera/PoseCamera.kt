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

/** Câmera para os exercícios de corpo inteiro: entrega a pose detectada em cada quadro, ou null. */
@Composable
fun PoseCamera(onPose: (PoseFrame?) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onPose)
    FrontCamera(createAnalyzer = { PoseAnalyzer { callback(it) } }, modifier = modifier)
}

private class PoseAnalyzer(private val onResult: (PoseFrame?) -> Unit) : FrameAnalyzer {
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
        detector.process(InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { pose -> if (!closed) onResult(pose.toFrame()) }
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

/** Converte a pose do ML Kit (coordenadas da imagem já girada) para o modelo do app. */
private fun Pose.toFrame(): PoseFrame? {
    if (allPoseLandmarks.isEmpty()) return null
    val points = JOINTS.mapNotNull { (joint, type) ->
        getPoseLandmark(type)?.let { lm -> joint to Point(lm.position.x, lm.position.y, lm.inFrameLikelihood) }
    }.toMap()
    return PoseFrame(points)
}
