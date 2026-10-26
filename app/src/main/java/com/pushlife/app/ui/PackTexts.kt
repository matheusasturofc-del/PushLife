package com.pushlife.app.ui

import androidx.annotation.StringRes
import com.pushlife.app.R
import com.pushlife.app.core.packs.Move
import com.pushlife.app.core.packs.PackId

// nome e dica de cada movimento
internal val Move.nameRes: Int
    @StringRes get() = when (this) {
        Move.MARCH -> R.string.move_march
        Move.ARM_CIRCLES -> R.string.move_arm_circles
        Move.JUMPING_JACK -> R.string.move_jumping_jack
        Move.WALL_PUSHUP -> R.string.move_wall_pushup
        Move.INCLINE_PUSHUP -> R.string.move_incline_pushup
        Move.KNEE_PUSHUP -> R.string.move_knee_pushup
        Move.PUSHUP -> R.string.move_pushup
        Move.DIAMOND_PUSHUP -> R.string.move_diamond_pushup
        Move.CHAIR_DIP -> R.string.move_chair_dip
        Move.PLANK -> R.string.move_plank
        Move.CHAIR_SQUAT -> R.string.move_chair_squat
        Move.SQUAT -> R.string.move_squat
        Move.JUMP_SQUAT -> R.string.move_jump_squat
        Move.LUNGE -> R.string.move_lunge
        Move.WALL_SIT -> R.string.move_wall_sit
        Move.GLUTE_BRIDGE -> R.string.move_glute_bridge
        Move.CALF_RAISE -> R.string.move_calf_raise
        Move.CRUNCH -> R.string.move_crunch
        Move.SITUP -> R.string.move_situp
        Move.DEAD_BUG -> R.string.move_dead_bug
        Move.BIRD_DOG -> R.string.move_bird_dog
        Move.SIDE_PLANK -> R.string.move_side_plank
        Move.MOUNTAIN_CLIMBER -> R.string.move_mountain_climber
        Move.BURPEE -> R.string.move_burpee
        Move.NECK_TILT -> R.string.move_neck_tilt
        Move.CHIN_TUCK -> R.string.move_chin_tuck
        Move.SHOULDER_ROLL -> R.string.move_shoulder_roll
        Move.CHEST_OPENER -> R.string.move_chest_opener
        Move.CAT_COW -> R.string.move_cat_cow
        Move.CHILD_POSE -> R.string.move_child_pose
        Move.FORWARD_FOLD -> R.string.move_forward_fold
        Move.HAMSTRING_STRETCH -> R.string.move_hamstring_stretch
        Move.QUAD_STRETCH -> R.string.move_quad_stretch
        Move.HIP_FLEXOR_STRETCH -> R.string.move_hip_flexor_stretch
        Move.SPINAL_TWIST -> R.string.move_spinal_twist
        Move.KNEES_TO_CHEST -> R.string.move_knees_to_chest
        Move.OVERHEAD_REACH -> R.string.move_overhead_reach
        Move.SIDE_BEND -> R.string.move_side_bend
        Move.BUTTERFLY -> R.string.move_butterfly
        Move.SEATED_MARCH -> R.string.move_seated_march
        Move.SEATED_TWIST -> R.string.move_seated_twist
        Move.SEATED_FORWARD -> R.string.move_seated_forward
        Move.SEATED_REACH -> R.string.move_seated_reach
    }

internal val Move.cueRes: Int
    @StringRes get() = when (this) {
        Move.MARCH -> R.string.move_march_cue
        Move.ARM_CIRCLES -> R.string.move_arm_circles_cue
        Move.JUMPING_JACK -> R.string.move_jumping_jack_cue
        Move.WALL_PUSHUP -> R.string.move_wall_pushup_cue
        Move.INCLINE_PUSHUP -> R.string.move_incline_pushup_cue
        Move.KNEE_PUSHUP -> R.string.move_knee_pushup_cue
        Move.PUSHUP -> R.string.move_pushup_cue
        Move.DIAMOND_PUSHUP -> R.string.move_diamond_pushup_cue
        Move.CHAIR_DIP -> R.string.move_chair_dip_cue
        Move.PLANK -> R.string.move_plank_cue
        Move.CHAIR_SQUAT -> R.string.move_chair_squat_cue
        Move.SQUAT -> R.string.move_squat_cue
        Move.JUMP_SQUAT -> R.string.move_jump_squat_cue
        Move.LUNGE -> R.string.move_lunge_cue
        Move.WALL_SIT -> R.string.move_wall_sit_cue
        Move.GLUTE_BRIDGE -> R.string.move_glute_bridge_cue
        Move.CALF_RAISE -> R.string.move_calf_raise_cue
        Move.CRUNCH -> R.string.move_crunch_cue
        Move.SITUP -> R.string.move_situp_cue
        Move.DEAD_BUG -> R.string.move_dead_bug_cue
        Move.BIRD_DOG -> R.string.move_bird_dog_cue
        Move.SIDE_PLANK -> R.string.move_side_plank_cue
        Move.MOUNTAIN_CLIMBER -> R.string.move_mountain_climber_cue
        Move.BURPEE -> R.string.move_burpee_cue
        Move.NECK_TILT -> R.string.move_neck_tilt_cue
        Move.CHIN_TUCK -> R.string.move_chin_tuck_cue
        Move.SHOULDER_ROLL -> R.string.move_shoulder_roll_cue
        Move.CHEST_OPENER -> R.string.move_chest_opener_cue
        Move.CAT_COW -> R.string.move_cat_cow_cue
        Move.CHILD_POSE -> R.string.move_child_pose_cue
        Move.FORWARD_FOLD -> R.string.move_forward_fold_cue
        Move.HAMSTRING_STRETCH -> R.string.move_hamstring_stretch_cue
        Move.QUAD_STRETCH -> R.string.move_quad_stretch_cue
        Move.HIP_FLEXOR_STRETCH -> R.string.move_hip_flexor_stretch_cue
        Move.SPINAL_TWIST -> R.string.move_spinal_twist_cue
        Move.KNEES_TO_CHEST -> R.string.move_knees_to_chest_cue
        Move.OVERHEAD_REACH -> R.string.move_overhead_reach_cue
        Move.SIDE_BEND -> R.string.move_side_bend_cue
        Move.BUTTERFLY -> R.string.move_butterfly_cue
        Move.SEATED_MARCH -> R.string.move_seated_march_cue
        Move.SEATED_TWIST -> R.string.move_seated_twist_cue
        Move.SEATED_FORWARD -> R.string.move_seated_forward_cue
        Move.SEATED_REACH -> R.string.move_seated_reach_cue
    }

// nome, descrição e busca no Spotify de cada pack
internal val PackId.nameRes: Int
    @StringRes get() = when (this) {
        PackId.ARMS -> R.string.pack_arms
        PackId.LEGS -> R.string.pack_legs
        PackId.CORE -> R.string.pack_core
        PackId.FULL -> R.string.pack_full
        PackId.CHALLENGE_10 -> R.string.pack_challenge_10
        PackId.CHALLENGE_100 -> R.string.pack_challenge_100
        PackId.STRETCH_MORNING -> R.string.pack_stretch_morning
        PackId.STRETCH_NIGHT -> R.string.pack_stretch_night
        PackId.STRETCH_NECK -> R.string.pack_stretch_neck
        PackId.STRETCH_CHAIR -> R.string.pack_stretch_chair
    }

internal val PackId.descRes: Int
    @StringRes get() = when (this) {
        PackId.ARMS -> R.string.pack_arms_desc
        PackId.LEGS -> R.string.pack_legs_desc
        PackId.CORE -> R.string.pack_core_desc
        PackId.FULL -> R.string.pack_full_desc
        PackId.CHALLENGE_10 -> R.string.pack_challenge_10_desc
        PackId.CHALLENGE_100 -> R.string.pack_challenge_100_desc
        PackId.STRETCH_MORNING -> R.string.pack_stretch_morning_desc
        PackId.STRETCH_NIGHT -> R.string.pack_stretch_night_desc
        PackId.STRETCH_NECK -> R.string.pack_stretch_neck_desc
        PackId.STRETCH_CHAIR -> R.string.pack_stretch_chair_desc
    }

internal val PackId.spotifyRes: Int
    @StringRes get() = when (this) {
        PackId.ARMS -> R.string.pack_arms_spotify
        PackId.LEGS -> R.string.pack_legs_spotify
        PackId.CORE -> R.string.pack_core_spotify
        PackId.FULL -> R.string.pack_full_spotify
        PackId.CHALLENGE_10 -> R.string.pack_challenge_10_spotify
        PackId.CHALLENGE_100 -> R.string.pack_challenge_100_spotify
        PackId.STRETCH_MORNING -> R.string.pack_stretch_morning_spotify
        PackId.STRETCH_NIGHT -> R.string.pack_stretch_night_spotify
        PackId.STRETCH_NECK -> R.string.pack_stretch_neck_spotify
        PackId.STRETCH_CHAIR -> R.string.pack_stretch_chair_spotify
    }
