package com.pushlife.app.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsGymnastics
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.ui.graphics.vector.ImageVector
import com.pushlife.app.R
import com.pushlife.app.core.Exercise
import com.pushlife.app.core.pose.Cue

// textos e ícone de cada exercício
data class ExerciseUi(
    @StringRes val name: Int,
    @StringRes val value: Int,
    @StringRes val setup: Int,
    val steps: List<Int>,
    val icon: ImageVector,
)

val Exercise.ui: ExerciseUi
    get() = when (this) {
        Exercise.PUSHUP -> ExerciseUi(
            R.string.ex_pushup, R.string.value_pushup, R.string.setup_pushup,
            listOf(R.string.train_step_1, R.string.train_step_2, R.string.train_step_3), Icons.Rounded.FitnessCenter,
        )
        Exercise.SQUAT -> ExerciseUi(
            R.string.ex_squat, R.string.value_squat, R.string.setup_side,
            listOf(R.string.squat_1, R.string.squat_2, R.string.squat_3), Icons.Rounded.AccessibilityNew,
        )
        Exercise.LUNGE -> ExerciseUi(
            R.string.ex_lunge, R.string.value_lunge, R.string.setup_side,
            listOf(R.string.lunge_1, R.string.lunge_2, R.string.lunge_3), Icons.Rounded.Hiking,
        )
        Exercise.SITUP -> ExerciseUi(
            R.string.ex_situp, R.string.value_situp, R.string.setup_side_floor,
            listOf(R.string.situp_1, R.string.situp_2, R.string.situp_3), Icons.Rounded.SelfImprovement,
        )
        Exercise.JUMPING_JACK -> ExerciseUi(
            R.string.ex_jack, R.string.value_jack, R.string.setup_front,
            listOf(R.string.jack_1, R.string.jack_2, R.string.jack_3), Icons.Rounded.SportsGymnastics,
        )
        Exercise.PLANK -> ExerciseUi(
            R.string.ex_plank, R.string.value_plank, R.string.setup_side_floor,
            listOf(R.string.plank_1, R.string.plank_2, R.string.plank_3), Icons.Rounded.Timer,
        )
        Exercise.WALL_SIT -> ExerciseUi(
            R.string.ex_wall_sit, R.string.value_wall_sit, R.string.setup_wall_sit,
            listOf(R.string.wall_sit_1, R.string.wall_sit_2, R.string.wall_sit_3), Icons.Rounded.EventSeat,
        )
        Exercise.PULLUP -> ExerciseUi(
            R.string.ex_pullup, R.string.value_pullup, R.string.setup_pullup,
            listOf(R.string.pullup_1, R.string.pullup_2, R.string.pullup_3), Icons.Rounded.Upgrade,
        )
        Exercise.BURPEE -> ExerciseUi(
            R.string.ex_burpee, R.string.value_burpee, R.string.setup_side,
            listOf(R.string.burpee_1, R.string.burpee_2, R.string.burpee_3), Icons.AutoMirrored.Rounded.DirectionsRun,
        )
        Exercise.WALK -> ExerciseUi(
            R.string.ex_walk, R.string.value_walk, R.string.setup_walk,
            listOf(R.string.walk_1, R.string.walk_2, R.string.walk_3), Icons.AutoMirrored.Rounded.DirectionsWalk,
        )
    }

@get:StringRes
val Cue.text: Int
    get() = when (this) {
        Cue.NOT_VISIBLE -> R.string.cue_not_visible
        Cue.GET_READY -> R.string.cue_get_ready
        Cue.GO_DOWN -> R.string.status_go_down
        Cue.GO_UP -> R.string.status_go_up
        Cue.GO_LOWER -> R.string.cue_go_lower
        Cue.CHEST_UP -> R.string.cue_chest_up
        Cue.HIPS_UP -> R.string.cue_hips_up
        Cue.HIPS_DOWN -> R.string.cue_hips_down
        Cue.HOLD -> R.string.cue_hold
        Cue.KNEES_UP -> R.string.cue_knees_up
        Cue.TURN_SIDEWAYS -> R.string.cue_turn_sideways
        Cue.OPEN -> R.string.cue_open
        Cue.CLOSE -> R.string.cue_close
        Cue.FLOOR -> R.string.cue_floor
        Cue.STAND -> R.string.cue_stand
        Cue.PULL -> R.string.cue_pull
        Cue.EXTEND -> R.string.cue_extend
    }
