package com.pushlife.app.camera

import android.content.Context
import com.pushlife.app.core.pose.classify.PoseClassifier
import com.pushlife.app.core.pose.classify.PoseSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Poses de referência do Google ML Kit (flexões e agachamentos), lidas uma vez dos assets do app. */
object PoseModels {
    @Volatile
    var classifier: PoseClassifier? = null
        private set

    suspend fun load(context: Context) {
        if (classifier != null) return
        classifier = withContext(Dispatchers.IO) {
            val samples = context.assets.open("pose/fitness_pose_samples.csv").bufferedReader().useLines { PoseSample.parseAll(it) }
            PoseClassifier(samples)
        }
    }
}
